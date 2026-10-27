package app.vpn8

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Tiny SOCKS5 client used for the ping probe and the exit-IP lookup. */
object Socks {
    private fun readFully(i: InputStream, b: ByteArray) {
        var n = 0
        while (n < b.size) {
            val r = i.read(b, n, b.size - n)
            if (r < 0) throw java.io.IOException("eof")
            n += r
        }
    }

    /**
     * Downloads up to [maxBytes] of [path] over plain HTTP through the local SOCKS5 proxy and
     * returns (HTTP status, body bytes received, milliseconds), or null when no HTTP answer
     * arrived. Stops after [budgetMs] so a dead-slow link cannot block for long.
     */
    fun download(port: Int, host: String, path: String, maxBytes: Long, budgetMs: Long): Triple<Int, Long, Long>? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(LocalHost.host, port), 5000)
            s.soTimeout = 5000
            val o = s.getOutputStream()
            val i = s.getInputStream()
            o.write(byteArrayOf(5, 1, 0)); o.flush()
            val r = ByteArray(2); readFully(i, r)
            if (r[0] != 5.toByte() || r[1] != 0.toByte()) throw java.io.IOException("auth")
            val hb = host.toByteArray()
            o.write(byteArrayOf(5, 1, 0, 3, hb.size.toByte()) + hb + byteArrayOf(0, 80)); o.flush()
            val h = ByteArray(4); readFully(i, h)
            if (h[1] != 0.toByte()) throw java.io.IOException("connect refused")
            val skip = when (h[3].toInt()) {
                1 -> 4 + 2
                4 -> 16 + 2
                else -> { val l = ByteArray(1); readFully(i, l); (l[0].toInt() and 0xff) + 2 }
            }
            readFully(i, ByteArray(skip))
            val t0 = System.nanoTime()
            o.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()); o.flush()

            val head = StringBuilder()
            val buf = ByteArray(16 * 1024)
            var body = 0L
            var status = 0
            var headDone = false
            val deadline = t0 + budgetMs * 1_000_000
            try {
                while (System.nanoTime() < deadline) {
                    val n = i.read(buf)
                    if (n < 0) break
                    if (!headDone) {
                        head.append(String(buf, 0, n, Charsets.ISO_8859_1))
                        val end = head.indexOf("\r\n\r\n")
                        if (end >= 0) {
                            headDone = true
                            status = head.substring(0, head.indexOf("\r\n")).split(" ").getOrNull(1)?.toIntOrNull() ?: 0
                            body = (head.length - (end + 4)).toLong()
                        } else if (head.length > 8192) break
                    } else {
                        body += n
                    }
                    if (headDone && (status != 200 || body >= maxBytes)) break
                }
            } catch (_: java.net.SocketTimeoutException) { }
            if (!headDone) throw java.io.IOException("no header")
            Triple(status, body, (System.nanoTime() - t0) / 1_000_000)
        }
    } catch (_: Exception) {
        null
    }

    /** Returns (milliseconds to first response byte, response text) or null on failure. */
    fun httpGet(port: Int, host: String, path: String, timeoutMs: Int): Pair<Long, String>? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(LocalHost.host, port), timeoutMs)
            s.soTimeout = timeoutMs
            val o = s.getOutputStream()
            val i = s.getInputStream()
            val t0 = System.nanoTime()
            o.write(byteArrayOf(5, 1, 0)); o.flush()
            val r = ByteArray(2); readFully(i, r)
            if (r[0] != 5.toByte() || r[1] != 0.toByte()) throw java.io.IOException("auth")
            val hb = host.toByteArray()
            o.write(byteArrayOf(5, 1, 0, 3, hb.size.toByte()) + hb + byteArrayOf(0, 80)); o.flush()
            val h = ByteArray(4); readFully(i, h)
            if (h[1] != 0.toByte()) throw java.io.IOException("connect refused")
            val skip = when (h[3].toInt()) {
                1 -> 4 + 2
                4 -> 16 + 2
                else -> { val l = ByteArray(1); readFully(i, l); (l[0].toInt() and 0xff) + 2 }
            }
            readFully(i, ByteArray(skip))
            o.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()); o.flush()
            val first = i.read()
            if (first < 0) throw java.io.IOException("empty")
            val ms = (System.nanoTime() - t0) / 1_000_000
            val sb = StringBuilder().append(first.toChar())
            val buf = ByteArray(1024)
            try {
                while (sb.length < 4096) {
                    val n = i.read(buf)
                    if (n < 0) break
                    sb.append(String(buf, 0, n))
                }
            } catch (_: Exception) { }
            ms to sb.toString()
        }
    } catch (_: Exception) {
        null
    }
}
