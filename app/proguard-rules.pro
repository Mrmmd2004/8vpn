# hev-socks5-tunnel registers its natives against this exact class (JNI RegisterNatives).
-keep class hev.htproxy.TProxyService { *; }
-keepclasseswithmembernames class * { native <methods>; }

# tun2socks registers its natives by name (Java_app_vpn8_Tun2Socks_*).
-keep class app.vpn8.Tun2Socks { *; }
