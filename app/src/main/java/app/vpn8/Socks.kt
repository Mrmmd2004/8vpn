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

    /** Plain TCP connect time in ms (used when the tunnel is off), or null on failure. */
    fun tcpPing(host: String, port: Int, timeoutMs: Int): Long? = try {
        val t0 = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        (System.nanoTime() - t0) / 1_000_000
    } catch (_: Exception) {
        null
    }

    /** Returns (milliseconds to first response byte, response text) or null on failure. */
    fun httpGet(port: Int, host: String, path: String, timeoutMs: Int): Pair<Long, String>? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
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
