# Testing record

What was run, where, and what it does and does not prove. Kept honest:
nothing below was run on a physical phone in this session.

## Levels

| Level | What it is | Where it runs | Proves |
|---|---|---|---|
| Static | Code read and reasoned about | this session | The logic is present and wired |
| Unit | JVM tests under `app/src/test` | `build` job on every push (`./gradlew testDebugUnitTest`) | Engines are deterministic and produce the specified numbers |
| Instrumented | Emulator tests under `app/src/androidTest` | `instrumented` job (API 30 x86_64 emulator, KVM) | Migrations work on real SQLite; the app launches and navigates on an empty database |
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
| Stage E | see the Actions tab for the run on the latest commit; `docs/COMPLETION_RECORD.md` records the observed outcome |

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

## Known limitations

- No on-device testing was performed in this session; the emulator job is
  the first time the UI has been exercised at all, so report anything odd.
- Anniversary and statement periods are assumed from the card's date opened
  when the issuer page did not state the basis; such credits show "unknown"
  or "anniversary (assumed)" and never a confident date.
- Award data are cached inventory (seats.aero, when keyed), research leads
  or manual imports; nothing is live availability.
- No live flight status, gate or terminal data.
- Provenance is complete for 11 cards; other catalog entries are dated as a
  group, not per rule.
- Email extraction quality depends on the model; the app never shows an
  extracted value without its source subject, and users can correct any
  record.
