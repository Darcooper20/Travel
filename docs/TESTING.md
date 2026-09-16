# Testing record

What was run, where, and what it does and does not prove. Kept honest:
nothing below was run on a physical phone in this session.

## Levels

| Level | What it is | Where it runs | Proves |
|---|---|---|---|
| Static | Code read and reasoned about | this session | The logic is present and wired |
| Unit | JVM tests under `app/src/test` | `build` job on every push (`./gradlew testDebugUnitTest`) | Engines are deterministic and produce the specified numbers |
| Instrumented | Emulator tests under `app/src/androidTest` | `instrumented` job (API 30 x86_64 emulator, KVM) | Migrations work on real SQLite; the app launches and navigates on an empty database |
| Release build | `./gradlew assembleRelease` | `build` job | R8 shrinking succeeds with the keep rules in `app/proguard-rules.pro`. It does **not** prove the shrunk APK behaves correctly at runtime |
| Live | Real Gmail / Anthropic / Plaid / seats.aero calls | **not run** (no credentials or device in this environment) | - |

## Unit test classes (JVM)

| Class | Covers |
|---|---|
| `BenefitLedgerEngineTest` | Period windows per basis, allowance/received/pending/uncommitted maths, the $100 / $35 / $20 → $45 acceptance example, monthly sub-limits |
| `ParsingAndMappingTest` | Catalog versioning by date, freshness thresholds, sub-limit parsing |
| `PurchaseRecommenderTest` | Cap-aware recommendation including the $50-remaining-under-5% → $3.00 example, cash-back in dollars, points at ¢/pt, offer stacking notes |
| `ActionEngineTest` | Ranking (deadline, amount, confidence, effort), snooze / dismiss / complete state application by key |
| `StatusAndReconciliationTest` | Status forecasting with tri-state award-stay rules, reconciliation states and claim drafts, trip payment comparison |
| `StageDTest` | Award result labelling, certificate matching, merchant-offer stacking, preferences round-trip |
| `StageETest` | Prompt-injection fencing (`PromptGuardTest`), connection status states (`ConnectionStatusCalculatorTest`) |
| `SyncReportTest` | A failed sync can never reuse the wording of a clean one; partial runs report both what was found and what was missed, and promise a retry |
| `CategorisationTest` | Plaid category vs merchant-name precedence, non-spend never reclassified by name, shared keyword table, history beats keywords |
| `CapAndSpendTest` | Cap overrides short-circuit (no duplicate rows), account-year and statement-cycle period starts, misrouted spend, unmapped spend kept separate, pending and refunds excluded, discontinued cards never "best" |
| `OptimizeAndValueTest` | Ranking order, discontinued cards last, cash-vs-points including forgone earnings, transfer dedupe, earn-plan ordering, annual-fee subtraction, unverified fees left uncounted, only-card forfeiture warning, renewal dates, duplicate benefits, protection checklists |
| `LoyaltyInsightsTest` | Unknown balances stay unknown, tier progress clamping, expiry precedence and staging, loyalty-number tri-state wording, trip reminders, household naming |

Run locally with an Android SDK installed:

```bash
./gradlew testDebugUnitTest
open app/build/reports/tests/testDebugUnitTest/index.html
```

## Instrumented tests (emulator)

| Class | Covers |
|---|---|
| `MigrationTest` | A database created with the original v1 DDL migrates through every hand-written migration to the current version and passes Room's schema validation; rows survive with new columns NULL; `credit_usage` rows become `USED_UNKNOWN_AMOUNT` ledger entries with amount 0 and source MIGRATION; the old loyalty boolean maps true → CONFIRMED and false → UNKNOWN; a fresh install opens at the current version |
| `AppSmokeTest` | First launch shows the guided setup; finishing it lands in the app; each bottom tab and Settings render on an empty database; skipping goes straight to Home |

Run locally with an emulator or device attached:

```bash
./gradlew connectedDebugAndroidTest
open app/build/reports/androidTests/connected/index.html
```

## CI history for this programme

| Commit | Result |
|---|---|
| Stage A | compile fix (nullable receiver), then green |
| Stage B | compile fixes (expression-body return, missing import), then green |
| Stage C | compiled; 6 `PurchaseRecommenderTest` failures (cash-back units) fixed in Stage D |
| Stage D | compiled; 1 `ActionEngineTest` failure (undated items outranked dated ones) fixed in Stage E |
| Stage E (ea260bf) | `build` green (unit tests + APK); `instrumented` failed to compile (androidTest-only errors) |
| Stage E follow-ups (fe96bdc … 213dc22) | migrations passed on every run; the smoke test exposed, in order: a below-the-fold tap, stale onboarding state cached by the `AppPrefs` singleton, a race with the deferred post-onboarding navigation, and finally a real navigation bug (Home tab ignored when a screen sat directly above the start destination) |
| Stage E final (b0425a0) | **both jobs green**: 34 unit tests, 5 instrumented tests (3 migration, 2 smoke) on an API 30 emulator; APK published to `debug-latest` |
| Post-audit fixes (042e0a0 … 0da229b) | **all green**: 111 unit tests, 5 instrumented tests, debug APK and release (R8) APK. Four defects fixed, previously untested engines covered. See "Audit findings" below |
| Gmail crash + auto-add + uncatalogued programmes (a1c…42316da) | **all green** on both emulator API levels. The AppCompat theme crash on `RedirectUriReceiverActivity` was found from a user's crash report, not from CI, which is why an instrumented test now launches that activity directly |
| Non-US programmes (2e9a6e7) | **all green**: 152 unit tests, 8 instrumented tests on API 30 and API 34 emulators, debug and release (R8) APKs. 12 new tests cover market coverage, profile completeness, the unvalued-programme path, and the verified expiry windows |
| Null enum columns (0e6f830) | **all green**: 152 unit tests, 13 instrumented tests on both emulators. Fixed a crash on every completed sync: Room's generated `bind` calls a non-null type converter with no null check of its own, so a nullable enum column threw Kotlin's non-null intrinsic. Found from a device crash report, not from CI - and unfindable from a JVM test, since the defect was in generated code. `NullableEnumColumnTest` now inserts every nullable enum column as null through the real DAOs |

## Acceptance journey

Specified journey: add a card → ask which card for a purchase → record a
credit use in the ledger → a trip appears from email → reconcile what was
earned.

| Step | Automated? | How |
|---|---|---|
| Add a card from the catalog | instrumented smoke test reaches the Cards tab; adding is not scripted | manual |
| Best card for a $100 purchase with a $50 cap remaining | unit (`PurchaseRecommenderTest`) | - |
| Ledger: allowance / received / pending / uncommitted | unit (`BenefitLedgerEngineTest`) | - |
| Trip from email with cancellation and loyalty-number state | static only: depends on live model output | manual with a real key |
| Reconciliation queue and claim draft | unit (`StatusAndReconciliationTest`) | - |

The UI journey end-to-end is therefore **partial**: every calculation on
the path is unit-tested, launch and navigation are emulator-tested, but the
screens have not been driven through the full sequence by an automated test
or by a person on a device.

## Audit findings and what they cost

Four defects were found by reading the source after Stage E, all now fixed
and covered by tests:

1. **A failed sync read as a clean one.** Gmail searches, message fetches and
   model extraction all swallowed their errors, so "no new travel or loyalty
   emails" was printed whether the mailbox was empty or unreachable. Fixed by
   counting each failure class, carrying the cause, and forbidding clean
   wording on a partial run.
2. **Failed batches were marked as read.** Emails whose extraction batch
   errored were still written to the processed-email ledger, which later syncs
   skip. One transient API error therefore dropped those emails permanently,
   short of a full re-scan. Fixed by admitting only understood emails to the
   ledger.
3. **Merchant names were ignored when categorising Plaid transactions.** The
   mapper took the name as a parameter and never read it, so anything Plaid
   filed under a vague bucket became OTHER and under-counted the
   misrouted-spend headline.
4. **Two label and branch mistakes**: a nested `let` label made a manual
   cap override record its figure twice, and the activation caveat never fired
   for rotating categories.

A fifth problem surfaced the moment `assembleRelease` was added to CI: R8
failed on four missing `com.google.errorprone.annotations` classes referenced
from Tink, which `androidx.security-crypto` pulls in for
EncryptedSharedPreferences. The release variant had never been built, so the
first person to attempt a release would have hit it. Fixed with a `dontwarn`
in `app/proguard-rules.pro`; the release APK now builds in CI on every push.

Three of the four were invisible to the user as errors: they produced
confident, wrong-but-plausible output. That is the argument for the coverage
added alongside them.

## Known limitations

- No testing on a physical phone was performed in this session. The emulator
  job exercises launch, onboarding, every bottom tab and Settings on an empty
  database; screens with data (cards, trips, ledger entries) have only been
  exercised through their engines' unit tests, so report anything odd.
- Anniversary and statement periods are assumed from the card's date opened
  when the issuer page did not state the basis; such credits show "unknown"
  or "anniversary (assumed)" and never a confident date.
- Award data are cached inventory (seats.aero, when keyed), research leads
  or manual imports; nothing is live availability.
- No live flight status, gate or terminal data.
- Provenance is complete for 11 cards; other catalog entries are dated as a
  group, not per rule.
- The release variant is built but never run. CI proves the R8 rules let it
  compile and shrink (it did not, until the Tink `dontwarn` was added); nobody
  has installed a minified build and exercised it.
  Reflection-driven paths (Retrofit, kotlinx.serialization, Room, AppAuth)
  have keep rules, but the first real release install should be smoke-tested
  by hand.
- Email extraction quality depends on the model; the app never shows an
  extracted value without its source subject, and users can correct any
  record.
