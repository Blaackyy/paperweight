package io.papermc.paperweight.tasks.softspoon

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.restamp.Restamp
import io.papermc.restamp.RestampContextConfiguration
import io.papermc.restamp.RestampInput
import java.nio.file.Files
import kotlin.io.path.*
import org.cadixdev.at.io.AccessTransformFormats
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.openrewrite.InMemoryExecutionContext

@CacheableTask
abstract class ApplySourceAT : BaseTask() {

    @get:Classpath
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val atFile: RegularFileProperty

    @get:Optional
    @get:CompileClasspath
    abstract val minecraftClasspath: ConfigurableFileCollection

    override fun init() {
        outputJar.convention(defaultOutput())
    }

    @TaskAction
    fun run() {
        val inputZip = inputJar.convertToPath().openZip()

        val classPath = minecraftClasspath.files.map { it.toPath() }.toMutableList()
        classPath.add(inputJar.convertToPath())

        val configuration = RestampContextConfiguration.builder()
            .accessTransformers(atFile.convertToPath(), AccessTransformFormats.FML)
            .sourceRoot(inputZip.getPath("/"))
            .sourceFilesFromAccessTransformers()
            .classpath(classPath)
            .executionContext(InMemoryExecutionContext { it.printStackTrace() })
            .failWithNotApplicableAccessTransformers()
            .build()

        val parsedInput = RestampInput.parseFrom(configuration)
        val results = Restamp.run(parsedInput).allResults

        outputJar.convertToPath().writeZip().use { zip ->
            val alreadyWritten = mutableSetOf<String>()
            results.forEach { result ->
                zip.getPath(result.after.sourcePath.toString()).writeText(result.after.printAll())
                alreadyWritten.add("/" + result.after.sourcePath.toString())
            }

            inputZip.walk().filter { Files.isRegularFile(it) }. filter { !alreadyWritten.contains(it.toString()) }.forEach { file ->
                zip.getPath(file.toString()).writeText(file.readText())
            }

            zip.close()
        }
        inputZip.close()
    }
}
