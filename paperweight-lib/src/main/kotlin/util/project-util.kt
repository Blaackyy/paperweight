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

package io.papermc.paperweight.util

import io.papermc.paperweight.tasks.FixJarForReobf
import io.papermc.paperweight.tasks.RemapJar
import kotlin.io.path.exists
import kotlin.io.path.forEachLine
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*

fun Project.setupServerProject(
    parent: Project,
    remappedJar: Any,
    libsFile: Any,
    packagesToFix: ListProperty<String>,
    reobfConfig: RemapJar.() -> Unit
): TaskProvider<RemapJar>? {
    if (!projectDir.exists()) {
        return null
    }

    plugins.apply("java")

    configurations {
        named("implementation") {
            withDependencies {
                dependencies {
                    val libs = libsFile.convertToPathOrNull()
                    if (libs != null && libs.exists()) {
                        libs.forEachLine { line ->
                            add("implementation", line)
                        }
                    }
                }
            }
        }
    }
    dependencies.apply {
        add("implementation", parent.files(remappedJar))
    }

    apply(plugin = "com.github.johnrengelman.shadow")
    return createBuildTasks(packagesToFix, reobfConfig)
}

private fun Project.createBuildTasks(packagesToFix: ListProperty<String>, reobfConfig: RemapJar.() -> Unit): TaskProvider<RemapJar> {
    val shadowJar: TaskProvider<Jar> = tasks.named("shadowJar", Jar::class)

    val fixJarForReobf by tasks.registering<FixJarForReobf> {
        dependsOn(shadowJar)

        inputJar.set(shadowJar.flatMap { it.archiveFile })
        packagesToProcess.set(packagesToFix)
    }

    val reobfJar by tasks.registering<RemapJar> {
        group = "paperweight"
        description = "Re-obfuscate the built jar to obf mappings"

        inputJar.set(fixJarForReobf.flatMap { it.outputJar })

        reobfConfig()

        fromNamespace.set(Constants.DEOBF_NAMESPACE)
        toNamespace.set(Constants.SPIGOT_NAMESPACE)
        remapper.from(rootProject.configurations.named(Constants.REMAPPER_CONFIG))

        outputJar.set(buildDir.resolve("libs/${shadowJar.get().archiveBaseName.get()}-reobf.jar"))
    }

    return reobfJar
}
