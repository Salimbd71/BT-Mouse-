# BT mouse

A fast, low-latency Android wireless mouse, touchpad, scroll wheel, and keyboard controller supporting **Bluetooth HID** (for Smart TVs & Windows Laptops) and **Wi-Fi Direct UDP** (for Windows PCs via a directly installable `.exe` receiver).

## Features
- **Dual Mode (Bluetooth HID + Wi-Fi PC)**:
  - **Bluetooth HID Mode**: Works out-of-the-box with Smart TVs and supported PCs without extra software.
  - **Wi-Fi Mode (Windows PC)**: Ultra-low-latency local Wi-Fi UDP protocol (port `51920` / auto-discovery `51921`) with auto-discovery, manual IP fallback, full Unicode typing (including Bangla), and atomic `Ctrl + Wheel` pinch-zoom.
- **Precision Touchpad & Gestures**:
  - 1-finger drag: Accelerated cursor movement with sub-pixel coalescing
  - 1-finger tap: Left click
  - Double-tap & hold: Drag-and-drop / text selection lock
  - 2-finger tap: Right click
  - 2-finger drag: Vertical & horizontal scrolling
  - 2-finger pinch: Zoom (`Ctrl + Scroll`)
- **Hardware Controls**: Dedicated Left, Middle, and Right mouse buttons (with hold-to-drag support) plus a notched vertical scroll wheel strip.
- **Keyboard & Smart TV Remote**: Real-time soft keyboard bridge, sticky modifiers (`Ctrl`, `Alt`, `Shift`, `Win`), Windows shortcuts (`Alt+Tab`, `Win+D`, `Ctrl+C/V/Z`), `F1–F12`, and a dedicated Smart TV D-Pad & Media deck.
- **Optimized Lag-Free Protocol**: Frame-paced `60 / 125 / 250 Hz` packet coalescing.
- **Dark Mode Support**: System, Dark, and Light themes persisted locally.

## Windows Wi-Fi Receiver (`.exe`)
The companion receiver lives in `windows-receiver/` (.NET 8 WinForms, single-file self-contained `BTMouseReceiver.exe` + Inno Setup installer `BTMouseReceiver-Setup.exe`).
- Running **`BTMouseReceiver-Setup.exe`** installs the receiver, optionally registers startup in the system tray, and automatically adds the Windows Firewall allow rule for UDP ports `51920` and `51921`.
- Connect your phone and PC to the same Wi-Fi (or phone hotspot), switch **BT mouse** to **Wi-Fi** in the top bar, and tap your PC name.

## Building & Releasing APKs and Windows `.exe` via GitHub Actions (`release.yml`)
This repository includes `.github/workflows/release.yml` configured for **manual execution only** (`Actions -> Build & Release APK & Windows EXE -> Run workflow`), with:
- **App version (`app_version`)** input box (default `1.0`) — automatically updates Android `versionName`/`versionCode`, Windows Receiver version, and `Settings -> About`.
- **Run unit tests before building APKs** (`run_unit_tests`, default `true`)
- **Build Windows Wi-Fi Receiver (.exe installer & portable)** (`build_windows_receiver`, default `true`)

At the end of the workflow run, signed APKs and Windows `.exe` builds are uploaded as workflow artifacts **and automatically published directly to a GitHub Release (`v<version>`)**:
- `BT-mouse-v<version>-release.apk` & `BT-mouse-v<version>-debug.apk`
- `BTMouseReceiver-Setup-v<version>.exe` (direct Windows installer)
- `BTMouseReceiver-v<version>.exe` (standalone portable executable)

### Release Signing Secrets
The workflow uses repository secrets in **Settings -> Secrets and variables -> Actions** to sign the release APK automatically:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password for the `upload` alias


