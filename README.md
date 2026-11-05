# Nothing Glass

An LSPosed / Vector module that adds dark frosted glass to the notification shade, quick settings, notification cards, and volume panel on Nothing OS.

## Features

- Hardware background blur with adjustable tint and opacity.
- Styling for notification cards, pop-up notifications, and lock-screen surfaces.
- Translucent Essential notification sections with a white outline.
- Matching glass surfaces for volume controls.
- Preset colors, the Android Monet system palette, and a dark color generated from your wallpaper.
- Material Design 3 settings with collapsible sections.
- English by default, with an in-app Russian language option.
- Live configuration updates, a safe mode, and an emergency disable flag.

## Compatibility

Developed and tested on a **Nothing Phone (3a), A059 / Asteroids**, running **Nothing OS 5.0, Android 17**, build `Asteroids_C5.0-261023-2146`.

Root and an active LSPosed-compatible framework such as Vector are required. The module targets `com.android.systemui` and depends on firmware-specific internal classes. Compatibility with other devices or firmware versions has not been verified.

## Installation

1. Download the APK from [Releases](https://github.com/ShinysLove/NothingGlass/releases).
2. Install it, then enable Nothing Glass in LSPosed / Vector.
3. Select **System UI (`com.android.systemui`)** as the module scope.
4. Restart SystemUI or reboot the device.
5. Open Nothing Glass to adjust the effects. Most settings are applied live.

The initial downloadable APK is a **debug-signed build**. The signing key is not included in this repository. A build made on another computer may require uninstalling an existing installation if its signing certificate differs.

## Build

Requirements: **JDK 17** and **Android SDK Platform 34**. The Gradle wrapper is included.

Open the project in Android Studio, or set `ANDROID_HOME` / create an untracked `local.properties` pointing to your Android SDK, then run:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Recovery

To disable the module's hooks through ADB:

```bash
adb shell su -c 'touch /data/local/tmp/nothing_glass_disable'
adb shell su -c 'pkill -f com.android.systemui'
```

To remove the emergency flag after fixing a configuration:

```bash
adb shell su -c 'rm /data/local/tmp/nothing_glass_disable'
```

Disabling the module in LSPosed / Vector and restarting SystemUI is another recovery option.

## Project layout

- `app/src/main/java/com/nothing/glass/hooks/` — SystemUI hooks.
- `app/src/main/java/com/nothing/glass/config/` — configuration and live updates.
- `app/src/main/java/com/nothing/glass/ui/` — settings activity.
- `app/src/main/res/` — UI layouts, icons, theme, and translations.

## License

[MIT](LICENSE).
