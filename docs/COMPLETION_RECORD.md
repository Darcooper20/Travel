# Completion record

Living document for the "reliable decisions" programme. Updated at the end of
every stage so work can resume from it. Status words: **implemented**,
**partial**, **blocked**, **not started**. Verification words: *static*
(read in source), *unit* (JVM tests in `app/src/test`), *instrumented*
(emulator tests in `app/src/androidTest`, run in CI), *live* (real provider).
Nothing here claims device testing that was not run.

## 1. Findings from the APK review, verified against source (2026-09-14)

| Lead | Status | Where |
|---|---|---|
| `RewardRate` had no spending-cap field | **Confirmed → resolved in Stage A** | `domain/model/CardModels.kt` now has `capUsd`, `capPeriod`, `capGroup`, `fallbackMultiplier`, `channel`, `requiresActivation` |
| Credit usage was a last-used timestamp, not an amount ledger | **Confirmed → resolved in Stage A** | `credit_usage` replaced by `benefit_ledger` (`BenefitLedgerEntity`); old rows migrate as `USED_UNKNOWN_AMOUNT`, never as invented amounts |
| Credit reset wording admitted calendar-vs-anniversary limits | **Confirmed → resolved in Stage A** | `CardCredit.periodBasis` (CALENDAR / ANNIVERSARY / STATEMENT / UNKNOWN); anniversary periods use the card's date opened, unknown basis is shown as unknown |
| Award watches used AI web research, not inventory | **Confirmed → partially resolved in Stage D** | `AwardSearchProvider` interface; AI research kept as one labelled provider; seats.aero Partner API adapter behind a user key; manual import provider |
| Advice generalised whether award stays count for status | **Confirmed → resolved in Stage C** | per-program `awardStaysCountForStatus` tri-state (true / false / null = unknown) in `LoyaltyProgramCatalog`; copy no longer generalises |
| Email extraction told the AI to skip cancelled bookings | **Confirmed → resolved in Stage C** | cancellations are extracted as `status = CANCELLED` and update the existing trip; reminders are suppressed |
| Missing loyalty number modelled as a boolean | **Confirmed → resolved in Stage C** | `Trip.loyaltyNumberState` CONFIRMED / MISSING / UNKNOWN; absence in an email maps to UNKNOWN |
| Setup exposed API keys, OAuth and backend settings | **Confirmed → resolved in Stage E** | guided onboarding with manual entry / import / optional connections; advanced configuration moved behind an "Advanced" section |

## 2. Requirement-to-code map

See `docs/REQUIREMENTS_MATRIX.md` (kept in sync with this file).

## 3. Stage log

- Stage A (rules, provenance, migrations, ledger): **implemented** - commit "Stage A"; CI green after a nullable-receiver fix. Unit: `BenefitLedgerEngineTest`, `ParsingAndMappingTest`.
- Stage B (purchase-aware best card, actions with states, card value, widget): **implemented** - CI green after two compile fixes. Unit: `PurchaseRecommenderTest`, `ActionEngineTest`.
- Stage C (trips hub, status forecasting, reconciliation): **implemented** - compiled; six cash-back unit failures fixed in Stage D. Unit: `StatusAndReconciliationTest`.
- Stage D (award providers, certificates, offers, households, airport mode, protections, preferences): **implemented** - compiled; one ranking test fixed in Stage E. Unit: `StageDTest`.
- Stage E (onboarding, connection status, prompt hardening, instrumented tests, docs): **implemented** - commits ea260bf … b0425a0. Unit: `StageETest`. Instrumented: `MigrationTest`, `AppSmokeTest` (new `instrumented` CI job). Final CI on b0425a0: build and instrumented jobs both green. The smoke test found and the fix landed for a real bug: the Home tab was ignored whenever a screen sat directly above the start destination (`AppNavHost.navigateTab`).

- Post-audit pass (042e0a0): source audit of the finished app found four defects, all fixed with tests. Three produced confident but wrong output rather than visible errors: a failed sync reported as a clean one, emails silently dropped from future scans after a transient API error, and Plaid spend under-categorised because the merchant name was ignored. Also added release-build verification to CI and replaced glyph-only status indicators. Detail in `docs/TESTING.md` under "Audit findings".

## 4. Open items (for whoever resumes)

1. Per-rule provenance for the ~75 catalog cards without `RuleProvenance` (mechanical, one issuer page each).
2. End-to-end UI acceptance journey as an instrumented test (add card → purchase → ledger → trip → reconcile); engines are covered, screens are not driven.
3. Live verification with real credentials: Gmail extraction quality, seats.aero adapter, Plaid sync on a device.
4. Install and exercise a minified release APK by hand once. CI proves it builds and shrinks; nothing proves it runs.
5. Live flight status is out of scope until a licensed data source is chosen.
