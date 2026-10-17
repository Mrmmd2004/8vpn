#include <jni.h>
#include "_cgo_export.h"

JNIEXPORT jint JNICALL
Java_app_vpn8_Tun2Socks_start(JNIEnv *env, jclass cls, jint fd, jint port, jint mtu, jint bufKb) {
    return (jint)t2s_start((int)fd, (int)port, (int)mtu, (int)bufKb);
}

JNIEXPORT void JNICALL
Java_app_vpn8_Tun2Socks_stop(JNIEnv *env, jclass cls) {
    t2s_stop();
}
