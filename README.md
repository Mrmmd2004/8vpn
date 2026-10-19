<div align="center">

<img src="banners/github-banner-en.png" alt="8 VPN" width="100%">

**[English](#english) · [فارسی](#فارسی)**

[![Telegram](https://img.shields.io/badge/Telegram-@Clubapp8-26A5E4?logo=telegram&logoColor=white)](https://t.me/Clubapp8)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)

</div>

---

<a id="english"></a>

# 8 VPN — English

A lightweight Android VPN client built on the **Aether** core and **hev-socks5-tunnel**.
Jetpack Compose, Material 3, dark by default, English and Persian (RTL).

📢 Telegram channel: <https://t.me/Clubapp8>

## Features

- **Two modes**: VPN (the whole device goes through the tunnel) or Proxy-only (a local SOCKS5 and optional HTTP proxy).
- **Route switcher**: Aether, Psiphon or Tor (Aether protocols: MASQUE, WireGuard, WARP-in-MASQUE, WARP-in-WARP), with Psiphon region selection and Tor bridge options.
- **Smart reconnect**: reconnects when the core dies, when pings fail, or when the network changes (Wi-Fi ↔ mobile data), then retries with a fresh scan.
- **Split tunneling**: include only selected apps or exclude them.
- **Two TUN engines**: hev-socks5-tunnel, with tun2socks (Go + gVisor) as an alternative.
- **Live info**: ping, download/upload speed, exit IP, logs.
- Quick Settings tile, start on boot, dynamic color (Android 12+), dark / light theme.
- English and Persian UI with full RTL support.

## Screenshots

<p align="center">
  <img src="screenshots/01-home.jpg" width="250" alt="Home">
  <img src="screenshots/02-connected.jpg" width="250" alt="Connected">
  <img src="screenshots/03-psiphon-region.jpg" width="250" alt="Psiphon region">
</p>

## How it works

```
apps -> VpnService (TUN) -> hev-socks5-tunnel -> SOCKS5 127.0.0.1:1819 -> Aether -> internet
```

Ports: SOCKS5 `1819` (configurable), Tor `1820`, Psiphon `1821`, optional HTTP `1822`.

## Engines and credits

<img src="banners/engines-banner.png" alt="Engines" width="100%">

| Component | Role | Link | License |
|---|---|---|---|
| **Aether** (CluvexStudio) | Core engine used for every route | <https://github.com/CluvexStudio/Aether> | AGPL-3.0 |
| **Psiphon** (psiphon-tunnel-core) | Censorship circumvention route | <https://github.com/Psiphon-Labs/psiphon-tunnel-core> | GPL-3.0 |
| **Tor** | Onion routing route | <https://www.torproject.org> | BSD-3-Clause |
| **Lyrebird** | Tor pluggable transports (obfs4, webtunnel) | <https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird> | BSD-3-Clause |
| **hev-socks5-tunnel** | TUN → SOCKS5 engine (default) | <https://github.com/heiher/hev-socks5-tunnel> | MIT |
| **tun2socks** | Alternative TUN → SOCKS5 engine (Go + gVisor) | <https://github.com/xjasonlyu/tun2socks> | GPL-3.0 |

> Please check each upstream repository for its current license terms.

## Build with GitHub Actions

1. Upload this project to your repository (`Mrmmd2004/8vpn`).
2. Open **Actions → Build APKs → Run workflow** (or push to `main`, or push a tag like `v1.0.0` to also create a release).
3. Download the `8VPN-apks` artifact:
   `8VPN-armeabi-v7a.apk`, `8VPN-arm64-v8a.apk`, `8VPN-universal.apk` (v7a + v8a + x86_64).

The first build is slow (Rust + BoringSSL + Tor + Go for three ABIs). The workflow also builds `libt2s-jni.so` (tun2socks);
if that step fails, the build still succeeds and the app uses hev only.

**Signing (optional).** Without secrets the APKs are signed with a debug key. For a stable key add these repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

**Versions.** `AETHER_REF` and `HEV_REF` at the top of `.github/workflows/main.yml` pick the upstream versions.
The default Aether ref is `v2.3.0`. When running the workflow by hand you can pass `aether_repo` (for example your own fork with a changed core) and `aether_ref` (tag, branch or full commit hash). Note that commit `21e7150` is the commit just before pull request #141 and is older than v2.2.0, so it lacks `--gool-peer` and `--gool-classic`.

## Notes

- Aether is AGPL-3.0, so this project is AGPL-3.0 too (see [LICENSE](LICENSE)).
- Aether's trademark policy asks derivative products to use their own name and branding, so the app is named **8 VPN** and the core is only credited.
- The Psiphon region list is static; the real set depends on Psiphon's servers.

---

<a id="فارسی"></a>

<div dir="rtl" align="right">

<img src="banners/github-banner-fa.png" alt="8 VPN" width="100%">

# 8VPN — فارسی

یک کلاینت سبک VPN برای اندروید، ساخته‌شده روی هسته‌ی **Aether** و **hev-socks5-tunnel**.
با Jetpack Compose و Material 3، پیش‌فرض تیره، با رابط فارسی (راست‌به‌چپ) و انگلیسی.

📢 کانال تلگرام: <https://t.me/Clubapp8>

## امکانات

- **دو حالت**: VPN (کل دستگاه از تونل رد می‌شود) یا فقط پروکسی (یک SOCKS5 محلی و در صورت نیاز پروکسی HTTP).
- **انتخاب مسیر**: Aether، Psiphon یا Tor (پروتکل‌های Aether: MASQUE و WireGuard و WARP-in-MASQUE و WARP-in-WARP)، همراه با انتخاب منطقه‌ی Psiphon و گزینه‌های بریج Tor.
- **اتصال مجدد هوشمند**: اگر هسته از کار بیفتد، پینگ‌ها شکست بخورند یا شبکه عوض شود (وای‌فای ↔ دیتای موبایل) دوباره وصل می‌شود و در صورت لزوم با اسکن تازه تلاش می‌کند.
- **تونل‌سازی انتخابی (Split tunneling)**: فقط برنامه‌های انتخاب‌شده یا همه به‌جز آن‌ها.
- **دو موتور TUN**: hev-socks5-tunnel و به‌عنوان جایگزین tun2socks (Go + gVisor).
- **اطلاعات زنده**: پینگ، سرعت دانلود و آپلود، IP خروجی و لاگ‌ها.
- کاشی تنظیمات سریع، اجرا هنگام بوت، رنگ پویا (اندروید ۱۲+)، تم تیره و روشن.
- رابط فارسی و انگلیسی با پشتیبانی کامل از راست‌به‌چپ.

## تصاویر برنامه

<p align="center">
  <img src="screenshots/01-home.jpg" width="250" alt="صفحه‌ی اصلی">
  <img src="screenshots/02-connected.jpg" width="250" alt="متصل">
  <img src="screenshots/03-psiphon-region.jpg" width="250" alt="منطقه‌ی Psiphon">
</p>

## نحوه‌ی کار

</div>

```
apps -> VpnService (TUN) -> hev-socks5-tunnel -> SOCKS5 127.0.0.1:1819 -> Aether -> internet
```

<div dir="rtl" align="right">

پورت‌ها: SOCKS5 روی `1819` (قابل تغییر)، Tor روی `1820`، Psiphon روی `1821` و HTTP اختیاری روی `1822`.

## موتورها و منابع

<img src="banners/engines-banner.png" alt="Engines" width="100%">

</div>

<div dir="rtl" align="right">

| مولفه | نقش | لینک | مجوز |
|---|---|---|---|
| **Aether** (CluvexStudio) | هسته‌ی اصلی برای همه‌ی مسیرها | <https://github.com/CluvexStudio/Aether> | AGPL-3.0 |
| **Psiphon** (psiphon-tunnel-core) | مسیر عبور از فیلترینگ | <https://github.com/Psiphon-Labs/psiphon-tunnel-core> | GPL-3.0 |
| **Tor** | مسیر روتینگ پیازی | <https://www.torproject.org> | BSD-3-Clause |
| **Lyrebird** | پلاگین‌های ترنسپورت Tor (obfs4 و webtunnel) | <https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird> | BSD-3-Clause |
| **hev-socks5-tunnel** | موتور پیش‌فرض TUN به SOCKS5 | <https://github.com/heiher/hev-socks5-tunnel> | MIT |
| **tun2socks** | موتور جایگزین TUN به SOCKS5 (Go + gVisor) | <https://github.com/xjasonlyu/tun2socks> | GPL-3.0 |

> مجوز دقیق و فعلی هر پروژه را در مخزن اصلی خودش بررسی کنید.

## ساخت با GitHub Actions

1. پروژه را در مخزن خودتان (`Mrmmd2004/8vpn`) آپلود کنید.
2. به **Actions ← Build APKs ← Run workflow** بروید (یا روی `main` پوش کنید، یا یک تگ مثل `v1.0.0` بزنید تا ریلیز هم ساخته شود).
3. آرتیفکت `8VPN-apks` را دانلود کنید:
   `8VPN-armeabi-v7a.apk`، `8VPN-arm64-v8a.apk` و `8VPN-universal.apk` (شامل v7a و v8a و x86_64).

بیلد اول کند است (Rust و BoringSSL و Tor و Go برای سه معماری). ورک‌فلو کتابخانه‌ی `libt2s-jni.so` (tun2socks) را هم می‌سازد؛
اگر آن مرحله خطا بدهد، بیلد باز هم موفق می‌شود و برنامه فقط از hev استفاده می‌کند.

**امضا (اختیاری).** بدون سکرت، APKها با کلید دیباگ امضا می‌شوند. برای کلید ثابت این سکرت‌ها را به مخزن اضافه کنید:
`KEYSTORE_BASE64` و `KEYSTORE_PASSWORD` و `KEY_ALIAS` و `KEY_PASSWORD`.

**نسخه‌ها.** مقادیر `AETHER_REF` و `HEV_REF` بالای فایل `.github/workflows/main.yml` نسخه‌ی پروژه‌های بالادستی را مشخص می‌کنند.
مقدار پیش‌فرض Aether همان `v2.3.0` است. هنگام اجرای دستی ورک‌فلو می‌توانید `aether_repo` (مثلاً فورک خودتان با هسته‌ی تغییرداده‌شده) و `aether_ref` (تگ، برنچ یا هش کامل کامیت) را بدهید. توجه: کامیت `21e7150` درست قبل از pull request شماره‌ی ۱۴۱ است و از v2.2.0 قدیمی‌تر است، پس `--gool-peer` و `--gool-classic` را ندارد.

## نکته‌ها

- Aether مجوز AGPL-3.0 دارد، پس این پروژه هم AGPL-3.0 است (فایل [LICENSE](LICENSE)).
- سیاست علامت تجاری Aether از محصولات مشتق می‌خواهد نام و برند خودشان را داشته باشند؛ برای همین برنامه **8VPN** نام دارد و از هسته فقط به‌عنوان منبع یاد می‌شود.
- فهرست مناطق Psiphon ثابت است و مجموعه‌ی واقعی به سرورهای Psiphon بستگی دارد.

</div>

---

## sing-box TUN engine

VPN mode now uses **sing-box** (the `libbox` library) as the default TUN engine:
`apps -> TUN -> sing-box (gVisor stack) -> SOCKS5 127.0.0.1:<port> -> Aether / Psiphon / Tor`.
DNS is answered by sing-box (DNS over TCP through the proxy), and for Psiphon and Tor non-DNS UDP is refused
at once so apps fall back from QUIC to TCP. If the library is missing or fails to start, the app falls back to
hev-socks5-tunnel automatically. The engine can be changed in Settings.

`libbox.aar` is built by the `libbox` job of `.github/workflows/main.yml` (sing-box `v1.13.21`, tag
`with_gvisor` + `with_clash_api` and a few small ones, arm + arm64) and placed in `app/libs/`. Use the `singbox_ref` input to build another sing-box tag.
For a local Gradle build, put a `libbox.aar` into `app/libs/` first.

## موتور TUN با sing-box

حالت VPN اکنون به‌صورت پیش‌فرض از **sing-box** (کتابخانه `libbox`) استفاده می‌کند:
`برنامه‌ها -> TUN -> sing-box -> SOCKS5 روی 127.0.0.1 -> Aether / Psiphon / Tor`.
DNS را خود sing-box (روی TCP از طریق پروکسی) جواب می‌دهد و برای Psiphon و Tor، UDP غیر از DNS فوراً رد می‌شود
تا برنامه‌ها از QUIC به TCP برگردند. اگر کتابخانه نباشد یا اجرا نشود، برنامه خودکار به hev برمی‌گردد.
موتور را می‌توان از تنظیمات عوض کرد. فایل `libbox.aar` را مرحله‌ی `libbox` در ورک‌فلو می‌سازد.
