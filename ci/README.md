# How the APK build works

The workflow lives at [`.github/workflows/build.yml`](../.github/workflows/build.yml)
([`ci/build-apk.yml`](build-apk.yml) is an identical fallback copy). On **every push — including
the merge of a PR into `main`** — it:

1. builds a signed `app-release.apk` (JDK 17 + Gradle),
2. uploads it as a workflow artifact named `shorts-apk` (Actions tab → run → Artifacts), and
3. publishes it to the **Shorts v1.1.0** GitHub release (tag pushes `v*` publish to their own
   release instead).

## One-time setup (repo owner)

### 1. Let the workflow publish releases

`Settings → Actions → General → Workflow permissions` → **Read and write permissions** → *Save*.

Without this the build still succeeds and the APK is still attached to the run as an artifact —
only the final "Publish to GitHub release" step fails (HTTP 403), because a workflow can never
grant itself more than this repository setting allows.

### 2. If the workflow file didn't make it into the repo

GitHub sometimes rejects workflow files pushed by automation accounts. If
`.github/workflows/build.yml` is missing from the repo, copy `ci/build-apk.yml` to that path
(e.g. via <https://github.com/Dragoisback/shorts/new/main?filename=.github/workflows/build.yml>) —
no other change is needed.

### 3. Stable signing — optional

Without repository secrets every CI build is signed with a fresh throwaway key, so Android
refuses to install a new APK over an older one (uninstall the old one first). For seamless
upgrades add these under `Settings → Secrets and variables → Actions`:

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

## Building locally instead

Open the project in Android Studio (JDK 17) and run `./gradlew assembleDebug`; the APK lands in
`app/build/outputs/apk/debug/app-debug.apk`.
