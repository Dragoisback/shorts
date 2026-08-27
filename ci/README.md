# Enabling the APK build workflow

The automation account that created this project is **not allowed to push files into
`.github/workflows/`**, so the build workflow lives here at `ci/build-apk.yml`.

## Pre-merge checklist (do these and the APK is published the moment you merge)

### 1. Turn on write permissions for Actions — required

`Settings → Actions → General → Workflow permissions` → select
**"Read and write permissions"** → *Save*.

Without this the build still succeeds, but the final "Publish to GitHub release" step fails with
`403` because the job token cannot create/upload release assets. (A workflow can never grant
itself more than this repository-level setting allows.)

While you're on that page, make sure `Actions permissions` is **"Allow all actions and reusable
workflows"** so `actions/checkout`, `actions/setup-java` and `gradle/actions/setup-gradle` can run.

### 2. Add the workflow file — required

The merge itself cannot create it (the file has to exist for GitHub to run it), so add it either
to the PR branch before merging — recommended, it builds the APK immediately *and* again on merge —
or straight to `main`.

**Option A – GitHub web UI (30 seconds)**

1. Open <https://github.com/Dragoisback/shorts/new/arena/01a042dd-shorts?filename=.github/workflows/build.yml>
2. Paste the contents of [`ci/build-apk.yml`](build-apk.yml).
3. Commit directly to the `arena/01a042dd-shorts` branch (it joins PR #1 automatically).

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

### 3. Signing secrets — optional

Without them each build is signed with a throwaway key, which means Android will refuse to install
a new APK over an older one (uninstall first). To keep upgrades working, add these repository
secrets under `Settings → Secrets and variables → Actions`:

| Secret | Meaning |
| --- | --- |
| `SHORTS_KEYSTORE_BASE64` | `base64 -w0 my-release.jks` |
| `SHORTS_KEYSTORE_PASSWORD` | keystore password |
| `SHORTS_KEY_ALIAS` | key alias |
| `SHORTS_KEY_PASSWORD` | key password |

Create a keystore with:

```bash
keytool -genkeypair -v -keystore my-release.jks -alias shorts \
  -keyalg RSA -keysize 2048 -validity 10000
```

## What happens on merge

`push` to any branch (including `main` after the merge) triggers the workflow, which:

1. builds `app-release.apk` with JDK 17 + Gradle,
2. uploads it as a workflow artifact (`shorts-apk`), and
3. creates the `v1.0.0` release if missing and uploads `shorts-v1.0.0.apk` to it with `--clobber`,
   so the [existing v1.0.0 release](https://github.com/Dragoisback/shorts/releases/tag/v1.0.0)
   simply gains the APK.

Want a fresh release per version instead? Bump `versionCode`/`versionName` in
`app/build.gradle.kts` and push a tag (`git tag v1.1.0 && git push origin v1.1.0`) — tag pushes
publish to a release named after the tag.

If the run fails, open the **Actions** tab and check the failing step; the first build is the one
most likely to surface a missing-dependency or resource error, and I can fix it from here.

## Option C – build locally instead

Open the project in Android Studio (JDK 17) and run `./gradlew assembleDebug`; the APK lands in
`app/build/outputs/apk/debug/app-debug.apk`.
