package app.vpn8

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks the latest GitHub release of 8 VPN and picks the APK that matches this device. */
object Updater {
    private const val API = "https://api.github.com/repos/Mrmmd2004/8vpn/releases/latest"

    data class Info(val tag: String, val newer: Boolean, val url: String, val isApk: Boolean)

    suspend fun check(): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            val con = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "8vpn-android")
            }
            try {
                if (con.responseCode != 200) error("HTTP ${con.responseCode}")
                val j = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                val tag = j.getString("tag_name")
                val page = j.optString("html_url", "https://github.com/Mrmmd2004/8vpn/releases/latest")
                val apk = pickApk(j)
                Info(tag, isNewer(tag, BuildConfig.VERSION_NAME), apk ?: page, apk != null)
            } finally {
                con.disconnect()
            }
        }
    }

    private fun pickApk(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        val all = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk") }
        fun find(key: String) = all.firstOrNull { it.optString("name").contains(key, true) }
            ?.optString("browser_download_url")
        for (abi in Build.SUPPORTED_ABIS) {
            val hit = when (abi) {
                "arm64-v8a", "armeabi-v7a" -> find(abi)
                else -> null
            }
            if (hit != null) return hit
        }
        return find("universal")
    }

    private fun parts(v: String) = v.trim().trimStart('v', 'V').split('.', '-', '_')
        .map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK into the app cache, reporting 0..100 (or -1 while the size is unknown). */
    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "update.part")
            val out = File(dir, "8VPN-update.apk")
            val con = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "8vpn-android")
            }
            try {
                if (con.responseCode != 200) error("HTTP ${con.responseCode}")
                val total = con.contentLengthLong
                var done = 0L
                var last = -2
                con.inputStream.use { input ->
                    tmp.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != last) { last = pct; onProgress(pct) }
                        }
                    }
                }
                if (total > 0 && done != total) error("incomplete download")
                if (!tmp.renameTo(out)) error("cannot save file")
                out
            } finally {
                con.disconnect()
            }
        }
    }

    /** Opens the system installer. Returns false when the user still has to allow installs from this app. */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.update", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
