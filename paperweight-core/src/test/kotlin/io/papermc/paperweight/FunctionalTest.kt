package io.papermc.paperweight

import io.papermc.paperweight.util.*
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

class FunctionalTest {

    @Test
    fun `test simple test project`(@TempDir(cleanup = CleanupMode.ON_SUCCESS) tempDir: Path) {
        setupMache("fake_mache", tempDir.resolve("mache.zip"))
        setupMojang("fake_mojang", tempDir.resolve("fake_mojang"))

        val result = tempDir.copyProject("functional_test")
            .gradleRunner()
            .withArguments("applyPatches", "dependencies", ":test-server:dependencies",  "--stacktrace" , "--scan")
            .withDebug(true)
            .build()

        assertEquals(result.task(":applyPatches")?.outcome, TaskOutcome.SUCCESS)
    }

    fun setupMache(macheName: String, target: Path) {
        val macheDir = Paths.get("src/test/resources/$macheName")
        zip(macheDir, target);
    }

    fun setupMojang(mojangName: String, target: Path) {
        val mojangDir = Paths.get("src/test/resources/$mojangName")
        mojangDir.copyRecursivelyTo(target)

        val serverFolder = target.resolve("server")
        ProcessBuilder()
            .directory(serverFolder)
            .command("javac", serverFolder.resolve("Test.java").toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()

        ProcessBuilder()
            .directory(serverFolder)
            .command("jar", "-cf", "server.jar", "Test.class", "test.json")
            .redirectErrorStream(true)
            .start()
            .waitFor()

        val versionFolder = target.resolve("bundle/META-INF/versions/fake/")
        versionFolder.createDirectories()
        serverFolder.resolve("server.jar").copyTo(versionFolder.resolve("server.jar"))

        val oshiFolder = target.resolve("bundle/META-INF/libraries/com/github/oshi/oshi-core/6.4.5/")
        oshiFolder.createDirectories()
        oshiFolder.resolve("oshi-core-6.4.5.jar").writeBytes(URL("https://libraries.minecraft.net/com/github/oshi/oshi-core/6.4.5/oshi-core-6.4.5.jar").readBytes())
        zip(target.resolve("bundle"), target.resolve("bundle.jar"))
    }
}
