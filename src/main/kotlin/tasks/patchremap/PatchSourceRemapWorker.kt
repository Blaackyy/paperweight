/*
 * paperweight is a Gradle plugin for the PaperMC project. It uses
 * some code and systems originally from ForgeGradle.
 *
 * Copyright (C) 2020 Kyle Wood
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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

package io.papermc.paperweight.tasks.patchremap

import io.papermc.paperweight.tasks.sourceremap.ConstructorsData
import io.papermc.paperweight.tasks.sourceremap.ParamNames
import io.papermc.paperweight.tasks.sourceremap.SrgParameterRemapper
import java.nio.file.Files
import java.nio.file.Path
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.mercury.Mercury
import org.cadixdev.mercury.remapper.MercuryRemapper

class PatchSourceRemapWorker(
    mappings: MappingSet,
    classpath: Collection<Path>,
    paramNames: ParamNames,
    constructorsData: ConstructorsData,
    private val inputDir: Path,
    private val outputDir: Path
) {

    private val merc : Mercury = Mercury()

    init {
        merc.classPath.addAll(classpath)

        merc.processors.addAll(listOf(
                 MercuryRemapper.create(mappings),
                 SrgParameterRemapper(mappings, constructorsData, paramNames)
        ))

        merc.isGracefulClasspathChecks = true
    }

    fun remap() {
        setup()

        println("mapping back to srg")

        merc.rewrite(inputDir, outputDir)

        cleanup()
    }

    private fun setup() {
        outputDir.deleteRecursively()
        Files.createDirectories(outputDir)
    }

    private fun cleanup() {
        inputDir.deleteRecursively()
        Files.move(outputDir, inputDir)
        outputDir.deleteRecursively()
    }

    private fun Path.deleteRecursively() {
        if (Files.notExists(this)) {
            return
        }
        Files.walk(this).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
