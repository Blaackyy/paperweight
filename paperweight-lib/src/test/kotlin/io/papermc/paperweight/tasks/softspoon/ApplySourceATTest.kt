package io.papermc.paperweight.tasks.softspoon

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.gradle.kotlin.dsl.*
import org.junit.jupiter.api.io.TempDir

class ApplySourceATTest : TaskTest() {
    private lateinit var task: ApplySourceAT

    @BeforeTest
    fun setup() {
        val project = setupProject()
        task = project.tasks.register("applySourceAT", ApplySourceAT::class).get()
    }

    @Test
    fun `should apply source access transformers`(@TempDir tempDir: Path) {
        val testResource = Path.of("src/test/resources/apply_source_at")
        val testInput = testResource.resolve("input")

        val inputJar = createZip(tempDir, testInput, "Test.jar", "Test.java", "Unrelated.java")
        val atFile = testInput.resolve("ats.at").toFile()
        val outputJar = tempDir.resolve("output.jar")

        task.inputJar.set(inputJar.toFile())
        task.atFile.set(atFile)
        task.outputJar.set(outputJar.toFile())

        task.run()

        val testOutput = testResource.resolve("output")
        val expectedJar = createZip(tempDir, testOutput, "expected.jar", "Test.java", "Unrelated.java")
        compareZip(outputJar, expectedJar)
    }
}
