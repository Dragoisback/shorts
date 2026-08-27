# Enabling the APK build workflow

The automation account that created this project is not allowed to push files into
`.github/workflows/`, so the build workflow lives here at `ci/build-apk.yml`.

Enable it once (takes ~30 seconds) and every push will build a signed APK and attach it to the
GitHub release:

**Option A – GitHub web UI**

1. Open <https://github.com/Dragoisback/shorts/new/arena/01a042dd-shorts?filename=.github/workflows/build.yml>
2. Paste the contents of [`ci/build-apk.yml`](build-apk.yml).
3. Commit directly to the `arena/01a042dd-shorts` branch.

**Option B – from your machine**

```bash
git clone https://github.com/Dragoisback/shorts.git
cd shorts
git checkout arena/01a042dd-shorts
mkdir -p .github/workflows
cp ci/build-apk.yml .github/workflows/build.yml
git add .github/workflows/build.yml
git commit -m "Enable APK build workflow"
git push
```

Then watch the **Actions** tab. When the run finishes, `shorts-v1.0.0.apk` appears on the
[Releases page](https://github.com/Dragoisback/shorts/releases/tag/v1.0.0).

**Option C – build locally instead**

Open the project in Android Studio (JDK 17) and run `./gradlew assembleDebug`; the APK lands in
`app/build/outputs/apk/debug/app-debug.apk`.
