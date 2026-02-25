# KrKey Android

This is an Android implementation of the KrKey keyboard.

## Building

To build the project, run:

```bash
./gradlew assembleDebug
```

(Note: On NixOS, you might need to use `gradle` directly or configure `local.properties` and `gradle.properties` as done in the setup).

## Installing

To install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Enabling

To enable the keyboard:

1. Go to Settings -> System -> Languages & input -> On-screen keyboard -> Manage on-screen keyboards.
2. Enable "KrKey Devanagari".
3. Switch to it by tapping the keyboard icon in the navigation bar when typing, or via Settings.

## Development

The main logic is in `app/src/main/java/com/akssri/krkey/KrKeyIME.java`.
The layout is in `app/src/main/res/layout/keyboard_view.xml`.
