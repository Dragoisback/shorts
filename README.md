# Shorts

A tiny Android app that turns any folder on your phone into a YouTube-Shorts-style feed:
pick a folder once, then swipe up and down through every video inside it.

## Features

- **Folder as library** – choose any folder (internal storage, SD card, USB/OTG) with the system
  folder picker; the app scans it and all its sub-folders for videos.
- **Vertical swipe feed** – full-screen `ViewPager2` + ExoPlayer (Media3), one video per page,
  exactly like Shorts/TikTok.
- **Loops automatically**, tap to pause/resume, mute toggle, position counter.
- **Remembers your folder** across launches (persisted URI permission), no storage permission
  prompts and no file copying.
- Plays mp4, mkv, webm, mov, 3gp, ts and anything else ExoPlayer can decode; files it can't
  decode (e.g. AVI/FLV) are skipped automatically instead of freezing the feed.
- Built for modern Pixels: targets **Android 16 (API 36)** — the OS the Pixel 10a ships with —
  with proper edge-to-edge layout (status bar, gesture bar and punch-hole cutout handled),
  screen stays awake while watching, audio focus and headphone-unplug handling.
- Runs on Android 6.0 (API 23) and up.

## Install

Download the latest `shorts-*.apk` from the
[Releases page](https://github.com/Dragoisback/shorts/releases), copy it to your phone, and
install it (you'll need to allow "install unknown apps" for your browser/file manager).

Then: open **Shorts** → *Choose video folder* → select your videos folder → swipe.

## APK releases

`.github/workflows/build.yml` builds a signed release APK on every push (including PR merges)
and publishes it to the [v1.1.0 release](https://github.com/Dragoisback/shorts/releases/tag/v1.1.0).
One-time setup and optional stable-signing secrets are described in
[`ci/README.md`](ci/README.md).

## Build it yourself

Requires JDK 17 and the Android SDK (or just open the project in Android Studio).

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed if SHORTS_KEYSTORE* env vars are set
```

### Signing

The workflow signs with a throwaway keystore unless you add the repository secrets listed in
[`ci/README.md`](ci/README.md). Without them each release is signed with a different key, so
uninstall the old version before installing a newer one.

## Project layout

```
app/src/main/java/com/dragoisback/shorts/
  MainActivity.kt    – folder picking, ViewPager2 feed, shared ExoPlayer instance
  ShortsAdapter.kt   – one full-screen page per video
  VideoScanner.kt    – recursive scan of the chosen document tree
```
