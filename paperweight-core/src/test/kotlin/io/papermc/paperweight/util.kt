package io.papermc.paperweight

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import org.gradle.testkit.runner.GradleRunner

fun Path.copyProject(resourcesProjectName: String): ProjectFiles {
    Paths.get("src/test/resources/$resourcesProjectName")
        .copyToRecursively(this, followLinks = false)
    return ProjectFiles(this)
}

class ProjectFiles(val projectDir: Path) {
    val gradleProperties: Path = resolve("gradle.properties")
    val buildGradle: Path = resolve("build.gradle")
    val buildGradleKts: Path = resolve("build.gradle.kts")
    val settingsGradle: Path = resolve("settings.gradle")
    val settingsGradleKts: Path = resolve("settings.gradle.kts")

    fun resolve(path: String): Path = projectDir.resolve(path)

    fun gradleRunner(): GradleRunner = GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
}
