package io.papermc.paperweight.tasks.mache

import io.papermc.codebook.CodeBook
import io.papermc.codebook.config.CodeBookContext
import io.papermc.codebook.config.CodeBookInput
import io.papermc.codebook.config.CodeBookRemapper
import io.papermc.codebook.config.CodeBookResource
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

abstract class RunCodeBookWorker : WorkAction<RunCodeBookWorker.RunCodebookParameters> {
    interface RunCodebookParameters : WorkParameters {
        val tempDir: DirectoryProperty
        val serverJar: RegularFileProperty
        val classpath: ConfigurableFileCollection
        val remapperClasspath: ConfigurableFileCollection
        val serverMappings: RegularFileProperty
        val paramMappings: ConfigurableFileCollection
        val constants: ConfigurableFileCollection
        val unpickDefinitions: ConfigurableFileCollection
        val outputJar: RegularFileProperty
        val logs: RegularFileProperty
        val logMissingLvtSuggestions: Property<Boolean>
    }

    override fun execute() {
        val tempDir = parameters.tempDir.get().asFile.toPath()
        if (tempDir.exists()) {
            tempDir.toFile().deleteRecursively()
        }
        tempDir.createDirectories()

        val logs = parameters.logs.get().asFile.toPath()
        logs.deleteIfExists()
        PrintStream(logs.outputStream()).use { output ->
            val oldOut = System.out
            val oldErr = System.err
            System.setOut(output)
            System.setErr(output)

            try {
                run(tempDir)
            } finally {
                System.setOut(oldOut)
                System.setErr(oldErr)
            }
        }
    }

    private fun run(tempDir: Path) {
        try {
            val ctx = CodeBookContext.builder()
                .tempDir(parameters.tempDir.get().asFile.toPath().absolute())
                .remapperJar(
                    CodeBookRemapper.ofClasspath()
                        .jars(parameters.remapperClasspath.files.map { it.toPath().absolute() })
                        .build(),
                )
                .mappings(CodeBookResource.ofFile(parameters.serverMappings.get().asFile.toPath().absolute()))
                .paramMappings(CodeBookResource.ofFile(parameters.paramMappings.singleFile.toPath().absolute()))
                .unpickDefinitions(parameters.unpickDefinitions.files.singleOrNull()?.let { CodeBookResource.ofFile(it.toPath().absolute()) })
                .constantsJar(parameters.constants.files.singleOrNull()?.let { CodeBookResource.ofFile(it.toPath().absolute()) })
                .outputJar(parameters.outputJar.get().asFile.toPath().absolute())
                .overwrite(false)
                .input(
                    CodeBookInput.ofJar()
                        .inputJar(parameters.serverJar.get().asFile.toPath().absolute())
                        .classpathJars(parameters.classpath.files.map { it.toPath().absolute() })
                        .build(),
                )
                .logMissingLvtSuggestions(parameters.logMissingLvtSuggestions.getOrElse(false))
                .build()

            CodeBook(ctx).exec()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
