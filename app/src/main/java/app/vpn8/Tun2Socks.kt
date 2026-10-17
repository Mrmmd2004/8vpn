package app.vpn8

/** JNI binding to libt2s-jni.so (xjasonlyu/tun2socks, Go + gVisor netstack). */
object Tun2Socks {
    /** False when the library is missing from the APK (the service then falls back to hev). */
    val available: Boolean = try {
        System.loadLibrary("t2s-jni")
        true
    } catch (_: Throwable) {
        false
    }

    /** Starts the stack on a dup of the TUN fd it now owns. Returns 0 on success. */
    @JvmStatic external fun start(fd: Int, socksPort: Int, mtu: Int, bufKb: Int): Int

    @JvmStatic external fun stop()
}
