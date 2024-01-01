package io.papermc.paperweight.tasks.patchremapv2

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import java.nio.file.Path
import kotlin.io.path.*
import org.cadixdev.mercury.Mercury
import org.cadixdev.mercury.remapper.MercuryRemapper
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.*

@UntrackedTask(because = "RemapCBPatches should always run when requested")
abstract class RemapCBPatches : BaseTask() {

    @get:InputDirectory
    abstract val base: DirectoryProperty

    @get:InputDirectory
    abstract val craftBukkit: DirectoryProperty

    @get:InputFile
    abstract val mappingsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputPatchDir: DirectoryProperty

    @TaskAction
    fun run() {
        val workDir = layout.cache.resolve(REMAPPED_CB).ensureClean()
        val patches = outputPatchDir.convertToPath().ensureClean()
        val mappings = MappingFormats.TINY.read(mappingsFile.convertToPath(), SPIGOT_NAMESPACE, NEW_DEOBF_NAMESPACE)

        val configFiles = project.project(":paper-server").configurations["runtimeClasspath"].resolve().map { it.toPath() }
        val classpath = configFiles + listOf(
            project.project(":paper-api").projectDir.toPath().resolve("src/main/java"),
            project.file(".gradle/caches/paperweight/taskCache/spigotRemapJar.jar").toPath(),
            Path.of("C:\\Users\\Martin\\.m2\\repository\\org\\jetbrains\\annotations\\24.0.1\\annotations-24.0.1.jar")
        )

        val merc = Mercury()
        merc.classPath.addAll(classpath)
        merc.processors.addAll(
            listOf(
                MercuryRemapper.create(mappings)
            )
        )
        merc.isGracefulClasspathChecks = true
        merc.rewrite(craftBukkit.convertToPath().resolve("src/main/java"), workDir)

        val inputDir = workDir
        val baseDir = Path.of("D:\\IdeaProjects\\Paper\\.gradle\\caches\\paperweight\\mache\\dum")

        val patchesCreated = baseDir.walk()
            .sumOf {
                diffFile(inputDir, baseDir, it.relativeTo(baseDir).toString().replace("\\", "/"), patches)
            }

        logger.lifecycle("Rebuilt $patchesCreated patches")
    }

    private fun diffFile(sourceRoot: Path, decompRoot: Path, relativePath: String, patchDir: Path): Int {
        val source = sourceRoot.resolve(relativePath)
        val decomp = decompRoot.resolve(relativePath)

        if (!source.exists() || !decomp.exists()) return 0

        val sourceLines = source.readLines(Charsets.UTF_8)
        val decompLines = decomp.readLines(Charsets.UTF_8)

        val patch = DiffUtils.diff(decompLines, sourceLines)
        if (patch.deltas.isEmpty()) {
            return 0
        }

        // remove all non craftbukkit chunks
        //patch.deltas.removeIf {
        //    !it.target.lines.any { line -> line.contains("CraftBukkit") && !line.contains("decompile error") }
        //}

        val unifiedPatch = UnifiedDiffUtils.generateUnifiedDiff(
            "a/$relativePath",
            "b/$relativePath",
            decompLines,
            patch,
            3,
        )

        val patchFile = patchDir.resolve("$relativePath.patch")
        patchFile.parent.createDirectories()
        patchFile.writeText(unifiedPatch.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        return 1
    }
}