package co.touchlab.skie.acceptancetests.framework.internal

import co.touchlab.skie.acceptancetests.framework.TempFileSystemFactory
import co.touchlab.skie.acceptancetests.framework.TestFilter
import co.touchlab.skie.acceptancetests.framework.TestNode
import co.touchlab.skie.acceptancetests.framework.TestResultWithLogs
import co.touchlab.skie.acceptancetests.framework.internal.testrunner.TestRunner
import co.touchlab.skie.acceptancetests.framework.testStream
import kotlin.streams.toList

internal class TestNodeRunner(
    tempFileSystemFactory: TempFileSystemFactory,
    private val testFilter: TestFilter,
) {

    private val testRunner = TestRunner(tempFileSystemFactory)

    fun runTests(testNode: TestNode): EvaluatedTestNode {
        val tests = testNode.flattenEvaluated()

        val testsWithResults = runTests(tests)

        return mapEvaluatedTests(testsWithResults, testNode) ?: EvaluatedTestNode.Container(
            "No tests",
            emptyList(),
        )
    }

    private fun TestNode.flattenEvaluated(): List<TestNode.Test> = when (this) {
        is TestNode.Container -> this.directChildren.flatMap { it.flattenEvaluated() }
        is TestNode.Test -> if (this.shouldBeEvaluated) listOf(this) else emptyList()
    }

    private val TestNode.Test.shouldBeEvaluated: Boolean
        get() = this.isActive && testFilter.shouldBeEvaluated(this)

    private fun runTests(tests: List<TestNode.Test>): Map<TestNode.Test, TestResultWithLogs> =
        tests
            .testStream()
            .map { it to testRunner.runTest(it) }
            .toList()
            .toMap()

    private fun mapEvaluatedTests(
        evaluatedTests: Map<TestNode.Test, TestResultWithLogs>,
        testNode: TestNode,
    ): EvaluatedTestNode? = when (testNode) {
        is TestNode.Container -> mapEvaluatedTests(evaluatedTests, testNode)
        is TestNode.Test -> mapEvaluatedTests(evaluatedTests, testNode)
    }

    private fun mapEvaluatedTests(
        evaluatedTests: Map<TestNode.Test, TestResultWithLogs>,
        container: TestNode.Container,
    ): EvaluatedTestNode.Container? {
        val children = container.directChildren.mapNotNull { mapEvaluatedTests(evaluatedTests, it) }

        if (children.isEmpty()) {
            return null
        }

        return EvaluatedTestNode.Container(container.name, children)
    }

    private fun mapEvaluatedTests(
        evaluatedTests: Map<TestNode.Test, TestResultWithLogs>,
        test: TestNode.Test,
    ): EvaluatedTestNode = evaluatedTests[test]?.let {
        EvaluatedTestNode.Test(
            name = test.name,
            expectedResult = test.expectedResult,
            actualResultWithLogs = it,
        )
    } ?: EvaluatedTestNode.SkippedTest(test.name)
}
