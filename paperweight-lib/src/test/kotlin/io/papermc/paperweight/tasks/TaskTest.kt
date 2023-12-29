package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import org.eclipse.jgit.api.Git
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions

open class TaskTest {

    fun setupProject() = ProjectBuilder.builder()
        .withGradleUserHomeDir(File("build"))
        .withProjectDir(File(""))
        .build()

    fun setupDir(tempDir: Path, testResource: Path, name: String): Path {
        val temp = tempDir.resolve(name).toFile()
        temp.mkdir()
        testResource.resolve(name).copyRecursivelyTo(temp.convertToPath())
        return temp.convertToPath();
    }

    fun setupFile(tempDir: Path, testResource: Path, name: String): Path {
        val temp = tempDir.resolve(name)
        testResource.resolve(name).copyTo(temp)
        return temp;
    }

    fun compareDir(tempDir: Path, testResource: Path, name: String) {
        val actualOutput = tempDir.resolve(name)
        val expectedOutput = testResource.resolve(name)

        val expectedFiles = expectedOutput.walk().filter { Files.isRegularFile(it) }.filter { !it.toString().contains(".git") }.toList()
        val actualFiles = actualOutput.walk().filter { Files.isRegularFile(it) }.filter { !it.toString().contains(".git") }.toList()

        Assertions.assertEquals(expectedFiles.size, actualFiles.size, "Expected $expectedFiles files, got $actualFiles")

        expectedFiles.forEach { expectedFile ->
            val actualFile = actualOutput.resolve(expectedOutput.relativize(expectedFile))

            compareFile(actualFile, expectedFile)
        }
    }

    fun compareZip(tempDir: Path, testResource: Path, name: String) {
        val actualOutput = tempDir.resolve(name)
        val expectedOutput = testResource.resolve(name)

        compareZip(actualOutput, expectedOutput)
    }

    fun compareZip(actualOutput: Path, expectedOutput: Path) {
        val actualZip = actualOutput.openZip()
        val actualFiles = actualZip.walk().filter { Files.isRegularFile(it) }.toList()
        val expectedZip = expectedOutput.openZip()
        val expectedFiles = expectedZip.walk().filter { Files.isRegularFile(it) }.toList()

        Assertions.assertEquals(expectedFiles.size, actualFiles.size, "Expected $expectedFiles files, got $actualFiles")

        expectedFiles.forEach { expectedFile ->
            val actualFile = actualZip.getPath(expectedFile.toString())

            compareFile(actualFile, expectedFile)
        }
    }

    fun compareFile(tempDir: Path, testResource: Path, name: String) {
        val actualOutput = tempDir.resolve(name)
        val expectedOutput = testResource.resolve(name)

        compareFile(actualOutput, expectedOutput)
    }

    private fun compareFile(actual: Path, expected: Path) {
        Assertions.assertTrue(actual.exists(), "Expected file $actual doesn't exist")
        // TODO I really dont want to ignore CRLF/LF differences here :/
        Assertions.assertEquals(expected.readText().replace("\r\n", "\n"), actual.readText().replace("\r\n", "\n"), "File $actual doesn't match expected")
    }

    fun setupGitRepo(directory: File, mainBranch: String) {
        var git = Git.init().setDirectory(directory).setInitialBranch(mainBranch).call()

        git.add().addFilepattern(".").call()
        git.commit().setMessage("Test").call()

        git.close()
    }

    fun createZip(tempDir: Path, testResource: Path, zipName: String, vararg fileNames: String, ): Path {
        val targetZip = tempDir.resolve(zipName)

        targetZip.writeZip().use { zip ->
            fileNames.forEach { fileName ->
                val sourceFile = testResource.resolve(fileName)
                zip.getPath(fileName).writeText(sourceFile.readText())
            }
        }

        return targetZip
    }

    fun createJar(tempDir: Path, testResource: Path, name: String): Path {
        val sourceFile = tempDir.resolve(name + ".java")
        testResource.resolve(name + ".java").copyTo(sourceFile)

        // run javac on the file
        ProcessBuilder()
            .directory(tempDir.toFile())
            .command("javac", sourceFile.toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()

        // create jar
        ProcessBuilder()
            .directory(tempDir.toFile())
            .command("jar", "-cf", name + ".jar", name + ".class")
            .redirectErrorStream(true)
            .start()
            .waitFor()

        return tempDir.resolve(name + ".jar")
    }

    fun compareJar(tempDir: Path, testResource: Path, fileName: String, className: String) {
        val outputJar = tempDir.resolve(fileName + ".jar")
        val expectedOutputFile = testResource.resolve(fileName + ".javap")

        // unpack jar
        ProcessBuilder()
            .directory(tempDir.toFile())
            .command("jar", "-xf", outputJar.toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()

        // disassemble class
        val process = ProcessBuilder()
            .directory(tempDir.toFile())
            .command("javap", "-p", "-c", className + ".class")
            .redirectErrorStream(true)
            .start()

        var actualOutput = process.inputStream.bufferedReader().readText()
        val expectedOutput = expectedOutputFile.readText()

        // cleanup output
        val lines = actualOutput.split("\n")
        if (lines[0].startsWith("Picked up JAVA_TOOL_OPTIONS")) {
            actualOutput = actualOutput.replace(lines[0] + "\n", "")
        }
        actualOutput = actualOutput.replace("\r\n", "\n")

        process.waitFor()

        assertEquals(expectedOutput, actualOutput, "Output doesn't match expected")
    }
}
