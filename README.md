# Los Compadres TV

Android TV / Fire Stick app for **Los Compadres** restaurant displays.

- **Package:** `com.loscompadres.tv` (version **1.2.0**)
- **Customer home (default):** fullscreen WebView slideshow over `/local` (LAN primary)
- **Staff cameras:** hidden behind long-press **Menu** + PIN — **no HA login / no Lovelace**; snapshot kiosk under `/local/compadres-cameras/`

## Pages

| Page | Who | URL source |
|------|-----|------------|
| 1 — Slideshow (launch) | Customers | `url_home` (LAN) / `url_home_nabu` backup |
| 2 — Cameras ≤4 | Staff only | `url_cameras` / `url_cameras_nabu` (`/local/…/index.html`) |
| 2b — All 5 cams | Staff toggle | `url_cameras_all` / `url_cameras_all_nabu` (`/local/…/all.html`) |

Default PIN: **`0909`** (change in `urls.xml` → `staff_pin` before production).

**Staff path:** long-press the remote **Menu** key (~0.7s) → enter PIN → `/local` cameras WebView (no Lovelace) with a slim staff bar:
- **Refresh** — reloads the current WebView page
- **View: Standard | All 5** — Standard = ≤4 cams (`index.html`); All 5 = `all.html` (choice remembered as `cameras_all_five`)
- **Back to slideshow** — returns to customer home

LAN is primary for both home and cameras (`prefer_home_lan` / `prefer_cameras_lan` = true) because the Stick stays on restaurant Wi‑Fi. Nabu Casa URLs are backup only.

Customer slideshow stays fullscreen with **no** persistent refresh chrome. Short Menu on slideshow does nothing; wrong/cancel PIN keeps the UI clean. **Back** from cameras returns to the slideshow.

## Update on Fire Stick (same package)

Install over the previous build (same `com.loscompadres.tv` package) with Downloader or ADB — no uninstall required:

```bash
adb install -r LosCompadresTV-debug.apk
```

Or open the GitHub release asset URL in the **Downloader** app on the Stick.

## Requirements

- Android Studio Ladybug+ (or recent) with Android SDK 35
- JDK 17
- minSdk 25 (Fire Stick / Android TV era devices)
- target/compileSdk 35

## Build

### Android Studio

1. **File → Open** → select this folder (`los-compadres-tv`).
2. Let Gradle sync (wrapper will download if needed).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**  
   or run **Run** on a TV emulator / device.

### Command line

```bash
./gradlew assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release:

```bash
./gradlew assembleRelease
```

## Sideload to Fire Stick (ADB)

1. On the Fire Stick: **Settings → My Fire TV → Developer Options**  
   - Enable **ADB debugging** and **Apps from Unknown Sources** (as needed).
2. Note the Stick’s IP (**Settings → Network**).
3. From your computer:

```bash
adb connect FIRESTICK_IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. Launch **Los Compadres TV** from the Apps row (Leanback launcher).

If `adb devices` shows `unauthorized`, accept the prompt on the TV.

## Configure URLs and PIN

Edit `app/src/main/res/values/urls.xml`:

```xml
<!-- Customer home — LAN primary -->
<string name="url_home">http://192.168.1.27:8123/local/compadres-tv/index.html</string>
<string name="url_home_nabu">https://…/local/compadres-tv/index.html</string>

<!-- Staff cameras ≤4 snapshot kiosk (no Lovelace / no HA login) -->
<string name="url_cameras">http://192.168.1.27:8123/local/compadres-cameras/index.html</string>
<string name="url_cameras_nabu">https://…/local/compadres-cameras/index.html</string>

<!-- All 5 cams -->
<string name="url_cameras_all">http://192.168.1.27:8123/local/compadres-cameras/all.html</string>
<string name="url_cameras_all_nabu">https://…/local/compadres-cameras/all.html</string>

<string name="staff_pin">0909</string>
<bool name="prefer_cameras_lan">true</bool>
<bool name="prefer_home_lan">true</bool>
```

- Stick is always on restaurant Wi‑Fi → keep both `prefer_*_lan` true.
- **Do not put HA tokens in the app.** Cameras use public `/local` kiosk pages (no login).

## Cookie notes

Cookies remain enabled for any optional auth pages, but the staff cameras path is intentionally **login-free** (`/local` HTML). Cleartext HTTP is allowed for LAN (`usesCleartextTraffic`).

## Project layout

```text
los-compadres-tv/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/loscompadres/tv/MainActivity.kt
│       └── res/values/{urls,strings,themes,colors}.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## License / private use

Private restaurant display app for Los Compadres. Not for store listing unless icons/branding are finalized.
