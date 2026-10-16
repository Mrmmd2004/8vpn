# hev-socks5-tunnel registers its natives against this exact class (JNI RegisterNatives).
-keep class hev.htproxy.TProxyService { *; }
-keepclasseswithmembernames class * { native <methods>; }
