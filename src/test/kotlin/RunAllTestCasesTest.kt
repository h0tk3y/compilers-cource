
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class RunAllTestCases {
    @Parameterized.Parameter(value = 0)
    lateinit var testCase: TestCase

    val interpreterRunner = InterpreterRunner()
    val stackRunner = StackRunner()
    val x86Runner = X86Runner()

    @Test open fun runWithInterpreter() {
        interpreterRunner.runTestCase(testCase)
    }

    @Test open fun runWithStack() {
        stackRunner.runTestCase(testCase)
    }

    @Test open fun runWithX86() {
        x86Runner.runTestCase(testCase)
    }
}