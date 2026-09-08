# Travel Benefits

A personal, sideload-only Android app for tracking your credit cards' rewards
and benefits, getting a "which card should I use" recommendation by spending
category, and pulling hotel loyalty membership numbers/status/points out of
your Gmail.

Everything runs and stores data locally on your phone. The only network
calls this app makes are:

1. To **api.anthropic.com** - to look up benefits for a card that isn't in
   the built-in catalog, and to read loyalty details out of scanned emails.
2. To **Google's OAuth and Gmail APIs** - only after you explicitly connect
   Gmail, using a **read-only** scope (`gmail.readonly`). The app never
   sends, deletes, labels, or modifies anything in your inbox.

No backend, no account system, no analytics/tracking SDKs.

## Important limitations - read this first

- **This project was written without a working Android build environment.**
  I don't have the Android SDK available in the sandbox I built this in (and
  the host that distributes it, `dl.google.com`, was blocked by that
  sandbox's network policy), so I could not compile or run this app to
  verify it. The code follows standard, well-documented patterns (Jetpack
  Compose, Room, Hilt, Retrofit, AppAuth) as carefully as I could, but
  **you should expect to fix at least a few small build errors** the first
  time you open it in Android Studio - version mismatches between Kotlin/
  Compose-compiler/KSP-style plugins are the most likely culprit. Android
  Studio's "Upgrade" quick-fixes for the Gradle/AGP/Kotlin versions in
  `gradle/libs.versions.toml` are the fastest way through those.
- **Card benefit data is a hand-curated snapshot, not a live feed.** Annual
  fees, reward categories, and credits shown for catalog cards reflect
  public information as understood in early-to-mid 2025 and *will* drift out
  of date - the app surfaces this warning on the dashboard, but always
  verify anything that matters (fees, credits, elite status rules) against
  the issuer's own site before acting on it.
- **Point valuations used to rank cards are estimates, not guarantees.**
  Comparing a cash-back card to a transferable-points card requires
  assuming a cents-per-point value (see `RewardCurrency` in
  `CardModels.kt`) - these are the kind of figures independent points
  trackers publish, not a redemption you're guaranteed to get.
- **The Gmail scan uses an LLM to read email text, not fixed regex rules**,
  because loyalty program emails have no consistent format. It can miss
  things or occasionally misread a number - always double check anything
  it finds before relying on it (e.g. before a trip).

## Architecture

- **UI**: Jetpack Compose (Material 3), single-activity, Navigation Compose
  with a bottom nav bar (Dashboard / Wallet / Best Card / Hotels / Settings).
- **State**: MVVM - one `ViewModel` per screen, `StateFlow` for UI state.
- **DI**: Hilt.
- **Local storage**: Room (wallet cards, loyalty accounts, cached card
  lookups) + `EncryptedSharedPreferences` (Anthropic API key, Google OAuth
  client ID, Gmail OAuth tokens) backed by the Android Keystore.
- **Networking**: Retrofit + OkHttp + kotlinx.serialization, talking
  directly to the Anthropic Messages API and the Gmail REST API over plain
  HTTPS (see "Why raw HTTP instead of an SDK" below).
- **Gmail sign-in**: [AppAuth-Android](https://github.com/openid/AppAuth-Android)
  (OAuth2 + PKCE), not Google Sign-In/Play Services - this keeps the app
  working on sideloaded installs on devices without Google Play Services
  (custom ROMs, de-Googled phones, etc.), which is the whole point of
  "allow sideloading."

### Why raw HTTP instead of an SDK for Anthropic

The official Anthropic Java SDK isn't published with Android's constraints
in mind (dependency footprint, JVM assumptions) and hasn't been verified
here for Android compatibility. For the two calls this app makes - a
web-search benefit lookup and a JSON extraction pass - a couple of Retrofit
DTOs are simpler and lighter than pulling in a general-purpose JVM SDK. If
you'd rather use the official SDK, `data/remote/anthropic/` is a small,
self-contained package you can swap out.

## Building it

1. Install [Android Studio](https://developer.android.com/studio) (which
   bundles the Android SDK) - this repo doesn't include one.
2. Open the project root in Android Studio and let it sync. It will likely
   offer to upgrade the Android Gradle Plugin / Kotlin version - accepting
   those suggestions is fine.
3. Run the `app` configuration on a device/emulator, **or** build an APK
   from the command line:

   ```
   ./gradlew assembleDebug
   ```

   The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Sideloading it (no Play Store)

The debug build is signed with your machine's auto-generated debug
keystore, which is enough to install directly:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to your phone and open it with a file manager - Android
will prompt you to allow installs from that app ("Install unknown apps")
the first time.

For a build that keeps a stable signature across reinstalls/updates (so
Android doesn't treat every reinstall as a brand-new app), generate your
own keystore and wire it up as a `release` signing config in
`app/build.gradle.kts`:

```
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias travelbenefits
```

then add a `signingConfigs { create("release") { ... } }` block pointing at
it and reference it from `buildTypes { release { signingConfig = ... } } }`,
and build with `./gradlew assembleRelease`.

## Setting up Gmail access (Google Cloud OAuth)

Gmail's API requires you to register your own OAuth client - there's no way
around this for a personal/sideloaded app, and it's free.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and
   create a new project (or reuse one).
2. **Enable the Gmail API**: APIs & Services → Library → search "Gmail API"
   → Enable.
3. **Configure the OAuth consent screen**: APIs & Services → OAuth consent
   screen.
   - User type: **External**.
   - Fill in the required fields (app name, your email).
   - Scopes: add `.../auth/gmail.readonly`.
   - **Test users**: add your own Gmail address here.
   - Leave publishing status as **Testing**. For a personal app used only by
     you (and up to 99 other test users you add), Google does not require
     an app-verification review while it stays in Testing - that review
     process is only needed to move to Production for public/unverified
     use.
4. **Create credentials**: APIs & Services → Credentials → Create
   Credentials → OAuth client ID → Application type **Android**.
   - Package name: `com.travelbenefits.app.debug` for a debug build (note
     the `.debug` suffix from `applicationIdSuffix` in `app/build.gradle.kts`),
     or `com.travelbenefits.app` if you build a release variant without
     that suffix.
   - SHA-1 certificate fingerprint: for a debug build, get it from your
     machine's debug keystore:

     ```
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

     Copy the `SHA1:` value. For a release build, run the same command
     against your own release keystore instead.
   - Save, then copy the generated **Client ID** (ends in
     `.apps.googleusercontent.com`).
5. **Set the redirect scheme and rebuild.** The OAuth redirect has to reach
   this exact app, which Android does via a custom URI scheme baked into
   the manifest at build time - it can't be read from something you type in
   at runtime. Take the numeric/alphanumeric prefix of your Client ID (the
   part before `.apps.googleusercontent.com`) and reverse the domain:

   ```
   Client ID:        123456789-abc123.apps.googleusercontent.com
   Redirect scheme:  com.googleusercontent.apps.123456789-abc123
   ```

   Then rebuild passing that as a Gradle property (or add
   `appAuthRedirectScheme=com.googleusercontent.apps.123456789-abc123` as a
   line in `gradle.properties` so you don't have to repeat it):

   ```
   ./gradlew assembleDebug -PappAuthRedirectScheme=com.googleusercontent.apps.123456789-abc123
   ```

   and install that APK.
6. In the app, go to **Settings** and paste the full Client ID into "Google
   OAuth client ID", then tap **Connect Gmail**.

If you ever rebuild with a different signing key (e.g. switch from debug to
your own release keystore) or a different OAuth client, you'll need a new
Android OAuth client registration (new SHA-1) and, if the client ID's prefix
changes, a rebuild with the matching `appAuthRedirectScheme`.

## Setting up the Anthropic API key

1. Get an API key from [console.anthropic.com](https://console.anthropic.com).
2. In the app, go to **Settings** and paste it into "Anthropic API key".

This key is stored encrypted on-device only (Android Keystore-backed) and
is sent solely to `api.anthropic.com` as the `x-api-key` header.

## Project layout

```
app/src/main/java/com/travelbenefits/app/
  auth/            AppAuth-based Gmail OAuth (PKCE)
  data/
    catalog/       Hand-curated credit card + hotel program data
    local/         Room database, DAOs, entities, EncryptedSharedPreferences
    remote/        Retrofit clients for Anthropic + Gmail
    repository/    Wallet, loyalty, card-lookup, Gmail-scan repositories
  di/              Hilt modules
  domain/          Domain models + the recommendation engine
  ui/              Compose screens, one package per screen, + navigation/theme
```
