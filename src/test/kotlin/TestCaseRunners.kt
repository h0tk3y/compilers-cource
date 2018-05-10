
import com.github.h0tk3y.compilersCourse.language.NaiveProgramInterpreter
import com.github.h0tk3y.compilersCourse.run
import com.github.h0tk3y.compilersCourse.stack.NaiveStackInterpreter
import com.github.h0tk3y.compilersCourse.stack.StatementToStackCompiler
import com.github.h0tk3y.compilersCourse.x86.StatementToX86Compiler
import com.github.h0tk3y.compilersCourse.x86.TargetPlatform
import org.junit.Assert
import org.junit.AssumptionViolatedException
import java.io.File
import java.nio.file.Files

abstract class TestCaseRunner {
    open fun runTestCase(testCase: TestCase) {
        if (!testCase.canRunOnRunner(this)) {
            throw AssumptionViolatedException("The test case cannot run on this runner ${this@TestCaseRunner}.")
        }
    }
}

class InterpreterRunner : TestCaseRunner() {
    val interpreter = NaiveProgramInterpreter()

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val output = interpreter.run(testCase.program, testCase.input).output
        testCase.checkOutput(output)
    }
}

class StackRunner : TestCaseRunner() {
    val compiler = StatementToStackCompiler()
    val interpreter = NaiveStackInterpreter()

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val stackProgram = compiler.compile(testCase.program)
        val stackMachine = interpreter.run(stackProgram, testCase.input)
        testCase.checkOutput(stackMachine.output)
    }
}

fun determinePlatform(): TargetPlatform {
    val os = System.getProperty("os.name").let {
        if (it.contains("win", true)) TargetPlatform.WIN else TargetPlatform.UNIX
    }
    return os
}

class X86Runner : TestCaseRunner() {
    val compiler = run {
        StatementToX86Compiler(determinePlatform())
    }

    val asmCache = linkedMapOf<String, File>()

    fun assemble(asm: String): File = asmCache.computeIfAbsent(asm) {
        val tmpDir = Files.createTempDirectory(null).toFile()
        val asmFile = File(tmpDir, "asm.s").apply { writeText(asm) }
        val exeFile = File(tmpDir, "output.exe")

        val runtimeFile = File("runtime/intrinsics.o")

        if (!runtimeFile.exists()) {
            val runtimeSourceFile = File(runtimeFile.parent, "intrinsics.c")
            if (!runtimeSourceFile.exists()) {
                throw RuntimeException("Could not find 'intrinsics.o' or 'intrinsics.c' in 'runtime' directory.")
            }
            val assembleRuntimeCmd =
                arrayOf("gcc", "-m32", "-std=gnu99", "-c", runtimeSourceFile.absolutePath, "-o", runtimeFile.absolutePath)
            Runtime.getRuntime().exec(assembleRuntimeCmd).run {
                waitFor()
                val log = inputStream.reader().readText()
                val errLog = errorStream.reader().readText()
                if (exitValue() != 0) {
                    throw RuntimeException("GCC assembler failed to build intrinsics: \n\n" +
                                           "GCC output: \n$log\n\n" +
                                           "GCC error stream: \n$errLog")
                }
            }
        }

        val cmd = arrayOf("gcc", "-m32", runtimeFile.absolutePath, asmFile.absolutePath,
                          "-o", exeFile.absolutePath)
        Runtime.getRuntime().exec(cmd).run {
            waitFor()
            val log = inputStream.reader().readText()
            val errLog = errorStream.reader().readText()
            if (exitValue() != 0) {
                throw RuntimeException("GCC assembler failed to build program: \n\n" +
                                       "$asm\n\n" +
                                       "GCC output: \n$log\n\n" +
                                       "GCC error stream: \n$errLog")
            }
        }
        exeFile
    }

    fun runExe(exe: File, input: List<String>): List<String> {
        val cmd = arrayOf(exe.absolutePath)
        val exec = Runtime.getRuntime().exec(cmd)
        val stdin = exec.outputStream.writer()
        for (i in input) {
            stdin.write("$i\r\n")
        }
        stdin.close()
        val returnCode = exec.waitFor()
        val result = exec.inputStream.reader().readLines()
        if (returnCode != 0) {
            Assert.fail("Program returned with non-zero code $returnCode. \n" +
                        "Output: ${result} \n" +
                        "Error: ${exec.errorStream.reader().readText()}")
        }
        return result
    }

    override fun runTestCase(testCase: TestCase) {
        super.runTestCase(testCase)
        val output = compiler.compile(testCase.program)
        val exe = assemble(output)
        val out = runExe(exe, testCase.input.map { "$it" })
        testCase.checkOutput(out.flatMap { it.split(" ").map { it.toIntOrNull() } })
    }
}
