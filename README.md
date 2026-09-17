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
- **Needs attention** - staged expiry reminders (90/60/30/7 days) with a
  "how to reset the clock" tip per program, "3 nights to Gold" nudges,
  upcoming trips and check-in reminders, bookings missing a loyalty number,
  unused card credits near period end, certificates about to expire,
  welcome-bonus deadlines and quarterly category activation. A daily local
  check pushes each reminder once as a notification.
- **Credits & certificates** - every catalog credit on your cards (airline
  fee, Uber, hotel, Global Entry...) as a per-period checklist with the
  unused value at stake, plus free-night certificates, companion passes and
  upgrade awards (found in email or added by hand) with expiry dates.
- **Welcome bonuses & rotating categories** - minimum-spend tracker per card
  (statement emails add to it), and this quarter's 5% categories for
  rotating/choice cards (Freedom Flex, Discover it, U.S. Bank Cash+) with an
  "activated" flag and an AI lookup, feeding the best-card picker.
- **Household members** - loyalty accounts and cards can be tagged with a
  family member's name and are grouped by person.
- **Spend analysis (Plaid, optional)** - link card accounts through a tiny
  self-hosted worker (`plaid-backend/`), and the Spend tab shows real spend
  per category, which card it went on, and what the best card in your
  wallet would have earned: "you put $1,200 of dining on the wrong card,
  ~$36 left on the table". Mapped cards also get exact welcome-bonus
  progress. Plaid returns transactions, not points balances.
- **Backup** - JSON export/import of everything and a CSV balance sheet;
  no cloud copy exists, so export now and then.
- **Maximize points**
  - *Earn*: best card for a spending category, either by overall estimated
    value or by "most points into program X" (co-brand cards directly, bank
    points at their transfer ratio), plus any elite status your cards grant.
  - *Redeem*: cash-vs-points calculator that accounts for the points and
    card rewards a paid booking would have earned.
  - *Transfer*: which of your cards' currencies move into a program, at what
    ratio, and whether that's a good use of them.
  - *Watch*: saved award watches ("Hyatt Regency Kyoto, 3 nights in March")
    that a daily research job checks with web search and flags when they
    look bookable, plus weekly research of live bank transfer bonuses shown
    against your transfer options. Both are researched, not live inventory.
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
3. To **your own Plaid worker** (and, via Plaid Link, to Plaid) - only if
   you set it up. See `plaid-backend/README.md`.

No account system, no analytics/tracking SDKs. The only backend is the
optional Plaid worker you host yourself (needed because Plaid's API secret
can't live in an APK); without it, everything still works minus the Spend
tab.

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
- **Membership numbers are filled in only when an email prints them in
  full.** The scan takes them from statement emails and, more usefully, from
  booking confirmations, where the number is printed because the airline
  needs it to credit the flight. Masked forms (`****4821`, `xxxx1234`,
  `ending in 4821`) are rejected rather than stored: a masked number is not a
  short membership number, and saving one would stop the app asking for the
  real thing while leaving you holding something you cannot use. A number
  already stored is never overwritten, so anything you typed in stands. The
  number is shown in full everywhere in the app - it is your own data on your
  own device, and hiding it from you would only make it harder to use.
- **Welcome emails are searched without a date limit.** A "welcome to the
  programme" email always prints the membership number, and is always older
  than the sync window, so for any tracked account that still has no number
  the scan runs one extra search against that programme's senders with no
  `after:` bound. It stops as soon as a number is found, so it costs nothing
  once your accounts are filled in.
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
- **The catalog covers five markets, and covers them unevenly.** Alongside
  the US programs there are entries for Australia, the UK, Canada and South
  Africa (Qantas, Velocity, Flybuys, Everyday Rewards; BA Executive Club,
  Virgin Atlantic, Tesco Clubcard, Nectar, Boots; Aeroplan, WestJet, PC
  Optimum, Scene+, AIR MILES; SAA Voyager, eBucks, Clicks ClubCard, Smart
  Shopper, Discovery Vitality). These carry balances, sender domains for the
  Gmail scan, and expiry rules where those could be checked - but **no tier
  ladders and no point valuations**. A valuation in this app is denominated
  in US cents and there is no exchange rate in it, so rather than print a
  placeholder the app says "not in the app for this programme" and leaves
  them out of the Earn, Redeem and Transfer calculators. `docs/SOURCES.md`
  lists what was checked and what wasn't. Settings has a per-market switch
  for the Gmail scan (each program costs one Gmail search per sync), on for
  every market by default.
- **Award search is not live.** No hotel or airline program offers a public
  award-availability API to personal apps, so "Ask" researches with web
  search and the app deep-links to each program's own award search to
  confirm.
- **Upgrading from any previous version** migrates the local database
  (v1 -> v9) in place with additive migrations only - there is no
  destructive fallback, and the migrations are exercised on a real emulator
  in CI (`MigrationTest`). Amounts the old version never recorded are
  flagged as unknown rather than invented.
- **What has and has not been tested** is written down in
  `docs/TESTING.md`: engines are unit-tested, launch/navigation and
  migrations are emulator-tested, and nothing has been run against live
  Gmail, Plaid or seats.aero accounts from this repository's CI.

## Architecture

- **UI**: Jetpack Compose (Material 3), single-activity, Navigation Compose
  with a bottom nav bar (Home / Loyalty / Trips / Maximize / Cards) and a
  Settings screen.
- **Background work**: WorkManager periodic jobs with Hilt-injected workers:
  `EmailSyncWorker` (Gmail, on your interval), `DailyInsightsWorker` (local
  reminders, once a day, no network) and `ResearchWorker` (award watches
  daily, transfer bonuses weekly, opt-in, uses the API).
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

Gmail access needs your own OAuth client. Two things have to line up, and the
second one catches everybody:

1. **The client must be registered for this exact app.** In Google Cloud
   Console, create an OAuth client of type **Android** and give it:
   - **Package name**: `com.travelbenefits.app.debug` for the debug APK
     published here. Note the `.debug` suffix, which the debug build type adds.
     A release build would be `com.travelbenefits.app`.
   - **SHA-1 certificate fingerprint**: for the published debug APK, signed
     with the checked-in debug keystore, this is
     `54:58:04:B8:2A:F2:21:6D:A0:7B:C1:ED:04:D2:23:19:F2:59:F5:B0`.

   Settings, Connections, Advanced configuration shows both values read from
   the build you actually installed, so copy them from there rather than from
   this page. Getting the client ID wrong produces
   `Error 401: invalid_client - The OAuth client was not found`.

2. **The app must be built to receive that client's callback.** Google returns
   the sign-in result to a custom URL scheme that is the reversed client ID:
   client `123456-abc.apps.googleusercontent.com` calls back on
   `com.googleusercontent.apps.123456-abc`. Android resolves that scheme from
   the manifest, which is fixed when the APK is compiled, so **the generic
   `debug-latest` download cannot complete sign-in for your client**. It carries
   a placeholder scheme. Everything else in the app works with it.

   To get a build that can:

   - On GitHub, open **Actions**, choose **Build Android APK**, click **Run
     workflow**, and paste your reversed client ID into
     `appAuthRedirectScheme`. The APK is published to the **gmail-build**
     release when it finishes.
   - Or build locally:
     ```bash
     ./gradlew assembleDebug -PappAuthRedirectScheme=com.googleusercontent.apps.123456-abc
     ```

   Settings warns you when the installed build and the client ID you entered
   cannot work together, and names the scheme you need.

3. **Custom URI schemes must be switched on for that client.** Google disables
   them by default for Android clients created now, because a custom scheme can
   be claimed by another installed app. With the switch off you get
   `Error 400: invalid_request - Custom URI scheme is not enabled for your
   Android client`, even though the client ID and the build are both correct.

   Open the Android client in Google Cloud Console, find the **Advanced
   Settings** section on the client configuration page, and enable the custom
   URI scheme option, then save. Changes can take a few minutes to apply.

   This app deliberately uses AppAuth rather than Google Identity Services, the
   alternative Google recommends, because Identity Services requires Google Play
   Services and the point of a sideloaded build is to keep working without it.
   That trade is why the switch is needed. If Google eventually removes the
   switch for Android clients, Gmail scanning here would need rethinking;
   everything else in the app is unaffected either way.

Also enable the **Gmail API** in the same Google Cloud project, and add your own
Google account as a test user on the OAuth consent screen while the app is in
testing. The only scope requested is `gmail.readonly`.

## First launch: the setup guide

The app opens with a three-step guide: what it does and never does, how you
want to start (**add cards and programs by hand**, **import a backup**, or
**set up optional connections**), and what stays private. Nothing asks for a
key or login. Every connection is optional and the app is fully usable with
manual entries only. The guide can be reopened from **Settings → About the
data → Show the setup guide again**.

Home shows a **Data sources** card with each source's state (connected,
needs attention, or off), the last sync time, linked institutions and any
sync error, plus loyalty balances that have not been refreshed for 60+ days.

## Optional connections: what they need and cost

| Connection | What it gives you | What you need | Cost (as of 2026-09-14, verify) |
|---|---|---|---|
| Anthropic API key | Email extraction, card lookup for cards not in the catalog, the points advisor, research leads | A key from console.anthropic.com | Pay-as-you-go per token; a sync of 60 emails is typically a few cents |
| Gmail (read-only) | Balances, status, trips, statements and expiry notices from your inbox | Your own Google Cloud OAuth client (steps below) and the Anthropic key | Free |
| Plaid | Real spend per card and category, cap usage, welcome-bonus progress | A tiny worker you host (`plaid-backend/`) holding the Plaid secret | Plaid's free development tier covers a small number of linked items; production pricing is per item, see plaid.com/pricing |
| seats.aero Partner API | Cached award-flight availability with an "as of" time | A Pro subscription key | Paid subscription, see seats.aero |

Keys are entered under **Settings → Connections → Advanced configuration**
and are stored only in Android's encrypted preferences. They are never
exported, logged (credential headers are redacted from HTTP logging) or
compiled into the APK.

## Setting up the Anthropic API key

1. Get an API key from [console.anthropic.com](https://console.anthropic.com).
2. In the app, go to **Settings → Connections → Advanced configuration**
   and paste it into "Anthropic API key".

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

## Signing your own release build

The debug variant is signed with the checked-in debug keystore so CI builds
install over each other. For a release build, generate your own key and point
Gradle at it. Nothing is committed: the path and passwords are read from
gradle properties or the environment.

```bash
keytool -genkeypair -v -keystore ~/travel-release.jks -keyalg RSA -keysize 2048   -validity 10000 -alias travel
```

Then add to `~/.gradle/gradle.properties` (not to the repository):

```properties
releaseStoreFile=/home/you/travel-release.jks
releaseStorePassword=...
releaseKeyAlias=travel
releaseKeyPassword=...
```

`./gradlew assembleRelease` then produces a signed, minified APK. Without
those properties the same command still runs R8 and produces an unsigned
APK, which is what CI checks.

## Testing

- `./gradlew testDebugUnitTest` runs the JVM engine tests (ledger maths,
  best-card picker, action ranking, forecasting, reconciliation, award
  labelling, prompt fencing, connection status).
- `./gradlew connectedDebugAndroidTest` runs the emulator tests: Room
  migrations from the original v1 schema to the current version on real
  SQLite, and a launch/navigation smoke test through the setup guide.
- `./gradlew assembleRelease` builds the shrunk (R8) variant. CI runs it on
  every push so a missing keep rule in `app/proguard-rules.pro` fails the
  build rather than crashing on a phone. It builds unsigned unless you
  configure a key (below).
- Both run in GitHub Actions on every push (`build` and `instrumented`
  jobs); reports are uploaded as artifacts. See `docs/TESTING.md` for what
  each level proves, and `docs/REQUIREMENTS_MATRIX.md` for the status of
  every requirement.

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
  ui/              Compose screens (onboarding, dashboard, loyalty, trips, optimize, wallet, settings, benefits, card value, reconcile, airport)
docs/              Requirement matrix, sources with dates, testing record, completion record
```
