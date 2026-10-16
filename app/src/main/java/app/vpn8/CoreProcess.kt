package app.vpn8

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs the Aether binary (shipped as libaether.so) as a child process. */
class CoreProcess(private val ctx: Context) {
    private var proc: Process? = null

    val alive: Boolean get() = proc?.isAlive == true

    fun start(args: List<String>) {
        val nl = ctx.applicationInfo.nativeLibraryDir
        val dir = File(ctx.filesDir, "aether").apply { mkdirs() }
        val pb = ProcessBuilder(listOf("$nl/libaether.so") + args)
            .directory(dir)
            .redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", dir.path)
            put("TMPDIR", ctx.cacheDir.path)
            put("AETHER_LOG_LEVEL", "info")
            put("AETHER_TOR_PT", "obfs4=$nl/liblyrebird.so;webtunnel=$nl/liblyrebird.so")
        }
        Engine.log("exec: aether ${args.joinToString(" ")}")
        val p = pb.start()
        proc = p
        thread(isDaemon = true, name = "aether-log") {
            try {
                p.inputStream.bufferedReader().forEachLine { Engine.log(it) }
            } catch (_: Exception) { }
            Engine.log("core exited (code ${runCatching { p.exitValue() }.getOrDefault(-1)})")
        }
    }

    fun stop() {
        val p = proc ?: return
        proc = null
        p.destroy()
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: InterruptedException) {
            p.destroyForcibly()
        }
    }
}
