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

package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.defaultOutput
import io.papermc.paperweight.util.file
import io.papermc.paperweight.util.fileOrNull
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

open class AddMissingSpigotClassMappings : DefaultTask() {

    @InputFile
    val classSrg: RegularFileProperty = project.objects.fileProperty()
    @InputFile
    val memberSrg: RegularFileProperty = project.objects.fileProperty()

    @Optional
    @InputFile
    val missingClassEntriesSrg: RegularFileProperty = project.objects.fileProperty()
    @Optional
    @InputFile
    val missingMemberEntriesSrg: RegularFileProperty = project.objects.fileProperty()

    @OutputFile
    val outputClassSrg: RegularFileProperty = defaultOutput("class.csrg")
    @OutputFile
    val outputMemberSrg: RegularFileProperty = defaultOutput("member.csrg")

    @TaskAction
    fun run() {
        addLines(classSrg.file, missingClassEntriesSrg.fileOrNull, outputClassSrg.file)
        addLines(memberSrg.file, missingMemberEntriesSrg.fileOrNull, outputMemberSrg.file)
    }

    private fun addLines(inFile: File, appendFile: File?, outputFile: File) {
        val lines = mutableListOf<String>()
        inFile.bufferedReader().use { reader ->
            lines.addAll(reader.readLines())
        }
        appendFile?.bufferedReader()?.use { reader ->
            lines.addAll(reader.readLines())
        }
        lines.sort()
        outputFile.bufferedWriter().use { writer ->
            lines.forEach(writer::appendln)
        }
    }
}
