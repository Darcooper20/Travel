# Travel Benefits - loyalty wallet

A personal, sideload-only Android app in the spirit of Navan Edge / AwardWallet:
one place for your hotel and airline loyalty programs, the credit cards you
carry, and the trips you've booked - fed automatically by monitoring your
Gmail - with tools to squeeze the most out of your points.

## What it does

- **Loyalty wallet** - hotels, airlines, and shops & dining (Amazon gift-card
  balance, Starbucks, Target Circle, Walmart Cash, Costco, Uber Cash,
  DoorDash, Sephora, Ulta, CVS, Walgreens, Kroger fuel points, Best Buy,
  Chipotle, Dunkin', Panera, Rakuten): membership number, status, balance
  (points or dollars), estimated value, progress to the next tier, and when
  points expire if you go inactive. Balance history per program.
- **Credit cards** - the cards you carry *and* the rewards balance sitting on
  each (Ultimate Rewards, Membership Rewards, miles, cash back), pulled from
  issuer statement emails or typed in, valued alongside everything else and
  fed into the transfer-partner maths.
- **Email monitor** - reads Gmail (read-only) incrementally, in the
  background on a schedule you choose (WorkManager) or on demand, and uses
  Claude to turn loyalty statements, shop/dining rewards emails, card
  statements, booking confirmations and "your points are expiring" notices
  into structured records. Each source (hotels/airlines, shops, cards,
  bookings) can be switched off separately. New memberships, balance
  and status changes, new trips and expiry warnings land in an activity feed
  and, optionally, a notification.
- **Trips** - flights, hotels, cars and rail pulled from confirmation emails
  (or added by hand), with an "is this earning?" check: if a booking maps to
  a program you belong to but the confirmation doesn't show your member
  number, the app flags it before check-in.
- **Needs attention** - expiring points, "3 nights to Gold" nudges, upcoming
  trips, bookings missing a loyalty number, member numbers you haven't saved.
- **Maximize points**
  - *Earn*: best card for a spending category, either by overall estimated
    value or by "most points into program X" (co-brand cards directly, bank
    points at their transfer ratio), plus any elite status your cards grant.
  - *Redeem*: cash-vs-points calculator that accounts for the points and
    card rewards a paid booking would have earned.
  - *Transfer*: which of your cards' currencies move into a program, at what
    ratio, and whether that's a good use of them.
  - *Ask*: an AI advisor (Claude + web search) that takes your actual
    balances and cards and researches the best redemption for a trip you
    describe - and tells you where to confirm live availability, because no
    program exposes award inventory to third-party apps.
- **Cards** - the original wallet: a curated catalog of ~85 US cards, live
  lookups for anything else, and card-granted hotel/airline status.

Everything runs and stores data locally on your phone. The only network
calls this app makes are:

1. To **api.anthropic.com** - to read loyalty/trip details out of scanned
   emails, to look up cards not in the built-in catalog, and for the points
   advisor. Only balances, tiers and card names go to the advisor; never
   membership numbers or email text.
2. To **Google's OAuth and Gmail APIs** - only after you explicitly connect
   Gmail, using a **read-only** scope (`gmail.readonly`). The app never
   sends, deletes, labels, or modifies anything in your inbox.

No backend, no account system, no analytics/tracking SDKs.

## Getting a build without installing Android Studio

Every push to the branches listed in `.github/workflows/build-apk.yml` triggers
`.github/workflows/build-apk.yml`, which builds the debug APK on GitHub's
servers and publishes it to the repo's **Releases** page under the tag
`debug-latest` (also re-runnable manually from the Actions tab). Open the
repo on GitHub → **Releases** → `debug-latest` → download `app-debug.apk`
→ sideload it per the instructions below. That release is overwritten on
every push, so it always has the newest build.

## Important limitations - read this first

- **This project was originally written without a working Android build
  environment** (the sandbox had no Android SDK, and the host that
  distributes it, `dl.google.com`, was network-blocked there), so the first
  few commits shipped uncompiled. It's since been verified through GitHub
  Actions (`.github/workflows/build-apk.yml`) - `./gradlew assembleDebug`
  now builds cleanly and produces a real, installable APK. What that
  verifies is that the code *compiles*; it has not been exercised feature-
  by-feature in a running app/emulator, so treat "it builds" as a lower bar
  than "every screen behaves exactly as intended" and report anything that
  looks wrong on-device.
- **Card benefit data is a hand-curated snapshot (~85 cards), not a live
  feed.** Most entries reflect public information as understood in
  September 2026 (a handful of older entries date to early-2025) and *will*
  drift out of date - the app surfaces this warning on the dashboard, but
  always verify anything that matters (fees, credits, elite status rules)
  against the issuer's own site before acting on it. Several entries also
  flag recent product discontinuations/rebrands/naming confusions
  surfaced during research - read a card's notes before trusting it.
- **Point valuations used to rank cards are estimates, not guarantees.**
  Comparing a cash-back card to a transferable-points card requires
  assuming a cents-per-point value (see `RewardCurrency` in
  `CardModels.kt`) - these are the kind of figures independent points
  trackers publish, not a redemption you're guaranteed to get.
- **The Gmail scan uses an LLM to read email text, not fixed regex rules**,
  because loyalty program emails (hotel and airline) have no consistent
  format. It can miss things or occasionally misread a number - always
  double check anything it finds before relying on it (e.g. before a trip).
  Every email it reads costs an API call; Settings has a per-sync cap
  (default 60 emails) and the processed-email ledger means nothing is read
  twice.
- **Tier ladders, expiry rules, transfer ratios and point values are a
  curated snapshot too** (`data/catalog/LoyaltyProgramCatalog.kt` and
  `TransferPartnerCatalog.kt`, each entry dated). Where a threshold couldn't
  be pinned down it's left blank rather than guessed (e.g. Atmos Rewards,
  Radisson Rewards). Programs change these every year or two.
- **Award search is not live.** No hotel or airline program offers a public
  award-availability API to personal apps, so "Ask" researches with web
  search and the app deep-links to each program's own award search to
  confirm.
- **Upgrading from the previous version** migrates the local database
  (v1 -> v3) in place; if anything looks off, clearing the app's storage
  and re-syncing rebuilds it from Gmail.

## Architecture

- **UI**: Jetpack Compose (Material 3), single-activity, Navigation Compose
  with a bottom nav bar (Home / Loyalty / Trips / Maximize / Cards) and a
  Settings screen.
- **Background sync**: WorkManager periodic work (`work/EmailSyncWorker`)
  with a Hilt-injected worker; interval, notifications, lookback window and
  per-sync email cap are all in Settings.
- **State**: MVVM - one `ViewModel` per screen, `StateFlow` for UI state.
- **DI**: Hilt.
- **Local storage**: Room v2 (wallet cards, loyalty accounts, balance
  snapshots, trips, activity feed, processed-email ledger, cached card
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

The debug build is signed with the fixed keystore checked into this repo
(`app/debug.keystore`) rather than an auto-generated one - see the comment
in `app/build.gradle.kts` for why that matters for CI. Install with:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to your phone and open it with a file manager - Android
will prompt you to allow installs from that app ("Install unknown apps")
the first time.

**If you get "App not installed"**, it means a version signed with a
*different* key is already on your phone (this happened during early CI
runs, before the fixed keystore existed - each of those was signed with a
different random key). Uninstall the existing "Travel Benefits" app once,
then install the new APK - from then on, every build from this repo shares
the same signature and will update cleanly instead of needing this again.

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

## Using the email monitor

1. Set up the Anthropic key and Gmail connection (below).
2. On **Home**, tap **Sync now**. The first run reads up to a year of mail
   from known hotel/airline/OTA senders (capped per sync); later runs only
   read what's new.
3. In **Settings → Email monitor**, turn on **Background sync**, pick an
   interval, and allow notifications if you want a heads-up when a balance
   changes, a trip appears or points are about to expire.
4. Anything the scan gets wrong can be corrected by tapping the account or
   trip; manual edits win over later scans of older emails.

## Project layout

```
app/src/main/java/com/travelbenefits/app/
  auth/            AppAuth-based Gmail OAuth (PKCE)
  data/
    catalog/       Hand-curated card catalog, loyalty program profiles, transfer partners
    local/         Room database (+ migrations), DAOs, entities, prefs
    remote/        Retrofit clients for Anthropic + Gmail
    repository/    Email monitor, loyalty, trips, activity, advisor, wallet, card lookup
  di/              Hilt modules
  domain/          Domain models, loyalty insights (tiers/expiry/alerts), points optimizer
  notifications/   Notification channel + sync-result notifications
  work/            WorkManager worker + scheduler for background sync
  ui/              Compose screens (dashboard, loyalty, trips, optimize, wallet, settings)
```
