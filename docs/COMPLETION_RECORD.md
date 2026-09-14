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

- Stage A (rules, provenance, migrations, ledger): in progress.
- Stage B: not started.
- Stage C: not started.
- Stage D: not started.
- Stage E: not started.
