package io.papermc.paperweight.tasks.mache

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.patches.*
import io.papermc.restamp.Restamp
import io.papermc.restamp.RestampContextConfiguration
import io.papermc.restamp.RestampInput
import java.nio.file.Path
import java.util.function.Predicate
import javax.inject.Inject
import kotlin.io.path.*
import org.cadixdev.at.io.AccessTransformFormats
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.openrewrite.InMemoryExecutionContext

abstract class SetupVanilla : BaseTask() {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Internal
    abstract val predicate: Property<Predicate<Path>>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val patches: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ats: RegularFileProperty

    @get:Optional
    @get:CompileClasspath
    abstract val minecraftClasspath: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val mache: ConfigurableFileCollection

    @get:Internal
    abstract val useNativeDiff: Property<Boolean>

    @get:Internal
    abstract val patchExecutable: Property<String>

    @get:Inject
    abstract val exec: ExecOperations

    init {
        run {
            useNativeDiff.convention(false)
            patchExecutable.convention("patch")
        }
    }

    @TaskAction
    fun run() {
        val outputPath = outputDir.convertToPath().ensureClean()

        println("Copy initial sources...")
        inputFile.convertToPath().openZip().walk()
            .filter(predicate.get())
            .forEach {
                val target = outputPath.resolve(it.toString().substring(1))
                target.parent.createDirectories()
                it.copyTo(target, true)
            }

        println("Setup git repo...")
        val vanillaIdent = PersonIdent("Vanilla", "vanilla@automated.papermc.io")

        val git = Git.init()
            .setDirectory(outputPath.toFile())
            .setInitialBranch("main")
            .call()
        git.add().addFilepattern(".").call()
        git.commit()
            .setMessage("Vanilla")
            .setAuthor(vanillaIdent)
            .setSign(false)
            .call()
        git.tag().setName("vanilla").setTagger(vanillaIdent).setSigned(false).call()

        if (patches.isPresent()) {
            // prepare for patches for patching
            val patchesFolder = patches.convertToPath().ensureClean()

            mache.singleFile.toPath().openZip().use { zip ->
                zip.getPath("patches").copyRecursivelyTo(patchesFolder)
            }

            println("Applying mache patches...")
            val result = createPatcher().applyPatches(outputPath, patches.convertToPath(), outputPath, outputPath)

            val macheIdent = PersonIdent("Mache", "mache@automated.papermc.io")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("Mache")
                .setAuthor(macheIdent)
                .setSign(false)
                .call()
            git.tag().setName("mache").setTagger(macheIdent).setSigned(false).call()

            if (result is PatchFailure) {
                result.failures
                    .map { "Patch failed: ${it.patch.relativeTo(patches.get().path)}: ${it.details}" }
                    .forEach { logger.error(it) }
                git.close()
                throw Exception("Failed to apply ${result.failures.size} patches")
            }
        }

        if (ats.isPresent()) {
            val classPath = minecraftClasspath.files.map { it.toPath() }.toMutableList()
            classPath.add(outputPath)

            println("Applying access transformers...")
            val configuration = RestampContextConfiguration.builder()
                .accessTransformers(ats.convertToPath(), AccessTransformFormats.FML)
                .sourceRoot(outputPath)
                .sourceFilesFromAccessTransformers()
                .classpath(classPath)
                .executionContext(InMemoryExecutionContext { it.printStackTrace() })
                .failWithNotApplicableAccessTransformers()
                .build()

            val parsedInput = RestampInput.parseFrom(configuration)
            val results = Restamp.run(parsedInput).allResults

            results.forEach { result ->
                if (result.after != null) {
                    outputPath.resolve(result.after.sourcePath).writeText(result.after.printAll())
                }
            }

            val macheIdent = PersonIdent("ATs", "ats@automated.papermc.io")
            git.add().addFilepattern(".").call()
            git.commit()
                .setMessage("ATs")
                .setAuthor(macheIdent)
                .setSign(false)
                .call()
            git.tag().setName("ATs").setTagger(macheIdent).setSigned(false).call()
        }

        git.close()
    }

    internal open fun createPatcher(): Patcher {
        return if (useNativeDiff.get()) {
            NativePatcher(exec, patchExecutable.get())
        } else {
            JavaPatcher()
        }
    }
}
