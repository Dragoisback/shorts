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
- Supports mp4, mkv, webm, mov, 3gp, avi, ts and anything else ExoPlayer can decode.

## Install

Download the latest `shorts-*.apk` from the
[Releases page](https://github.com/Dragoisback/shorts/releases), copy it to your phone, and
install it (you'll need to allow "install unknown apps" for your browser/file manager).

Then: open **Shorts** → *Choose video folder* → select your videos folder → swipe.

## Build it yourself

Requires JDK 17 and the Android SDK (or just open the project in Android Studio).

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed if SHORTS_KEYSTORE* env vars are set
```

CI lives in [`ci/build-apk.yml`](ci/build-apk.yml). Copy it to `.github/workflows/build.yml`
(see [`ci/README.md`](ci/README.md) — GitHub blocks automation accounts from writing workflow
files) and every push will build a signed release APK and attach it to the
[v1.0.0 release](https://github.com/Dragoisback/shorts/releases/tag/v1.0.0).

### Signing

The workflow signs with a throwaway keystore unless you add these repository secrets:

| Secret | Meaning |
| --- | --- |
| `SHORTS_KEYSTORE_BASE64` | base64 of your `.jks` keystore |
| `SHORTS_KEYSTORE_PASSWORD` | keystore password |
| `SHORTS_KEY_ALIAS` | key alias |
| `SHORTS_KEY_PASSWORD` | key password |

Without them each release is signed with a different key, so uninstall the old version before
installing a newer one.

## Project layout

```
app/src/main/java/com/dragoisback/shorts/
  MainActivity.kt    – folder picking, ViewPager2 feed, shared ExoPlayer instance
  ShortsAdapter.kt   – one full-screen page per video
  VideoScanner.kt    – recursive scan of the chosen document tree
```
