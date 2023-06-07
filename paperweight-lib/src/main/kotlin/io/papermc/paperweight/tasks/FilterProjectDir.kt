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

package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.*
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.*
import kotlin.streams.toList
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class FilterProjectDir : BaseTask() {

    @get:InputDirectory
    abstract val inputSrcDir: DirectoryProperty

    @get:InputDirectory
    abstract val inputResourcesDir: DirectoryProperty

    @get:InputFile
    abstract val vanillaJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    override fun init() {
        outputJar.convention(defaultOutput())
    }

    @TaskAction
    fun run() {
        val output = outputJar.path
        val target = output.resolveSibling("${output.name}.dir")
        target.createDirectories()

        vanillaJar.path.openZip().use { zip ->
            val srcMatchers = createMatchers(inputSrcDir.path, zip)
            val resourcesMatchers = createMatchers(inputResourcesDir.path, zip)

            zip.walk().use { stream ->
                stream.filter { f ->
                    f.isRegularFile() &&
                        !srcMatchers.any { matcher -> matcher.matches(f) } &&
                        !resourcesMatchers.any { matcher -> matcher.matches(f) }
                }.forEach { f ->
                    val targetFile = target.resolve(f.invariantSeparatorsPathString.substring(1))
                    targetFile.parent.createDirectories()
                    f.copyTo(targetFile)
                }
            }
        }

        zip(target, output)
        target.deleteRecursively()
    }

    private fun createMatchers(dir: Path, zip: FileSystem): List<PathMatcher> {
        return Files.walk(dir).use { stream ->
            stream.filter { it.isRegularFile() }
                .map {
                    val relativePath = it.relativeTo(dir)
                    zip.getPathMatcher("glob:/$relativePath")
                }.toList()
        }
    }
}
