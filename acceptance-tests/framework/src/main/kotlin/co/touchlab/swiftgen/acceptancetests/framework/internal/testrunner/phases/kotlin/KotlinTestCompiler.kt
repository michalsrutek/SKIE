package co.touchlab.swiftgen.acceptancetests.framework.internal.testrunner.phases.kotlin

import co.touchlab.swiftgen.BuildConfig
import co.touchlab.swiftgen.acceptancetests.framework.TempFileSystem
import co.touchlab.swiftgen.acceptancetests.framework.internal.testrunner.IntermediateResult
import co.touchlab.swiftgen.acceptancetests.framework.internal.testrunner.TestResultBuilder
import co.touchlab.swiftgen.configuration.SwiftGenConfiguration
import co.touchlab.swiftgen.plugin.SwiftGenComponentRegistrar
import co.touchlab.swiftlink.plugin.ConfigurationKeys
import co.touchlab.swiftlink.plugin.SwiftKtComponentRegistrar
import co.touchlab.swiftpack.api.SwiftPackModuleBuilder
import co.touchlab.swiftpack.spi.NamespacedSwiftPackModule
import org.jetbrains.kotlin.cli.bc.K2Native
import org.jetbrains.kotlin.cli.bc.K2NativeCompilerArguments
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

internal class KotlinTestCompiler(
    private val tempFileSystem: TempFileSystem,
    private val testResultBuilder: TestResultBuilder,
) {

    fun compile(kotlinFiles: List<Path>, configuration: SwiftGenConfiguration): IntermediateResult<Path> {
        val outputDirectory = tempFileSystem.createDirectory("kotlin-build")

        val generatedSwiftDirectory = configureSwiftKt()

        val (messageCollector, outputStream) = createCompilerOutputStream()

        val arguments = createCompilerArguments(kotlinFiles, configuration, outputDirectory)

        val result = K2Native().exec(messageCollector, Services.EMPTY, arguments)

        return interpretResult(result, outputDirectory, outputStream, generatedSwiftDirectory)
    }

    private fun configureSwiftKt(): Path {
        val outputDirectory = tempFileSystem.createDirectory("swiftpack")
        val expandedSwiftDirectory = tempFileSystem.createDirectory("swiftpack-expanded")

        SwiftPackModuleBuilder.Config.outputDir = outputDirectory.toFile()

        PluginRegistrar.configure.set {
            val swiftPackModule = NamespacedSwiftPackModule.Reference("Kotlin", outputDirectory.toFile())

            add(ConfigurationKeys.swiftPackModules, swiftPackModule)
            put(ConfigurationKeys.expandedSwiftDir, expandedSwiftDirectory.toFile())
        }

        PluginRegistrar.plugins.set(
            listOf(
                SwiftKtComponentRegistrar(),
                SwiftGenComponentRegistrar(),
            )
        )

        return expandedSwiftDirectory
    }

    private fun createCompilerOutputStream(): Pair<PrintingMessageCollector, OutputStream> {
        val outputStream = ByteArrayOutputStream()

        val messageCollector = PrintingMessageCollector(
            PrintStream(outputStream),
            MessageRenderer.GRADLE_STYLE,
            false,
        )

        return messageCollector to outputStream
    }

    private fun createCompilerArguments(
        kotlinFiles: List<Path>,
        configuration: SwiftGenConfiguration,
        outputDirectory: Path,
    ): K2NativeCompilerArguments =
        K2NativeCompilerArguments().apply {
            freeArgs = kotlinFiles.map { it.absolutePathString() }

            pluginOptions = (pluginOptions ?: emptyArray()) + configuration.toPluginOptions()

            produce = "framework"
            staticFramework = true

            temporaryFilesDir = outputDirectory.absolutePathString()
            outputName = outputDirectory.resolve("Kotlin").absolutePathString()
            bundleId = "Kotlin"

            pluginClasspaths = (pluginClasspaths ?: emptyArray()) + arrayOf(BuildConfig.RESOURCES)
        }

    private fun interpretResult(
        result: ExitCode,
        outputDirectory: Path,
        outputStream: OutputStream,
        generatedSwiftDirectory: Path,
    ): IntermediateResult<Path> {
        val generatedSwift = generatedSwiftDirectory.listDirectoryEntries().joinToString("\n") {
            "------ ${it.name} ------\n" + it.readText()
        }
        testResultBuilder.appendLog("Generated Swift", generatedSwift)

        return when (result) {
            ExitCode.OK -> {
                testResultBuilder.appendLog("Kotlin compilation", outputStream.toString())

                IntermediateResult.Value(outputDirectory)
            }
            else -> {
                val testResult = testResultBuilder.buildKotlinCompilationError(outputStream.toString())

                IntermediateResult.Error(testResult)
            }
        }
    }
}