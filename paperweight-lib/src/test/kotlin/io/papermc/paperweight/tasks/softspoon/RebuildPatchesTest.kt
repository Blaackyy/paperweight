package io.papermc.paperweight.tasks.softspoon

import io.papermc.paperweight.tasks.*
import java.io.File
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir

class RebuildPatchesTest: TaskTest() {
    private lateinit var task: RebuildPatches

    @BeforeTest
    fun setup() {
        val project = setupProject()
        task = project.tasks.register("rebuildPatches", RebuildPatches::class).get()
    }

    @Test
    fun `should rebuild patches`(@TempDir tempDir: Path) {
        var testResource = Path.of("src/test/resources/rebuild_patches")
        val testInput = testResource.resolve("input")

        val source = setupDir(tempDir, testInput, "source").toFile()
        val base = setupDir(tempDir, testInput, "base").toFile()
        val patches = tempDir.resolve("patches").toFile()
        val atFile = testInput.resolve("ats.at").toFile()
        val atFileOut = tempDir.resolve("ats.at").toFile()

        task.input.set(source)
        task.base.set(base)
        task.patches.set(patches)
        task.atFile.set(atFile)
        task.atFileOut.set(atFileOut)

        task.run()

        val testOutput = testResource.resolve("output")
        compareDir(tempDir, testOutput, "base")
        compareDir(tempDir, testOutput, "source")
        compareDir(tempDir, testOutput, "patches")
        compareFile(tempDir, testOutput, "ats.at")
    }
}
