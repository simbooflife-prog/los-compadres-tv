# Los Compadres TV

Android TV / Fire Stick app for **Los Compadres** restaurant displays.

- **Package:** `com.loscompadres.tv`
- **Customer home (default):** fullscreen WebView slideshow
- **Staff cameras:** hidden behind long-press **Menu** + PIN (no visible tab)

## Pages

| Page | Who | URL source |
|------|-----|------------|
| 1 — Slideshow (launch) | Customers | `url_home` in `app/src/main/res/values/urls.xml` |
| 2 — Cameras | Staff only | `url_cameras` (or LAN `url_cameras_lan`) |

Default PIN: **`1234`** (change in `urls.xml` → `staff_pin` before production).

**Staff path:** long-press the remote **Menu** key (~0.7s) → enter PIN → cameras WebView.  
**Back** from cameras returns to the slideshow. Short Menu press does nothing useful for customers.

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
<string name="url_home">https://…/local/compadres-tv/index.html</string>
<string name="url_cameras">https://…/lovelace/cameras</string>
<string name="url_cameras_lan">http://192.168.1.27:8123/lovelace/cameras</string>
<string name="staff_pin">1234</string>
<bool name="prefer_cameras_lan">false</bool>
```

- Set `prefer_cameras_lan` to `true` when the Stick is on the same LAN as Home Assistant and you want the local URL.
- **Do not put tokens in URLs.** Log in once inside the WebView; cookies persist.

## Cookie / login notes

The app enables:

- JavaScript + DOM storage
- `CookieManager.setAcceptCookie(true)`
- third-party cookies for the WebView
- `CookieManager.flush()` on page finish / pause

After a Home Assistant or Nabu Casa login in the WebView, the session cookie should stick across app restarts on the same device. If login is lost, open staff cameras (or home), sign in again, and leave the app running briefly so cookies flush to disk.

Cleartext HTTP is allowed for the LAN fallback (`usesCleartextTraffic`).

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
