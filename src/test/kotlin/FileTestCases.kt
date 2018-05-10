import com.github.h0tk3y.compilersCourse.language.Program
import com.github.h0tk3y.compilersCourse.parsing.readProgram
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class RunCoreTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = coreTests
    }
}

@RunWith(Parameterized::class)
class RunExpressionTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = expressionTests
    }
}

@RunWith(Parameterized::class)
class RunDeepExpressionTestCases : RunAllTestCases() {
    companion object {
        @Parameterized.Parameters(name = "test case: {0}")
        @JvmStatic fun testCases() = deepExpressionTests
    }
}

fun readTests(testRoot: File, takeFirstN: Int? = null): List<TestCaseMatchOutput> {
    val testFiles = testRoot.listFiles().sorted().filter { it.name.endsWith(".expr") }
            .let { if (takeFirstN != null) it.take(takeFirstN) else it }

    return testFiles.map { exprFile ->
        val testId = exprFile.name.removeSuffix(".expr")
        val input = File(testRoot, testId + ".input").readText()
        val output = File(testRoot, "orig/$testId.log").readText()
        object : TestCaseMatchOutput("$testRoot/" + exprFile.name, parseLog(input).filterNotNull(), parseLog(output)) {
            override val program: Program
                get() = readProgram(exprFile.readText())
        } as TestCaseMatchOutput
    }
}

val coreTests = (listOf("core").flatMap { readTests(File("compiler-tests/$it")) }).registerSimple()

val expressionTests = (listOf("expressions").flatMap { readTests(File("compiler-tests/$it")) }).registerSimple()

val deepExpressionTests = (listOf("deep-expressions").flatMap { readTests(File("compiler-tests/$it")) }).registerSimple()