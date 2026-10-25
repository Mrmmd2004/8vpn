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
    /** Aether finished validating the tunnel and started its SOCKS server (the port can be open earlier). */
    @Volatile var socksReady = false
    /** Psiphon could not open its database (a leftover helper still holds the lock). */
    @Volatile var datastoreFailed = false

    fun start(allArgs: List<String>) {
        // NAME=value tokens in the extra arguments are environment variables, not flags.
        val envRe = Regex("^[A-Z][A-Z0-9_]*=.*")
        val args = allArgs.filterNot { envRe.matches(it) }
        val extraEnv = allArgs.filter { envRe.matches(it) }
        val nl = ctx.applicationInfo.nativeLibraryDir
        val dir = File(ctx.filesDir, "aether").apply { mkdirs() }
        val pb = ProcessBuilder(listOf("$nl/libaether.so") + args)
            .directory(dir)
            .redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", dir.path)
            put("TMPDIR", ctx.cacheDir.path)
            put("AETHER_LOG_LEVEL", "info")
            for (kv in extraEnv) put(kv.substringBefore('='), kv.substringAfter('='))
            put("AETHER_TOR_PT", "obfs4=$nl/liblyrebird.so;webtunnel=$nl/liblyrebird.so")
        }
        Engine.log("exec: aether ${args.joinToString(" ")}")
        val p = pb.start()
        proc = p
        thread(isDaemon = true, name = "aether-log") {
            try {
                p.inputStream.bufferedReader().forEachLine {
                    if (it.contains("psiphon is ready")) psiphonReady = true
                    if (it.contains("socks5 server listening") || it.contains("tunnel validated")) socksReady = true
                    if (it.contains("tryDatastoreOpenDB") || it.contains("openDataStore")) datastoreFailed = true
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

/**
 * Kills helper processes of this app that a previous run left behind (aether, psiphon, lyrebird).
 * Stopping the core only signals aether itself; when it has to be SIGKILLed, its psiphon / tor helper
 * is orphaned and keeps the Psiphon datastore (or the SOCKS port) locked. Only processes of our own
 * uid are visible and killable, and only ones running our own native libraries are matched.
 */
object Leftovers {
    private val names = listOf("libaether.so", "libpsiphon.so", "liblyrebird.so")

    fun kill(): Int {
        val me = android.os.Process.myPid()
        var n = 0
        val dirs = File("/proc").listFiles() ?: return 0
        for (d in dirs) {
            val pid = d.name.toIntOrNull() ?: continue
            if (pid == me) continue
            val cmd = try { File(d, "cmdline").readText() } catch (_: Exception) { continue }
            if (names.any { cmd.contains(it) }) {
                try {
                    android.system.Os.kill(pid, android.system.OsConstants.SIGKILL)
                    n++
                } catch (_: Exception) { }
            }
        }
        return n
    }
}
