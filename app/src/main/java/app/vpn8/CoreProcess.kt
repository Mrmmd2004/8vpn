package app.vpn8

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs the Aether binary (shipped as libaether.so) as a child process. */
class CoreProcess(private val ctx: Context) {
    private var proc: Process? = null

    val alive: Boolean get() = proc?.isAlive == true

    @Volatile var psiphonReady = false

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
                p.inputStream.bufferedReader().forEachLine {
                    if (it.contains("psiphon is ready")) psiphonReady = true
                    Engine.log(it)
                }
            } catch (_: Exception) { }
            val code = try { if (p.waitFor(3, TimeUnit.SECONDS)) p.exitValue() else null } catch (_: Exception) { null }
            val why = when (code) {
                137 -> " = killed by Android (SIGKILL)"
                139 -> " = native crash (SIGSEGV)"
                134 -> " = aborted (SIGABRT)"
                143 -> " = stopped (SIGTERM)"
                else -> ""
            }
            Engine.log("core exited (code ${code ?: "unknown"})$why")
        }
    }

    /** SIGTERM first so Aether can stop Psiphon/Tor children, SIGKILL if it lingers. */
    fun stop(graceMs: Long = 1200) {
        val p = proc ?: return
        proc = null
        p.destroy()
        try {
            if (!p.waitFor(graceMs, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        } catch (_: InterruptedException) {
            p.destroyForcibly()
        }
    }
}
