package io.papermc.paperweight.tasks.softspoon

import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(because = "Always apply patches")
abstract class ApplyFeaturePatches : ControllableOutputTask() {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputDirectory
    abstract val repo: DirectoryProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputDirectory
    abstract val patches: DirectoryProperty

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Input
    abstract val verbose: Property<Boolean>

    override fun init() {
        printOutput.convention(true).finalizeValueOnRead()
        verbose.convention(true)
        //verbose.convention(providers.verboseApplyPatches())
    }

    @TaskAction
    fun run() {
        Git.checkForGit()

        val repoPath = repo.convertToPath()

        val git = Git(repoPath)

        if (git("checkout", "main").runSilently(silenceErr = true) != 0) {
            git("checkout", "-b", "main").runSilently(silenceErr = true)
        }
        git("reset", "--hard", "file").executeSilently(silenceErr = true)
        git("gc").runSilently(silenceErr = true)

        applyGitPatches(git, "server repo", repoPath, patches.convertToPath(), printOutput.get(), verbose.get())
    }
}
