# 8 VPN

A lightweight Android VPN client built on the **Aether** core (CluvexStudio) and **hev-socks5-tunnel**.
Jetpack Compose, Material 3, dark by default, English and Persian (RTL).

Telegram: https://t.me/Clubapp8 · GitHub: https://github.com/Mrmmd2004/8vpn

## How it works

```
apps -> VpnService (TUN) -> hev-socks5-tunnel -> SOCKS5 127.0.0.1:1819 -> Aether -> internet
```

* **VPN mode**: the whole device goes through the tunnel. **Proxy mode**: only a local SOCKS5 (and optional HTTP) proxy is opened.
* The Aether / Psiphon / Tor switcher on the home screen builds the matching Aether flags
  (`--psiphon`, `--psiphon-only`, `--psiphon-reverse`, `--tor`, `--tor-only`, `--tor-reverse`).
* Ports: SOCKS5 `1819` (configurable), Tor `1820`, Psiphon `1821`, optional HTTP `1822`.
* Smart reconnect: if the core dies, the pings fail (3 in a row, 5 s apart by default) or the network changes
  (Wi-Fi <-> mobile data), it reconnects. A network change reconnects at once. Failed attempts are retried (3 by default,
  0.5 s apart), then everything is dropped and retried with a fresh scan before giving up. The attempt counter and the
  reason for the last drop are shown on the home screen. Attempts, delay, ping count/interval, TUN MTU and TCP buffer are in Settings.

## Build with GitHub Actions

1. Upload this project to your repository (`Mrmmd2004/8vpn`).
2. Open **Actions → Build APKs → Run workflow** (or push to `main`, or push a tag like `v1.0.0` to also create a release).
3. Download the `8VPN-apks` artifact: `8VPN-armeabi-v7a.apk`, `8VPN-arm64-v8a.apk`, `8VPN-universal.apk` (v7a + v8a + x86_64).

The first build is slow (Rust + BoringSSL + Tor + Go for three ABIs). The workflow also builds `libt2s-jni.so` (tun2socks);
if that step fails the build still succeeds, the app uses hev only and the run shows a warning.

**Signing (optional).** Without secrets the APKs are signed with a debug key, which installs fine but cannot be updated
over a build signed with another key. For a stable key add these repository secrets:
`KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

**Versions.** `AETHER_REF` and `HEV_REF` at the top of `.github/workflows/main.yml` pick the upstream tags.
If the Aether tag is not named `v2.3.0`, change it there.

## Notes

* Aether is AGPL-3.0, so this project is AGPL-3.0 too (see `LICENSE`). hev-socks5-tunnel is MIT.
* Aether's trademark policy asks derivative products to use their own name and branding. The app is named **8 VPN**;
  the core is only named as a credit. Ask the Aether maintainers if you want to use their name in the UI.
* The Psiphon region list is a static list of commonly available regions; the exact set depends on Psiphon's servers.
* Not included in this lean version: home screen widget, connection history, onboarding, deep links.

---



## Two cores in one APK

- `libaether.so` = Aether **v2.1.0** (`AETHER_REF`), used only by the plain Aether / WARP route. It downloads well.
- `libaether3.so` = Aether **v2.3.0** with Tor (`AETHER3_REF`), used by every Psiphon and Tor route, together with
  `libpsiphon.so` and `liblyrebird.so` built from the same v2.3.0 tree.
- The choice is made in `Cores.libFor()` in `CoreProcess.kt`. If one of the two files is missing the app uses the other.
- Both refs can be changed when you start the workflow by hand (`aether_ref`, `aether3_ref`).
