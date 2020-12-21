import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import org.gradle.api.publish.maven.MavenPom
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    eclipse
    `kotlin-dsl`
    `maven-publish`
    id("net.minecrell.licenser") version "0.4.1"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "io.papermc.paperweight"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs = listOf("-Xjvm-default=enable")
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://files.minecraftforge.net/maven/")
    maven("https://maven.fabricmc.net/")
    mavenLocal()
}

val shade: Configuration by configurations.creating
configurations.implementation {
    extendsFrom(shade)
}

dependencies {
    shade("org.apache.httpcomponents:httpclient:4.5.13")
    shade("com.github.salomonbrys.kotson:kotson:2.5.0")

    // ASM for inspection
    val asmVersion = "9.0"
    shade("org.ow2.asm:asm:$asmVersion")
    shade("org.ow2.asm:asm-tree:$asmVersion")

    // Cadix
    val lorenzVersion = "0.5.6"
    shade("org.cadixdev:lorenz:$lorenzVersion")
    shade("org.cadixdev:lorenz-asm:$lorenzVersion")
    shade("org.cadixdev:lorenz-io-proguard:$lorenzVersion")
    shade("org.cadixdev:atlas:0.2.0")
    shade("org.cadixdev:at:0.1.0-rc1")
    shade("org.cadixdev:mercury:0.1.0-rc2-PW-SNAPSHOT")

    shade("net.fabricmc:lorenz-tiny:3.0.0")
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

eclipse {
    classpath {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

license {
    header = file("license/copyright.txt")
    include("**/*.kt")
}

tasks.shadowJar {
    configurations = listOf(shade)

    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
    }

    val prefix = "paper.libs"
    listOf(
        "com.github.salomonbrys.kotson",
        "com.google.gson",
        "me.jamiemansfield",
        "net.fabricmc",
        "org.apache.commons.codec",
        "org.apache.commons.logging",
        "org.apache.felix",
        "org.apache.http",
        "org.cadixdev",
        "org.eclipse",
        "org.objectweb",
        "org.osgi"
    ).forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
}

val isSnapshot = version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        register<MavenPublication>("shadow") {
            groupId = project.group.toString()
            artifactId = "io.papermc.paperweight.gradle.plugin"
            version = project.version.toString()

            project.shadow.component(this)
            artifact(project.tasks.named("sourcesJar"))

            for (artifact in artifacts) {
                if (artifact.classifier == "all") {
                    artifact.classifier = null
                }
            }

            withoutBuildIdentifier()
            pom {
                pomConfig()
            }
        }

        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            withoutBuildIdentifier()
            pom {
                pomConfig()
            }
        }

        repositories {
            val url = if (isSnapshot) {
                "https://repo.demonwav.com/snapshots"
            } else {
                "https://repo.demonwav.com/releases"
            }
            maven(url) {
                credentials(PasswordCredentials::class)
                name = "demonwav"
            }
        }
    }
}

fun MavenPom.pomConfig() {
    val repoUrl = "https://github.com/DemonWav/paperweight"

    name.set("paperweight")
    description.set("Gradle plugin for the PaperMC project")
    url.set(repoUrl)
    inceptionYear.set("2020")
    packaging = "jar"

    licenses {
        license {
            name.set("LGPLv2.1")
            url.set("$repoUrl/blob/master/license/LGPLv2.1.txt")
            distribution.set("repo")
        }
    }

    issueManagement {
        system.set("GitHub")
        url.set("$repoUrl/issues")
    }

    developers {
        developer {
            id.set("DemonWav")
            name.set("Kyle Wood")
            email.set("demonwav@gmail.com")
            url.set("https://github.com/DemonWav")
        }
    }

    scm {
        url.set(repoUrl)
        connection.set("scm:git:$repoUrl.git")
        developerConnection.set(connection)
    }
}
