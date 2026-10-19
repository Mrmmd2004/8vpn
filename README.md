<div align="center">

<img src="banners/github-banner-en.png" alt="8 VPN" width="100%">

**[English](#english) · [فارسی](#فارسی)**

[![Telegram](https://img.shields.io/badge/Telegram-@Clubapp8-26A5E4?logo=telegram&logoColor=white)](https://t.me/Clubapp8)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
![Version](https://img.shields.io/badge/version-2.8-8D9BFF)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)

</div>

---

<a id="english"></a>

# 8 VPN — English

A lightweight Android VPN client built on the **Aether** core, with **sing-box** (libbox) as the default TUN engine and **hev-socks5-tunnel** as a fallback.
Jetpack Compose, Material 3, dark and light themes, English and Persian (RTL).

📢 Telegram channel: <https://t.me/Clubapp8>

## What's new in 2.8

- **Brand-new interface.** A redesigned home screen with an animated connect button, a one-glance stats card (ping, download, upload, link speed) and a collapsible connection setup panel.
- **Tidier settings.** Options are grouped into cards (General, Core & protocol, Routing, Psiphon, Tor, Advanced) with rounded inputs and one-tap choices.
- **Dark and light themes**, dynamic color on Android 12+, and smooth screen transitions.
- **sing-box core works great.** The `libbox` TUN engine runs without problems and is the default VPN engine; hev-socks5-tunnel stays available as a fallback.

## Download

The latest builds are always published in [Releases](../../releases) (`8VPN-arm64-v8a.apk` for most phones, `8VPN-armeabi-v7a.apk` for older ones, or `8VPN-universal.apk` if you are unsure).
Check the Telegram channel [@Clubapp8](https://t.me/Clubapp8) or X regularly to stay up to date with the newest versions.

## Features

- **Two modes**: VPN (the whole device goes through the tunnel) or Proxy-only (a local SOCKS5 and optional HTTP proxy).
- **Route switcher**: Aether, Psiphon or Tor (Aether protocols: MASQUE, WireGuard, WARP-in-MASQUE, WARP-in-WARP), with Psiphon region selection and Tor bridge options.
- **Smart reconnect**: reconnects when the core dies, when pings fail, or when the network changes (Wi-Fi ↔ mobile data), then retries with a fresh scan.
- **Split tunneling**: include only selected apps or exclude them.
- **TUN engines**: sing-box (default, gVisor stack) with automatic fallback to hev-socks5-tunnel; the engine can be changed in Settings.
- **Live info**: ping, download/upload speed, link speed, exit IP, logs.
- Quick Settings tile, start on boot, dynamic color (Android 12+), dark / light theme.
- English and Persian UI with full RTL support.

## Screenshots

<p align="center">
  <img src="screenshots/01-home.jpg" width="190" alt="Home">
  <img src="screenshots/02-connected.jpg" width="190" alt="Connected">
  <img src="screenshots/03-psiphon-region.jpg" width="190" alt="Psiphon region">
  <img src="screenshots/04-settings.jpg" width="190" alt="Settings">
  <img src="screenshots/05-light-theme.jpg" width="190" alt="Light theme">
</p>

## How it works

```
apps -> VpnService (TUN) -> sing-box (libbox, gVisor) -> SOCKS5 127.0.0.1:1819 -> Aether / Psiphon / Tor -> internet
```

DNS is answered by sing-box (DNS over TCP through the proxy). For Psiphon and Tor, non-DNS UDP is refused at once so apps fall back from QUIC to TCP.
If the library is missing or fails to start, the app falls back to hev-socks5-tunnel automatically.

Ports: SOCKS5 `1819` (configurable), Tor `1820`, Psiphon `1821`, optional HTTP `1822`.

## Engines and credits

<img src="banners/engines-banner.png" alt="Engines" width="100%">

| Component | Role | Link | License |
|---|---|---|---|
| **Aether** (CluvexStudio) | Core engine used for every route | <https://github.com/CluvexStudio/Aether> | AGPL-3.0 |
| **Psiphon** (psiphon-tunnel-core) | Censorship circumvention route | <https://github.com/Psiphon-Labs/psiphon-tunnel-core> | GPL-3.0 |
| **Tor** | Onion routing route | <https://www.torproject.org> | BSD-3-Clause |
| **Lyrebird** | Tor pluggable transports (obfs4, webtunnel) | <https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird> | BSD-3-Clause |
| **sing-box** (SagerNet) | Default TUN → SOCKS5 engine (`libbox`, gVisor stack) | <https://github.com/SagerNet/sing-box> | GPL-3.0 (see upstream) |
| **hev-socks5-tunnel** | Fallback TUN → SOCKS5 engine | <https://github.com/heiher/hev-socks5-tunnel> | MIT |

> Please check each upstream repository for its current license terms.

## Notes

- Aether is AGPL-3.0, so this project is AGPL-3.0 too (see [LICENSE](LICENSE)).
- Aether's trademark policy asks derivative products to use their own name and branding, so the app is named **8 VPN** and the core is only credited.
- The Psiphon region list is static; the real set depends on Psiphon's servers.

---

<a id="فارسی"></a>

<div dir="rtl" align="right">

<img src="banners/github-banner-fa.png" alt="8 VPN" width="100%">

# 8VPN — فارسی

یک کلاینت سبک VPN برای اندروید، ساخته‌شده روی هسته‌ی **Aether**، با **sing-box** (کتابخانه‌ی libbox) به‌عنوان موتور پیش‌فرض TUN و **hev-socks5-tunnel** به‌عنوان جایگزین.
با Jetpack Compose و Material 3، تم تیره و روشن، با رابط فارسی (راست‌به‌چپ) و انگلیسی.

📢 کانال تلگرام: <https://t.me/Clubapp8>

## تازه‌های نسخه‌ی ۲.۸

- **رابط کاملاً جدید.** صفحه‌ی اصلی بازطراحی شد: دکمه‌ی اتصال متحرک، کارت آمار (پینگ، دانلود، آپلود و سرعت لینک) و پنل جمع‌شونده‌ی تنظیمات اتصال.
- **تنظیمات مرتب‌تر.** گزینه‌ها در کارت‌های جدا (عمومی، هسته و پروتکل، مسیریابی، Psiphon، Tor، پیشرفته) دسته‌بندی شده‌اند.
- **تم تیره و روشن**، رنگ پویا در اندروید ۱۲+ و انتقال نرم بین صفحه‌ها.
- **هسته‌ی sing-box بدون مشکل کار می‌کند.** موتور TUN با `libbox` پیش‌فرض حالت VPN است و hev-socks5-tunnel به‌عنوان جایگزین در دسترس می‌ماند.

## دانلود

جدیدترین نسخه‌ها همیشه در بخش [Releases](../../releases) منتشر می‌شوند (فایل `8VPN-arm64-v8a.apk` برای بیشتر گوشی‌ها، `8VPN-armeabi-v7a.apk` برای گوشی‌های قدیمی‌تر و `8VPN-universal.apk` اگر مطمئن نیستید).
برای باخبر شدن از جدیدترین نسخه‌ها، کانال تلگرام [@Clubapp8](https://t.me/Clubapp8) یا X را همیشه چک کنید.

## امکانات

- **دو حالت**: VPN (کل دستگاه از تونل رد می‌شود) یا فقط پروکسی (یک SOCKS5 محلی و در صورت نیاز پروکسی HTTP).
- **انتخاب مسیر**: Aether، Psiphon یا Tor (پروتکل‌های Aether: MASQUE و WireGuard و WARP-in-MASQUE و WARP-in-WARP)، همراه با انتخاب منطقه‌ی Psiphon و گزینه‌های بریج Tor.
- **اتصال مجدد هوشمند**: اگر هسته از کار بیفتد، پینگ‌ها شکست بخورند یا شبکه عوض شود (وای‌فای ↔ دیتای موبایل) دوباره وصل می‌شود و در صورت لزوم با اسکن تازه تلاش می‌کند.
- **تونل‌سازی انتخابی (Split tunneling)**: فقط برنامه‌های انتخاب‌شده یا همه به‌جز آن‌ها.
- **موتورهای TUN**: sing-box (پیش‌فرض، پشته‌ی gVisor) با بازگشت خودکار به hev-socks5-tunnel؛ موتور را می‌توان از تنظیمات عوض کرد.
- **اطلاعات زنده**: پینگ، سرعت دانلود و آپلود، سرعت لینک، IP خروجی و لاگ‌ها.
- کاشی تنظیمات سریع، اجرا هنگام بوت، رنگ پویا (اندروید ۱۲+)، تم تیره و روشن.
- رابط فارسی و انگلیسی با پشتیبانی کامل از راست‌به‌چپ.

## تصاویر برنامه

<p align="center">
  <img src="screenshots/01-home.jpg" width="190" alt="صفحه‌ی اصلی">
  <img src="screenshots/02-connected.jpg" width="190" alt="متصل">
  <img src="screenshots/03-psiphon-region.jpg" width="190" alt="منطقه‌ی Psiphon">
  <img src="screenshots/04-settings.jpg" width="190" alt="تنظیمات">
  <img src="screenshots/05-light-theme.jpg" width="190" alt="تم روشن">
</p>

## نحوه‌ی کار

</div>

```
apps -> VpnService (TUN) -> sing-box (libbox, gVisor) -> SOCKS5 127.0.0.1:1819 -> Aether / Psiphon / Tor -> internet
```

<div dir="rtl" align="right">

DNS را خود sing-box (روی TCP از طریق پروکسی) جواب می‌دهد. برای Psiphon و Tor، UDP غیر از DNS فوراً رد می‌شود تا برنامه‌ها از QUIC به TCP برگردند.
اگر کتابخانه نباشد یا اجرا نشود، برنامه خودکار به hev-socks5-tunnel برمی‌گردد.

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
| **sing-box** (SagerNet) | موتور پیش‌فرض TUN به SOCKS5 (`libbox`، پشته‌ی gVisor) | <https://github.com/SagerNet/sing-box> | GPL-3.0 (جزئیات در مخزن اصلی) |
| **hev-socks5-tunnel** | موتور جایگزین TUN به SOCKS5 | <https://github.com/heiher/hev-socks5-tunnel> | MIT |

> مجوز دقیق و فعلی هر پروژه را در مخزن اصلی خودش بررسی کنید.

## نکته‌ها

- Aether مجوز AGPL-3.0 دارد، پس این پروژه هم AGPL-3.0 است (فایل [LICENSE](LICENSE)).
- سیاست علامت تجاری Aether از محصولات مشتق می‌خواهد نام و برند خودشان را داشته باشند؛ برای همین برنامه **8VPN** نام دارد و از هسته فقط به‌عنوان منبع یاد می‌شود.
- فهرست مناطق Psiphon ثابت است و مجموعه‌ی واقعی به سرورهای Psiphon بستگی دارد.

</div>
