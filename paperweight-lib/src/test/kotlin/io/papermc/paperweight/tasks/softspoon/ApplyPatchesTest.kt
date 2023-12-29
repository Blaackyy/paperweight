package io.papermc.paperweight.tasks.softspoon

import io.papermc.paperweight.tasks.*
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.gradle.kotlin.dsl.*
import org.junit.jupiter.api.io.TempDir

class ApplyPatchesTest : TaskTest() {
    private lateinit var task: ApplyPatches

    @BeforeTest
    fun setup() {
        val project = setupProject()
        task = project.tasks.register("applyPatches", ApplyPatches::class).get()
    }

    @Test
    fun `should apply patches`(@TempDir tempDir: Path) {
        val testResource = Path.of("src/test/resources/apply_patches")
        val testInput = testResource.resolve("input")

        val input = setupDir(tempDir, testInput, "base").toFile()
        val output = tempDir.resolve("source").toFile()
        val patches = testInput.resolve("patches").toFile()

        setupGitRepo(input, "main")

        task.input.set(input)
        task.output.set(output)
        task.patches.set(patches)

        task.run()

        val testOutput = testResource.resolve("output")
        compareDir(tempDir, testOutput, "source")
    }
}
