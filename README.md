# BT mouse

A fast, low-latency Android Bluetooth HID wireless mouse, touchpad, scroll wheel, and keyboard controller designed for **Smart TVs** and **Windows Laptops**.

## Features
- **Composite Bluetooth HID Profile**: Works out-of-the-box as a standard wireless Mouse, Keyboard, and Media/TV Consumer Remote without installing any extra server software on your Smart TV or Windows PC.
- **Precision Touchpad & Gestures**:
  - 1-finger drag: Accelerated cursor movement with sub-pixel coalescing
  - 1-finger tap: Left click
  - Double-tap & hold: Drag-and-drop / text selection lock
  - 2-finger tap: Right click
  - 2-finger drag: Vertical & horizontal scrolling
  - 2-finger pinch: Zoom (`Ctrl + Scroll`)
- **Hardware Controls**: Dedicated Left, Middle, and Right mouse buttons (with hold-to-drag support) plus a notched vertical scroll wheel strip.
- **Keyboard & Smart TV Remote**: Real-time soft keyboard bridge, sticky modifiers (`Ctrl`, `Alt`, `Shift`, `Win`), Windows shortcuts (`Alt+Tab`, `Win+D`, `Ctrl+C/V/Z`), `F1–F12`, and a dedicated Smart TV D-Pad & Media deck.
- **Auto-Pairing & Auto-Reconnect**: One-tap discoverable mode and automatic reconnection to your last connected TV or laptop.
- **Optimized Lag-Free Protocol**: Frame-paced `60 / 125 / 250 Hz` HID packet coalescing with low-latency Bluetooth HID QoS tuning.
- **Dark Mode Support**: System, Dark, and Light themes persisted locally.

## Building APKs via GitHub Actions (`release.yml`)
This repository includes `.github/workflows/release.yml` configured for **manual execution only** (`Actions -> Build & Release APK -> Run workflow`), with a **Run unit tests before building APKs** checkbox so it never runs automatically on push. It builds both **Debug** and **Release** APKs and uploads them as workflow artifacts.

### Optional Release Signing Secrets
If no signing secrets are configured, the workflow generates a temporary keystore so your APK builds cleanly right away. For permanent Play Store / consistent release signing, add these repository secrets in **Settings -> Secrets and variables -> Actions**:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password for the `upload` alias
