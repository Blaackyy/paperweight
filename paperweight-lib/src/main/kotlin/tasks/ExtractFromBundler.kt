/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2021 Kyle Wood (DenWav)
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

import com.google.gson.JsonObject
import io.papermc.paperweight.util.*
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

@CacheableTask
abstract class ExtractFromBundler : BaseTask() {

    @get:Classpath
    abstract val bundlerJar: RegularFileProperty

    @get:OutputFile
    abstract val serverJar: RegularFileProperty

    @get:OutputFile
    abstract val serverLibrariesTxt: RegularFileProperty

    @get:OutputDirectory
    abstract val serverLibraryJars: DirectoryProperty

    override fun init() {
        super.init()
        serverJar.set(defaultOutput())
    }

    @TaskAction
    fun run() {
        ServerBundler.extractFromBundler(
            bundlerJar.path,
            serverJar.path,
            serverLibraryJars.path,
            serverLibrariesTxt.path
        )
    }
}

object ServerBundler {
    fun extractFromBundler(
        bundlerJar: Path,
        serverJar: Path,
        serverLibraryJars: Path,
        serverLibrariesTxt: Path?,
    ) {
        bundlerJar.openZip().use { bundlerFs ->
            extractServerJar(bundlerFs, serverJar)
            extractLibraryJars(bundlerFs, serverLibraryJars)
            serverLibrariesTxt?.let { writeLibrariesTxt(bundlerFs, it) }
        }
    }

    private fun extractServerJar(bundlerFs: FileSystem, serverJar: Path) {
        val versionId = gson.fromJson<JsonObject>(bundlerFs.getPath("version.json"))["id"].asString
        val versions = bundlerFs.getPath("META-INF/versions.list").readLines()
            .map { it.split('\t') }
            .associate { it[1] to it[2] }
        val serverJarPath = bundlerFs.getPath("META-INF/versions/${versions[versionId]}")

        serverJar.parent.createDirectories()
        serverJarPath.copyTo(serverJar, overwrite = true)
    }

    private fun extractLibraryJars(bundlerFs: FileSystem, serverLibraryJars: Path) {
        serverLibraryJars.deleteRecursively()
        serverLibraryJars.parent.createDirectories()
        bundlerFs.getPath("META-INF/libraries").copyRecursivelyTo(serverLibraryJars)
    }

    private fun writeLibrariesTxt(bundlerFs: FileSystem, serverLibrariesTxt: Path) {
        val libs = bundlerFs.getPath("META-INF/libraries.list").readLines()
            .map { it.split('\t')[1] }

        serverLibrariesTxt.parent.createDirectories()
        serverLibrariesTxt.writeLines(libs)
    }
}
