package co.touchlab.skie.plugin.libraries

import co.touchlab.skie.acceptancetests.framework.CompilerConfiguration
import co.touchlab.skie.acceptancetests.framework.ExpectedTestResult
import co.touchlab.skie.acceptancetests.framework.TempFileSystem
import co.touchlab.skie.acceptancetests.framework.TestResult
import co.touchlab.skie.acceptancetests.framework.TestResultWithLogs
import co.touchlab.skie.acceptancetests.framework.internal.testrunner.IntermediateResult
import co.touchlab.skie.acceptancetests.framework.internal.testrunner.TestLogger
import co.touchlab.skie.configuration.Configuration
import co.touchlab.skie.external_libraries.BuildConfig
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


class ExternalLibrariesTestRunner(
    private val testTmpDir: File,
    private val testFilter: TestFilter,
) {
    @OptIn(ExperimentalTime::class)
    fun runTests(scope: FunSpec, tests: List<ExternalLibraryTest>) {
        val channel = Channel<Map<ExternalLibraryTest, TestResultWithLogs>>()
        scope.concurrency = 2

        val tempDirectory = Path(BuildConfig.BUILD).resolve("test-temp")
        tempDirectory.toFile().deleteRecursively()
        tempDirectory.toFile().mkdirs()

        val tempFileSystem = TempFileSystem(tempDirectory)
        val tempSourceFile = tempDirectory.resolve("KotlinFile.kt")

        scope.test("Evaluation") {
            val testCompletionTracking = AtomicInteger(0)
            val filteredTests = tests
                .filter { testFilter.shouldBeEvaluated(it) }
            val results = filteredTests
                .parallelStream()
                .map {
                    val result = runTest(it) { "${testCompletionTracking.incrementAndGet()}/${filteredTests.size}" }
                    it to result
                }
                .collect(Collectors.toList())
                .toMap()

            channel.send(results)
            channel.close()
        }

        scope.context("Results") {
            val testResult = channel.receive()
            val resultProcessor = ExternalLibrariesTestResultProcessor(testTmpDir = testTmpDir)
            resultProcessor.processResult(this, testResult)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun runTest(test: ExternalLibraryTest, positionProvider: () -> String): TestResultWithLogs {
        val tempDirectory = test.outputPath
        tempDirectory.toFile().deleteRecursively()
        tempDirectory.toFile().mkdirs()

        val tempFileSystem = TempFileSystem(tempDirectory)
        val tempSourceFile = tempDirectory.resolve("KotlinFile.kt").apply {
            writeText("")
        }

        val testLogger = TestLogger()

        val skieConfiguration = Configuration {

        }

        val compilerConfiguration = CompilerConfiguration(
            dependencies = test.input.files,
            exportedDependencies = test.input.exportedFiles,
        )

        val measuredTest = measureTimedValue {
            IntermediateResult.Value(Unit)
                .flatMap {
                    val compiler = KotlinTestCompiler(tempFileSystem, testLogger)
                    compiler.compile(
                        kotlinFiles = listOf(tempSourceFile),
                        compilerConfiguration = compilerConfiguration,
                    )
                }
                .finalize {
                    val linker = KotlinTestLinker(tempFileSystem, testLogger)
                    linker.link(
                        it,
                        skieConfiguration,
                        compilerConfiguration,
                    )
                }
        }
        val testResult = measuredTest.value

        testLogger.prependTestInfo(test)

        val testResultWithLogs = testResult.withLogsAndDuration(testLogger, measuredTest.duration)
        writeResult(test, testResultWithLogs)
        reportResult(test, testResultWithLogs, positionProvider)
        return testResultWithLogs
    }

    private fun writeResult(test: ExternalLibraryTest, result: TestResultWithLogs) {
        val resultAsText = test.expectedResult.hasSucceededAsString(result)

        test.resultPath.writeText(resultAsText)
    }

    private fun reportResult(test: ExternalLibraryTest, result: TestResultWithLogs, positionProvider: () -> String) {
        val color = if (test.expectedResult.hasSucceeded(result)) {
            "\u001b[32m"
        } else {
            "\u001b[31m"
        }
        val colorReset = "\u001b[0m"

        val line = "${test.fullName}: ${test.expectedResult.hasSucceededAsString(result)} (${positionProvider()}, took ${result.duration.toString(DurationUnit.SECONDS, 2)})"

        println(color + line + colorReset)
    }

    private fun TestLogger.prependTestInfo(test: ExternalLibraryTest) {
        prependLine(
            """
                Test: ${test.library} ([${test.index}])
                To run only this test add env variable: libraryTest=${test.library.replace(".", "\\.")}
            """.trimIndent()
        )
    }

    private fun TestResult.withLogsAndDuration(testLogger: TestLogger, duration: Duration): TestResultWithLogs =
        TestResultWithLogs(
            this,
            duration,
            testLogger.toString(),
        )

    private fun ExpectedTestResult.hasSucceededAsString(result: TestResultWithLogs): String =
        if (this.hasSucceeded(result)) ExpectedTestResult.SUCCESS else ExpectedTestResult.FAILURE

}
