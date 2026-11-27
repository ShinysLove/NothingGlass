# Nothing Glass

Nothing Glass is a Vector / LSPosed module for Nothing OS 5. Version 2.0 is a complete rewrite of the project around the separate Notifications and Quick Settings panels in SystemUI. It extends the lock-screen rendering from NothingBlurShortcuts to the rest of the shade.

[Download the latest APK](https://github.com/ShinysLove/NothingGlass/releases/latest)

## Features

- Hardware background blur for both panels, with translucency and background scaling maintained at full expansion.
- Frosted notification cards, including grouped notifications, and translucent Quick Settings tiles.
- A soft blur along the moving lower edge of Quick Settings. Tiles become sharp as they clear the edge and soften as the panel closes.
- Matching glass surfaces for notification history, notification settings, Clear all, the Quick Settings edit button, and the active-apps chip.
- A compact VPN indicator that shows the key by itself. The expanded indicator has a translucent pill and a scrolling label with softly faded edges.
- Frosted lock-screen shortcuts and notifications, heads-up alerts, and volume controls inherited from NothingBlurShortcuts.

## Compatibility

The module targets **Nothing OS 5.0 (Android 17)**. Development and device testing used the **Nothing Phone (3a), A059 / Asteroids**, with firmware build **2611061007**. Other models and firmware versions are unverified.

You need root, a compatible Zygisk implementation, and Vector or an LSPosed build with Android 17 support. The panel effects use Android's cross-window blur support.

## Installation

1. Download and install `NothingGlass-2.0.0.apk` from GitHub Releases.
2. In Vector or LSPosed, enable **Nothing Glass** and select **System UI** (`com.android.systemui`).
3. Disable Nothing Glass 1.x, NothingBlurShortcuts, or any other module that applies conflicting SystemUI blur effects.
4. Reboot your phone.

Version 2.0 uses the package `io.github.nothingblurpanels`. It installs alongside Nothing Glass 1.x and updates compatible Nothing Blur Panels builds signed with the same key. The release APK is debug-signed. Local builds use a locally generated signing key, so their signatures can differ from the release.

To roll back, disable the module in Vector or LSPosed and reboot.

## Build from source

Install **JDK 17 or newer**, **Android SDK Platform 37.0**, and **Android SDK Build Tools 36.0.0**.

On Windows PowerShell:

```powershell
./build.ps1
```

On Linux or macOS, set `ANDROID_HOME` to your Android SDK path, install `zip`, and run:

```sh
./build.sh
```

Both scripts produce `bin/NothingGlass.apk`. Build output and the local signing key are kept out of Git.

## Credits

- **Ak1o-T** created [NothingBlurShortcuts](https://github.com/Ak1o-T/NothingBlurShortcuts), the open-source project that provides the foundation for this rewrite, including the lock-screen, heads-up notification, and volume rendering.
- **ShinysLove** maintains Nothing Glass and developed the split-shade integration and control styling.

The 2.x source is licensed under the [Apache License 2.0](LICENSE). The historical Nothing Glass 1.x source retains its [MIT license](LICENSE-LEGACY).
