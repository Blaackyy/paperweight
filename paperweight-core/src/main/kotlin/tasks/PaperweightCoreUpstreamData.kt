/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2021 Kyle Wood (DemonWav)
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

package io.papermc.paperweight.core.tasks

import io.papermc.paperweight.util.UpstreamData
import io.papermc.paperweight.util.gson
import io.papermc.paperweight.util.path
import javax.inject.Inject
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PaperweightCoreUpstreamData : DefaultTask() {

    @get:InputFile
    abstract val decompiledJar: RegularFileProperty

    @get:InputDirectory
    abstract val mcLibrariesDir: DirectoryProperty

    @get:OutputFile
    abstract val dataFile: RegularFileProperty

    @get:Inject
    abstract val providers: ProviderFactory

    @TaskAction
    fun run() {
        val dataFilePath = dataFile.path

        dataFilePath.parent.createDirectories()

        val data = UpstreamData(decompiledJar.path, mcLibrariesDir.path)
        dataFilePath.bufferedWriter(Charsets.UTF_8).use { writer ->
            gson.toJson(data, writer)
        }
    }
}
