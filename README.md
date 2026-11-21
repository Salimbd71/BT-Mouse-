# BT mouse

A fast, low-latency Android wireless mouse, touchpad, scroll wheel, and keyboard controller supporting **Bluetooth HID** (for Smart TVs & Windows Laptops) and **Wi-Fi Direct UDP** (for Windows PCs via `BTMouseReceiver-Setup.exe`).

## Features
- **Dual Mode (Bluetooth HID + Wi-Fi PC)**:
  - **Bluetooth HID Mode**: Works out-of-the-box with Smart TVs and supported PCs without extra software.
  - **Wi-Fi Mode (Windows PC)**: Ultra-low-latency local Wi-Fi UDP protocol (port `51920` / auto-discovery `51921`) with auto-discovery, manual IP fallback, full Unicode typing (including Bangla), and atomic `Ctrl + Wheel` pinch-zoom.
  - **Background Wi-Fi File Transfer (TCP `51922`)**: Send any file (photos, videos, APKs, PDFs, ZIPs) from Phone → PC (`Downloads\BT mouse Files`) and from PC → Phone (`Downloads/BT mouse`). Runs in an Android foreground service so transfers continue even if the app is closed, with a **Cancel** button both in-app and in the ongoing notification.
- **Splash Permission Flow**: Prompts users cleanly at launch to allow Bluetooth, Background Transfer Notification, and Downloads storage permissions.
- **Built-in Latest `.exe` Download Link**: Devices & File Transfer sheets display a copyable and shareable download link for the latest `BTMouseReceiver-Setup.exe`.
- **Precision Touchpad & Gestures**: 1-finger move/tap click, double-tap drag lock, 2-finger right-click/scroll, and 2-finger pinch zoom (`Ctrl + Scroll`).
- **Keyboard & Smart TV Remote**: Real-time soft keyboard bridge, sticky modifiers (`Ctrl`, `Alt`, `Shift`, `Win`), Windows shortcuts (`Alt+Tab`, `Win+D`, `Ctrl+C/V/Z`), `F1–F12`, and a dedicated Smart TV D-Pad & Media deck.
- **Source Package**: Organized under `com.slm` (`app/src/*/java/com/slm`).

## Windows Wi-Fi Receiver (`.exe`)
The companion receiver lives in `windows-receiver/` (.NET 8 WinForms bundled into Inno Setup installer **`BTMouseReceiver-Setup.exe`**).
- Running **`BTMouseReceiver-Setup.exe`** installs the receiver, optionally registers startup in the system tray, and automatically adds the Windows Firewall allow rule for ports `51920`, `51921`, and `51922`.
- Connect your phone and PC to the same Wi-Fi (or phone hotspot), switch **BT mouse** to **Wi-Fi** in the top bar, and tap your PC name.

## Building & Releasing via GitHub Actions (`release.yml`)
`.github/workflows/release.yml` runs on **manual trigger only** (`Actions -> Build & Release APK & Windows EXE -> Run workflow`), with separate inputs/checkboxes for every artifact:
- **App version (`app_version`)** input box (default `1.0`) — automatically updates Android `versionName`/`versionCode`, Windows Receiver version, and `Settings -> About`.
- **Run unit tests (`run_unit_tests`)** checkbox (default `true`)
- **Build Release APK (`build_release_apk`)** checkbox (default `true`)
- **Build Debug APK (`build_debug_apk`)** checkbox (default `false`)
- **Build Windows Setup Installer (`build_setup_exe`)** checkbox (default `true`)
- **Build Windows Portable Executable (`build_portable_exe`)** checkbox (default `false`)

All selected outputs are uploaded as workflow artifacts and **published directly to GitHub Releases (`v<version>`)**:
- `BT-mouse-v<version>-release.apk` (if `build_release_apk` checked)
- `BT-mouse-v<version>-debug.apk` (if `build_debug_apk` checked)
- `BTMouseReceiver-Setup.exe` (if `build_setup_exe` checked)
- `BTMouseReceiver-v<version>.exe` (if `build_portable_exe` checked)

### Release Signing Secrets
Configure in **Settings -> Secrets and variables -> Actions**:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password for the `upload` alias
