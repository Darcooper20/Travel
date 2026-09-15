# Requirement matrix

Status words: **implemented**, **partial**, **blocked**, **not started**.
Verification words: *static* (read in source), *unit* (JVM tests in
`app/src/test`, run by CI on every push), *instrumented* (emulator tests in
`app/src/androidTest`, run by the `instrumented` CI job), *live* (exercised
against the real provider - nothing in this table is marked live; see
`docs/TESTING.md`).

Paths are relative to `app/src/main/java/com/travelbenefits/app/`.

## 1. Verify the review leads

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Each of the eight leads checked against source, findings recorded | implemented | `docs/COMPLETION_RECORD.md` §1 | static |

## 2. Rules engine with provenance

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Category multipliers with caps, cap period, cap group, fallback rate | implemented | `domain/model/CardModels.kt` (`RewardRate`), `domain/CapUsageCalculator.kt` | unit (`PurchaseRecommenderTest`, `StageDTest`) |
| Booking channel (direct vs issuer portal) and activation flags | implemented | `RewardRate.channel`, `requiresActivation`; `PurchaseRecommender.applyOffers` | unit |
| Versioned catalog entries with effective dates and previous versions | implemented | `CardCatalogEntry.effectiveFrom/effectiveTo/previousVersions`, `CardCatalog.findByIdOn` | unit (`ParsingAndMappingTest`) |
| Provenance (source URL, verified-on date, freshness) per rule | implemented for 11 flagship cards; **partial** overall (remaining ~75 catalog cards carry only the catalog-wide "as of" note) | `RuleProvenance`, `CardCatalog.kt` | static |
| Credit period basis (calendar / anniversary / statement / unknown) | implemented | `CardCredit.periodBasis`, `BenefitLedgerEngine.periodWindow` | unit (`BenefitLedgerEngineTest`) |
| Travel perks and authorised-user pricing on entries | implemented | `TravelPerk`, `CardCatalogEntry.authorizedUserFeeUsd` | static |
| Money as integer cents in the ledger | implemented | `BenefitLedgerEntity.amountCents`, `BenefitLedgerEngine` | unit, instrumented (`MigrationTest`) |

## 3. Purchase-aware best-card recommendation

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Recommend for merchant/category/amount with cap remaining and fallback rate (acceptance: $50 left under 5% cap, $100 purchase → $3.00) | implemented | `domain/PurchaseRecommender.kt` | unit (`PurchaseRecommenderTest`) |
| Merchant text → category classifier | implemented | `domain/MerchantClassifier.kt`, shared table in `domain/MerchantKeywords.kt` | unit (`MerchantClassifierTest`) |
| Offers stacked when enrolled; portal/min-spend notes | implemented | `PurchaseRecommender.applyOffers`, `data/repository/OfferRepository.kt` | unit (`StageDTest`) |
| Home-screen widget and app shortcut opening the picker | implemented | `widget/BestCardWidget.kt`, `res/xml/shortcuts.xml`, `ui/navigation/NavigationRequests.kt` | static (not exercised on a device in this session) |
| Cash-back displayed in dollars, points valued at user or default ¢/pt | implemented | `PurchaseRecommender` (`rewardUnits` / `rewardsValue`), `OverrideRepository.valuations` | unit |
| Activation caveat shown for every rate that needs one | implemented (was missing on rotating categories) | `PurchaseRecommender` | unit |
| Cap usage from transactions, with manual overrides winning outright | implemented | `domain/CapUsageCalculator.kt` | unit (`CapUsageCalculatorTest`) |
| Plaid transactions categorised using the merchant name, not just Plaid's bucket | implemented (previously ignored) | `domain/PlaidCategoryMapper.kt` | unit (`PlaidCategoryMapperTest`) |

## 4. Benefit ledger

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Allowance / received / pending / uncommitted per period (acceptance: $100 allowance, $35 received, $20 pending → $45 uncommitted) | implemented | `domain/BenefitLedgerEngine.kt`, `domain/model/LedgerModels.kt` | unit (`BenefitLedgerEngineTest`) |
| Pending never counts as received; unknown amounts flagged not guessed | implemented | `LedgerKind.USED_UNKNOWN_AMOUNT`, `needsConfirmation` | unit, instrumented |
| Monthly sub-limits inside annual credits | implemented | `CardCredit.monthlySublimitUsd` | unit |
| Non-destructive migration from the old "used" checkbox | implemented | `Migrations.MIGRATION_5_6` | instrumented (`MigrationTest`) |

## 5. Action dashboard

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Ranked actions with amount, deadline, confidence, effort, next step | implemented | `domain/ActionEngine.kt`, `ui/dashboard/DashboardScreen.kt` | unit (`ActionEngineTest`) |
| Snooze / dismiss / complete with undo; states survive restarts | implemented | `ActionStateEntity`, `OverrideRepository`, `DashboardViewModel.setActionState/undoActionState` | unit (state application), static (UI) |
| Quiet hours for notifications | implemented | `SyncSettings.isQuietNow`, `notifications/AppNotifier.kt` | unit |

## 6. Annual card value and renewals

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Value used vs fee, renewal date, keep / downgrade / cancel framing with reasons | implemented | `domain/CardValueAnalyzer.kt`, `ui/cardvalue/` | unit (`ActionEngineTest` covers inputs), static |
| User overrides for valuations, authorised users, bonus points | implemented | `OverrideRepository` scopes `card:<id>`, `currency:<NAME>` | static |

## 7. Trips as the hub

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Trip status (confirmed / changed / cancelled), segments, booking history | implemented | `domain/model/TripModels.kt`, `TripDetailEntities.kt`, `ui/trips/TripDetailScreen.kt` | unit (`StatusAndReconciliationTest`), instrumented (migration) |
| Loyalty number tri-state (confirmed / missing / unknown) | implemented | `Trip.loyaltyNumberState`, extraction prompt | unit, instrumented |
| Payment-card comparison for a trip | implemented | `domain/TripPaymentComparator.kt` | unit |
| Cancellation terms and local departure time / zone extracted | implemented | `EmailMonitorRepository` prompt fields | static (depends on model output at runtime) |
| Live flight status, gate or terminal data | **not started** (no free, licensed source; would need FlightAware/Cirium API) | - | - |

## 8. Award search, transfers, certificates

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Provider abstraction with labelled result kinds (cached inventory / research lead / manual import) | implemented | `data/remote/awards/AwardSearchProvider.kt`, `domain/model/AwardSearchModels.kt` | unit (`StageDTest`) |
| seats.aero Partner API adapter behind a user-supplied key | implemented, **not live-tested** (no key in this environment) | `SeatsAeroProvider.kt`, `SecurePrefs.seatsAeroApiKey` | static |
| AI research provider kept, labelled as research | implemented | `ResearchAwardProvider.kt` | static |
| Manual import of award results | implemented | `AwardSearchRepository` manual provider | unit |
| Certificate matching to bookings | implemented | `domain/CertificateMatcher.kt` | unit |
| "Never transfer points or book automatically" | implemented by construction: no write API to any program exists in the code | whole codebase | static |
| Transfer-bonus tracking | implemented (AI research, dated) | `ResearchRepository.refreshTransferBonuses` | static |

## 9. Status forecasting

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Per-program forecast with qualification year and award-stay tri-state | implemented | `domain/StatusForecaster.kt`, `LoyaltyProgramCatalog.ProgramProfile` | unit |
| Programs where the rule could not be verified show "unknown" | implemented (Delta / United / JetBlue / Southwest award-stay rule = null) | `LoyaltyProgramCatalog.kt` | static |

## 10. Reconciliation

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Expected vs received queue with evidence and claim drafts | implemented | `domain/ReconciliationEngine.kt`, `ui/reconcile/` | unit |
| Expectations created from trips and statement credits | implemented | `TripRepository` expectation helpers, `BenefitsRepository` | static |

## 11. Offers, households, airport mode, protections, personalisation

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Merchant offers (enrolled flag, expiry, min spend) stacked into the picker | implemented | `OfferAndMemberEntities.kt`, `OfferRepository`, Wallet UI | unit |
| Household members and per-program pooling rule shown, never assumed | implemented | `MemberEntity`, `ProgramProfile.poolingRule` | static |
| Airport mode (offline: confirmations, member numbers, lounge access from card perks) | implemented | `ui/airport/` | static |
| Protection checklist per trip (trip delay / baggage / rental CDW from card perks) | implemented as guidance from catalog perks; **partial** because perk coverage exists only for the 11 cards with provenance | `domain/ProtectionGuide.kt` | unit |
| Explicit preferences (home airports, airlines, hotels, cabin, budget, flexibility, points preference) with reset; nothing learned silently | implemented | `domain/model/PreferenceModels.kt`, Settings | unit |

## 12. Setup, sync and privacy

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Guided onboarding: manual / import / optional connections; advanced config hidden | implemented | `ui/onboarding/`, `ui/settings/SettingsScreen.kt` | instrumented (`AppSmokeTest`) |
| Connection status: last sync, coverage, stale values, errors | implemented | `domain/ConnectionStatus.kt`, dashboard "Data sources" card | unit (`ConnectionStatusCalculatorTest`) |
| A failed sync is never reported as a clean one | implemented (previously indistinguishable) | `SyncReport` in `data/repository/EmailMonitorRepository.kt` | unit (`SyncReportTest`) |
| Emails the app could not read are retried, never silently dropped | implemented (previously written to the processed ledger anyway) | `EmailMonitorRepository.sync` / `extract` | unit, static |
| State conveyed by shape and a spoken label, not colour alone | implemented | dashboard `SourceStatusRow`, settings `ConnectionRow` | static |
| Secrets only in encrypted prefs; never in source, BuildConfig, logs or exports | implemented | `data/local/SecurePrefs.kt`, `di/NetworkModule.kt` (header redaction), `BackupRepository` | static |
| Email and web content treated as untrusted (prompt-injection hardening) | implemented | `domain/PromptGuard.kt` applied to every model call | unit (`PromptGuardTest`) |
| Read-only Gmail scope; user can disconnect and clear history | implemented | `auth/GmailAuthManager.kt`, Settings | static |

## 13. Android engineering

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Room migrations v1→v9, all additive, no destructive fallback | implemented | `data/local/Migrations.kt`, `di/DatabaseModule.kt` | instrumented |
| Background work via WorkManager with Hilt workers | implemented | `work/` | static |
| Deterministic engines with unit tests | implemented | `domain/*` | unit (13 test classes; every engine in `domain/` now has at least one) |
| Release variant builds with shrinking enabled | implemented | `app/proguard-rules.pro`, `assembleRelease` in CI | build-time only, see `docs/TESTING.md` |
| Release signing without committing a key | implemented | `app/build.gradle.kts` signing config from gradle properties or env | static |

## 14. Verification

| Requirement | Status | Where | Verified |
|---|---|---|---|
| Unit tests in CI | implemented | `.github/workflows/build-apk.yml` job `build` | unit |
| Emulator tests in CI (migrations + smoke) | implemented | job `instrumented` | instrumented |
| Acceptance journey (add card → purchase → ledger → trip → reconcile) as an automated test | **partial**: engine steps covered by unit tests; the end-to-end UI journey is not automated | `docs/TESTING.md` | - |
| Live-provider verification (Gmail, Anthropic, Plaid, seats.aero) | **blocked** in this environment (no credentials, no device) | - | - |
| Runtime verification of the minified release APK | **partial**: it builds and shrinks in CI, but no one has installed and exercised it | `docs/TESTING.md` | - |

## 15. Deliverables

| Deliverable | Status | Where |
|---|---|---|
| Source and migrations | implemented | this repository |
| Sideloadable APK and instructions | implemented (built by CI on every push) | GitHub release `debug-latest`; README "Sideloading it" |
| Requirement matrix | implemented | this file |
| Tests run and limitations | implemented | `docs/TESTING.md` |
| Sources with verification dates | implemented | `docs/SOURCES.md` |
| Provider access requirements and costs | implemented | README "Optional connections: what they need and cost" |
| Completion record for resuming | implemented | `docs/COMPLETION_RECORD.md` |
