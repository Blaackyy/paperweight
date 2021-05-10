package io.papermc.paperweight.plugin

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import io.papermc.paperweight.DownloadService
import io.papermc.paperweight.ext.PaperweightExtension
import io.papermc.paperweight.tasks.DownloadTask
import io.papermc.paperweight.tasks.SetupMcLibraries
import io.papermc.paperweight.util.Constants
import io.papermc.paperweight.util.MinecraftManifest
import io.papermc.paperweight.util.cache
import io.papermc.paperweight.util.download
import io.papermc.paperweight.util.ext
import io.papermc.paperweight.util.fromJson
import io.papermc.paperweight.util.gson
import io.papermc.paperweight.util.registering
import java.io.File
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer

@Suppress("MemberVisibilityCanBePrivate")
open class InitialTasks(
    project: Project,
    tasks: TaskContainer = project.tasks,
    cache: File = project.layout.cache,
    extension: PaperweightExtension = project.ext,
    downloadService: Provider<DownloadService> = project.download
) {

    val downloadMcManifest by tasks.registering<DownloadTask> {
        url.set(Constants.MC_MANIFEST_URL)
        outputFile.set(cache.resolve(Constants.MC_MANIFEST))

        downloader.set(downloadService)
    }
    private val mcManifest = downloadMcManifest.flatMap { it.outputFile }.map { gson.fromJson<MinecraftManifest>(it) }

    val downloadMcVersionManifest by tasks.registering<DownloadTask> {
        url.set(
            mcManifest.zip(extension.minecraftVersion) { manifest, version ->
                manifest.versions.first { it.id == version }.url
            }
        )
        outputFile.set(cache.resolve(Constants.VERSION_JSON))

        downloader.set(downloadService)
    }
    private val versionManifest = downloadMcVersionManifest.flatMap { it.outputFile }.map { gson.fromJson<JsonObject>(it) }

    val setupMcLibraries by tasks.registering<SetupMcLibraries> {
        dependencies.set(
            versionManifest.map { version ->
                version["libraries"].array.map { library ->
                    library["name"].string
                }
            }
        )
        outputFile.set(cache.resolve(Constants.MC_LIBRARIES))
    }

    val downloadMappings by tasks.registering<DownloadTask> {
        url.set(
            versionManifest.map { version ->
                version["downloads"]["server_mappings"]["url"].string
            }
        )
        outputFile.set(cache.resolve(Constants.SERVER_MAPPINGS))

        downloader.set(downloadService)
    }
}
