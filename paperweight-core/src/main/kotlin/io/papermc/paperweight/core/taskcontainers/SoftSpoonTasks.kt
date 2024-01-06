/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2023 Kyle Wood (DenWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.paperweight.core.taskcontainers

import io.papermc.paperweight.core.ext
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.tasks.mache.*
import io.papermc.paperweight.tasks.mache.RemapJar
import io.papermc.paperweight.tasks.patchremapv2.GeneratePatchRemapMappings
import io.papermc.paperweight.tasks.patchremapv2.RemapCBPatches
import io.papermc.paperweight.tasks.softspoon.ApplyFeaturePatches
import io.papermc.paperweight.tasks.softspoon.ApplyFilePatches
import io.papermc.paperweight.tasks.softspoon.ApplyFilePatchesFuzzy
import io.papermc.paperweight.tasks.softspoon.RebuildFilePatches
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import io.papermc.paperweight.util.data.mache.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

open class SoftSpoonTasks(
    val project: Project,
    val allTasks: AllTasks,
    tasks: TaskContainer = project.tasks
) {

    lateinit var mache: MacheMeta

    val macheCodebook by project.configurations.registering
    val macheRemapper by project.configurations.registering
    val macheDecompiler by project.configurations.registering
    val macheParamMappings by project.configurations.registering
    val macheMinecraft by project.configurations.registering
    val macheMinecraftExtended by project.configurations.registering

    val macheRemapJar by tasks.registering(RemapJar::class) {
        group = "mache"
        serverJar.set(allTasks.extractFromBundler.flatMap { it.serverJar })
        serverMappings.set(allTasks.downloadMappings.flatMap { it.outputFile })

        codebookClasspath.from(macheCodebook)
        minecraftClasspath.from(macheMinecraft)
        remapperClasspath.from(macheRemapper)
        paramMappings.from(macheParamMappings)

        outputJar.set(layout.cache.resolve(FINAL_REMAPPED_CODEBOOK_JAR))
    }

    val macheDecompileJar by tasks.registering(DecompileJar::class) {
        group = "mache"
        inputJar.set(macheRemapJar.flatMap { it.outputJar })
        decompilerArgs.set(mache.decompilerArgs)

        minecraftClasspath.from(macheMinecraft)
        decompiler.from(macheDecompiler)

        outputJar.set(layout.cache.resolve(FINAL_DECOMPILE_JAR))
    }

    val collectAccessTransform by tasks.registering(CollectATsFromPatches::class) {
        group = "mache"

        patchDir.set(project.ext.paper.featurePatchDir)
    }

    val mergeCollectedAts by tasks.registering<MergeAccessTransforms> {
        firstFile.set(project.ext.paper.additionalAts.fileExists(project))
        secondFile.set(collectAccessTransform.flatMap { it.outputFile })
    }

    val setupMacheSources by tasks.registering(SetupVanilla::class) {
        group = "mache"
        description = "Setup vanilla source dir (apllying mache patches and paper ATs)."

        mache.from(project.configurations.named(MACHE_CONFIG))
        patches.set(layout.cache.resolve(PATCHES_FOLDER)) // TODO extract mache
        ats.set(mergeCollectedAts.flatMap { it.outputFile })
        minecraftClasspath.from(macheMinecraft)

        inputFile.set(macheDecompileJar.flatMap { it.outputJar })
        predicate.set { Files.isRegularFile(it) && it.toString().endsWith(".java") }
        outputDir.set(layout.cache.resolve(BASE_PROJECT).resolve("sources"))
    }

    val setupMacheResources by tasks.registering(SetupVanilla::class) {
        group = "mache"
        description = "Setup vanilla resources dir"

        inputFile.set(macheDecompileJar.flatMap { it.outputJar })
        predicate.set { Files.isRegularFile(it) && !it.toString().endsWith(".java") }
        outputDir.set(layout.cache.resolve(BASE_PROJECT).resolve("resources"))
    }

    val applySourcePatches by tasks.registering(ApplyFilePatches::class) {
        group = "softspoon"
        description = "Applies patches to the vanilla sources"

        input.set(setupMacheSources.flatMap { it.outputDir })
        output.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/java") })
        patches.set(project.ext.paper.sourcePatchDir)
    }

    val applySourcePatchesFuzzy by tasks.registering(ApplyFilePatchesFuzzy::class) {
        group = "softspoon"
        description = "Applies patches to the vanilla sources"

        input.set(setupMacheSources.flatMap { it.outputDir })
        output.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/java") })
        patches.set(project.ext.paper.sourcePatchDir)
    }

    val applyResourcePatches by tasks.registering(ApplyFilePatches::class) {
        group = "softspoon"
        description = "Applies patches to the vanilla resources"

        input.set(setupMacheResources.flatMap { it.outputDir })
        output.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/resources") })
        patches.set(project.ext.paper.resourcePatchDir)
    }

    val applyFilePatches by tasks.registering(Task::class) {
        group = "softspoon"
        description = "Applies all file patches"
        dependsOn(applySourcePatches, applyResourcePatches)
    }

    val applyFeaturePatches by tasks.registering(ApplyFeaturePatches::class) {
        group = "softspoon"
        description = "Applies all feature patches"
        dependsOn(applyFilePatches)

        repo.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/java") })
        patches.set(project.ext.paper.featurePatchDir)
    }

    val applyPatches by tasks.registering(Task::class) {
        group = "softspoon"
        description = "Applies all patches"
        dependsOn(applyFilePatches, applyFeaturePatches)
    }

    val rebuildSourcePatches by tasks.registering(RebuildFilePatches::class) {
        group = "softspoon"
        description = "Rebuilds patches to the vanilla sources"

        minecraftClasspath.from(macheMinecraftExtended)
        atFile.set(project.ext.paper.additionalAts.fileExists(project))
        atFileOut.set(project.ext.paper.additionalAts.fileExists(project))

        base.set(layout.cache.resolve(BASE_PROJECT).resolve("sources"))
        input.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/java") })
        patches.set(project.ext.paper.sourcePatchDir)
    }

    val rebuildResourcePatches by tasks.registering(RebuildFilePatches::class) {
        group = "softspoon"
        description = "Rebuilds patches to the vanilla resources"

        base.set(layout.cache.resolve(BASE_PROJECT).resolve("resources"))
        input.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/resources") })
        patches.set(project.ext.paper.resourcePatchDir)
    }

    val rebuildFilePatches by tasks.registering(Task::class) {
        group = "softspoon"
        description = "Rebuilds all file patches"
        dependsOn(rebuildSourcePatches, rebuildResourcePatches)
    }

    val rebuildFeaturePatches by tasks.registering(RebuildGitPatches::class) {
        group = "softspoon"
        description = "Rebuilds all feature patches"
        dependsOn(rebuildFilePatches)

        inputDir.set(project.ext.serverProject.map { it.layout.projectDirectory.dir("src/vanilla/java") })
        patchDir.set(project.ext.paper.featurePatchDir)
        baseRef.set("file")
    }

    val rebuildPatches by tasks.registering(Task::class) {
        group = "softspoon"
        description = "Rebuilds all file patches"
        dependsOn(rebuildFilePatches, rebuildFeaturePatches)
    }

    // patch remap stuff
    val macheSpigotDecompileJar by tasks.registering<SpigotDecompileJar> {
        group = "patchremap"
        inputJar.set(macheRemapJar.flatMap { it.outputJar })
        fernFlowerJar.set(project.ext.craftBukkit.fernFlowerJar)
        decompileCommand.set(allTasks.buildDataInfo.map { it.decompileCommand })
    }

    val generatePatchRemapMappings by tasks.registering(GeneratePatchRemapMappings::class) {
        group = "patchremap"

        minecraftClasspath.from(macheMinecraft)
        serverJar.set(macheRemapJar.flatMap { it.outputJar })
        paramMappings.from(macheParamMappings)
        vanillaMappings.set(allTasks.downloadMappings.flatMap { it.outputFile })
        spigotMappings.set(allTasks.generateSpigotMappings.flatMap { it.notchToSpigotMappings })

        patchRemapMappings.set(layout.cache.resolve(SPIGOT_MOJANG_PARCHMENT_MAPPINGS))
    }

    val remapCBPatches by tasks.registering(RemapCBPatches::class) {
        group = "patchremap"
        base.set(layout.cache.resolve(BASE_PROJECT).resolve("sources"))
        // craftBukkit.set(allTasks.patchCraftBukkit.flatMap { it.outputDir })
        craftBukkit.set(project.layout.cache.resolve("paperweight/taskCache/patchCraftBukkit.repo"))
        outputPatchDir.set(project.layout.projectDirectory.dir("patches/remapped-cb"))
        // mappingsFile.set(allTasks.patchMappings.flatMap { it.outputMappings })
        mappingsFile.set(layout.cache.resolve(SPIGOT_MOJANG_PARCHMENT_MAPPINGS))
    }

    fun afterEvaluate() {
        // load mache
        mache = this.project.configurations.named(MACHE_CONFIG).get().singleFile.toPath().openZip().use { zip ->
            return@use gson.fromJson<MacheMeta>(zip.getPath("/mache.json").readLines().joinToString("\n"))
        }
        println("Loaded mache ${mache.version}")

        // setup repos
        this.project.repositories {
            for (repository in mache.repositories) {
                maven(repository.url) {
                    name = repository.name
                    mavenContent {
                        for (group in repository.groups ?: listOf()) {
                            includeGroupByRegex(group + ".*")
                        }
                    }
                }
            }

            maven("https://libraries.minecraft.net/") {
                name = "Minecraft"
            }
            mavenCentral()
        }

        val libsFile = project.layout.cache.resolve(SERVER_LIBRARIES_TXT)

        // setup mc deps
        macheMinecraft {
            withDependencies {
                project.dependencies {
                    val libs = libsFile.convertToPathOrNull()
                    if (libs != null && libs.exists()) {
                        libs.forEachLine { line ->
                            add(create(line))
                        }
                    }
                }
            }
        }
        macheMinecraftExtended {
            withDependencies {
                project.dependencies {
                    val libs = libsFile.convertToPathOrNull()
                    if (libs != null && libs.exists()) {
                        libs.forEachLine { line ->
                            add(create(line))
                        }
                    }
                    add(create(project.files(project.layout.cache.resolve(FINAL_REMAPPED_CODEBOOK_JAR))))
                }
            }
        }

        // setup mache deps
        this.project.dependencies {
            mache.dependencies.codebook.forEach {
                "macheCodebook"("${it.group}:${it.name}:${it.version}")
            }
            mache.dependencies.paramMappings.forEach {
                "macheParamMappings"("${it.group}:${it.name}:${it.version}")
            }
            mache.dependencies.remapper.forEach {
                "macheRemapper"("${it.group}:${it.name}:${it.version}")
            }
            mache.dependencies.decompiler.forEach {
                "macheDecompiler"("${it.group}:${it.name}:${it.version}")
            }
        }

        this.project.ext.serverProject.get().setupServerProject(libsFile)
    }

    private fun Project.setupServerProject(libsFile: Path) {
        if (!projectDir.exists()) {
            return
        }

        // minecraft deps
        val macheMinecraft by configurations.creating {
            withDependencies {
                dependencies {
                    // setup mc deps
                    val libs = libsFile.convertToPathOrNull()
                    if (libs != null && libs.exists()) {
                        libs.forEachLine { line ->
                            add(create(line))
                        }
                    }
                }
            }
        }

        // impl extends minecraft
        configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME) {
            extendsFrom(macheMinecraft)
        }

        // add vanilla source set
        the<JavaPluginExtension>().sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME) {
            java {
                srcDirs(projectDir.resolve("src/vanilla/java"))
            }
            resources {
                srcDirs(projectDir.resolve("src/vanilla/resources"))
            }
        }
    }
}
