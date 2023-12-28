package io.papermc.paperweight.tasks.softspoon

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir

class CollectATsFromPatchesTest {

    private lateinit var task: CollectATsFromPatches

    @BeforeTest
    fun setup() {
        val project = ProjectBuilder.builder().withProjectDir(File("")).build()
        task = project.tasks.register("collectATs", CollectATsFromPatches::class).get()
    }

    @Test
    fun `should collect ATs from patches`(@TempDir tempDir: Path) {
        task.patchDir.set(File("src/test/resources/input/patches"))
        task.outputFile.set(tempDir.resolve("output.at").toFile())

        task.run()

        val expectedOutput = Paths.get("src/test/resources/output/output.at").toFile()

        assertEquals(expectedOutput.readText(), tempDir.resolve("output.at").readText())
    }

    @Test
    fun `should handle patches without ATs`(@TempDir tempDir: Path) {
        task.patchDir.set(File("src/test/resources/input/patches_no_ats"))
        task.outputFile.set(tempDir.resolve("output.at").toFile())

        task.run()

        val expectedOutput = Paths.get("src/test/resources/output/no_ats.at").toFile()

        assertEquals(expectedOutput.readText(), tempDir.resolve("output.at").readText())
    }

    @Test
    fun `should handle empty patch directory`(@TempDir tempDir: Path) {
        task.patchDir.set(File("src/test/resources/input/empty_patches"))
        task.outputFile.set(tempDir.resolve("output.at").toFile())

        task.run()

        val expectedOutput = Paths.get("src/test/resources/output/empty_patches.at").toFile()

        assertEquals(expectedOutput.readText(), tempDir.resolve("output.at").readText())
    }
}
