<p align="center"><img src="docs/images/dauxio.png" width="300"></p>
<h1 align="center"><b>Dauxio</b></h1>
<h4 align="center">A simple, rational music player for Android.</h4>
<p align="center">
    <a href="https://www.gnu.org/licenses/gpl-3.0">
        <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
    </a>
    <img alt="Minimum SDK Version" src="https://img.shields.io/badge/API-24%2B-1450A8?style=flat">
</p>
<h4 align="center"><a href="/CHANGELOG.md">Changelog</a></h4>

> [!NOTE]
> **Dauxio** is a fork of the excellent [Auxio](https://github.com/OxygenCobalt/Auxio) music player by Alexander Capehart (OxygenCobalt). Huge gratitude to the original creators and contributors for building such a robust foundation. Dauxio builds upon this work with customized workflows and enhancements.

## About

Dauxio is a local music player with a fast, reliable UI/UX without unnecessary bloat present in other music players. Built on modern media playback libraries, Dauxio offers reliable library support and listening quality compared to apps relying on outdated Android functionality. In short, **It plays music.**

## Screenshots

<p align="center">
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot0.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot1.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot2.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot3.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot4.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot5.png" width=250>
</p>

## Features

- Playback based on [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/exoplayer)
- Snappy UI derived from the latest Material Design guidelines
- Opinionated UX that prioritizes ease of use over edge cases
- Customizable behavior
- Support for disc numbers, multiple artists, release types, precise/original dates, sort tags, and more
- Advanced artist system that unifies artists and album artists
- SD Card-aware folder management
- Reliable playlisting functionality
- Playback state persistence
- Android Auto support
- Automatic gapless playback
- Full ReplayGain support (On MP3, FLAC, OGG, OPUS, and MP4 files)
- External equalizer support (ex. Wavelet)
- Edge-to-edge
- Embedded covers support
- Search functionality
- Headset autoplay
- Stylish widgets that automatically adapt to their size
- Completely private and offline
- No rounded album covers (if you want them)

## Permissions

- Storage (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) to read and play your music files
- Services (`FOREGROUND_SERVICE`, `WAKE_LOCK`) to keep the music playing in the background
- Notifications (`POST_NOTIFICATION`) to indicate ongoing playback and music loading

## Building

Dauxio relies on a patched version of Media3 that enables extra playback features, alongside taglib for metadata parsing.
1. `cmake` and `ninja-build` must be installed before building the project.
2. The project uses submodules, so when cloning initially, use `git clone --recurse-submodules` to properly download external dependencies.
3. Build requires a Unix-based environment (Linux/macOS) for the Media3 build scripts.

### Set up Android Studio

#### Install Android Studio

```bash
pkg -S android-studio
```

#### Configuring Android Studio

- Ensure NDK version 28.2.13676358 is installed under **Languages & Frameworks > Android SDK**.
- Install Java 21 with your system package manager:
    ```bash
    sudo pkg -S jdk21-openjdk
    ```
    Set Java version to `jdk21-openjdk`.
- Run `./gradlew assembleDebug`

#### Connecting to your Android Device

You can connect your phone via USB to run the app:

1. **Enable Developer Options on your phone**:
   - Go to **Settings > About phone**
   - Tap **Build number** 7 times until you see *"You are now a developer!"*
2. **Enable USB debugging**:
   - Go to **Settings > Developer options**
   - Turn on **USB debugging**
3. **Connect phone to computer**:
   - Use a USB cable and accept the *Allow USB debugging?* prompt.
4. **Verify device detection**:
   ```bash
   adb devices
   ```

#### Install on Device

```bash
./gradlew installDebug
```

#### Push Test Music (Optional)

```bash
adb push ~/Music/ /sdcard/Music
```

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Dauxio is Free Software: You can use, study, share, and improve it. Specifically, you can redistribute and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl.html) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

