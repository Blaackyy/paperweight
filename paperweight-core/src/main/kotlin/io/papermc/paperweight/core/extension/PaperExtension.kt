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

package io.papermc.paperweight.core.extension

import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.data.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.*

open class PaperExtension(objects: ObjectFactory, layout: ProjectLayout) {

    @Suppress("MemberVisibilityCanBePrivate")
    val baseTargetDir: DirectoryProperty = objects.dirWithDefault(layout, ".")
    val spigotApiPatchDir: DirectoryProperty = objects.dirFrom(baseTargetDir, "patches/api")
    val spigotServerPatchDir: DirectoryProperty = objects.dirFrom(baseTargetDir, "patches/server")
    val paperApiDir: DirectoryProperty = objects.dirFrom(baseTargetDir, "Paper-API")
    val paperServerDir: DirectoryProperty = objects.dirFrom(baseTargetDir, "Paper-Server")

    @Suppress("MemberVisibilityCanBePrivate")
    val buildDataDir: DirectoryProperty = objects.dirWithDefault(layout, "build-data")
    val devImports: RegularFileProperty = objects.fileFrom(buildDataDir, "dev-imports.txt")
    val additionalAts: RegularFileProperty = objects.fileFrom(buildDataDir, "paper.at")
    val reobfMappingsPatch: RegularFileProperty = objects.fileProperty()

    val reobfPackagesToFix: ListProperty<String> = objects.listProperty()

    val patchSets: ListProperty<PatchSet> = objects.listProperty()
}
