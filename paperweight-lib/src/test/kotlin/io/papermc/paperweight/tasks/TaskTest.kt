package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.eclipse.jgit.api.Git
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions

open class TaskTest {

    fun setupProject() = ProjectBuilder.builder().withGradleUserHomeDir(File("build")).withProjectDir(File("")).build()

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
}
