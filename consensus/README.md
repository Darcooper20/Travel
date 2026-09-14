# Consensus

A personal, sideload-only Android app. You type one question; it goes to
Claude, ChatGPT, Gemini and Grok at the same time. Each model then reads
the others' answers, critiques them and revises its own. Finally the model
you picked as judge writes one unified answer and lists whatever the
models still disagree on. Follow-up questions in the same thread carry the
previous questions and unified answers as context.

Everything runs on the phone. The only network calls are direct HTTPS
requests to the four providers' APIs using your own API keys, which are
stored encrypted (Android Keystore) on the device. No backend, no accounts,
no analytics.

## What you need before it works

Four **API** keys. These are separate from any chat subscription you may
have. ChatGPT Plus, Gemini Advanced, SuperGrok and Claude Pro do **not**
include API access. Each API account is pay-as-you-go: you add a card or
prepaid credit and are billed per token used.

Set a monthly spend limit on each account. The app stores keys on the phone,
so if the phone is lost the keys go with it; a spend cap bounds the damage
and you can revoke keys from each console.

The app works with any subset: a provider without a key is simply greyed
out in the per-question options. Start with two if you want to test cheaply.

### 1. Anthropic (Claude)

1. Go to <https://console.anthropic.com/> and sign in or create an account.
2. Open **Billing** (or **Plans & Billing**) and add credit or a payment
   method. Set a monthly limit there.
3. Open **API Keys** (<https://console.anthropic.com/settings/keys>), click
   **Create Key**, name it (for example "consensus-phone") and copy it. It
   starts with `sk-ant-` and is shown only once.
4. Paste it into the app: **Settings → Claude → API key → Save**.

### 2. OpenAI (ChatGPT)

1. Go to <https://platform.openai.com/> and sign in. This is the developer
   platform, not chat.openai.com; the same login works but billing is
   separate.
2. Open **Settings → Billing** and add a payment method or prepaid credit.
   Set a **usage limit** under Limits.
3. Open **API keys** (<https://platform.openai.com/api-keys>), click
   **Create new secret key**, copy it. It starts with `sk-`.
4. Paste into **Settings → ChatGPT → API key → Save**.

Note: the app uses OpenAI's Responses API with the built-in `web_search`
tool. If your account or project is restricted to certain models, use the
model IDs the platform's **Limits** page lists as allowed.

### 3. Google (Gemini)

1. Go to <https://aistudio.google.com/> and sign in with a Google account.
2. Click **Get API key** (<https://aistudio.google.com/apikey>) and create a
   key. The first key is created in a default Google Cloud project for you.
3. The free tier is rate-limited and Google states free-tier prompts may be
   used to improve their products. To move to paid usage with higher limits
   and no training on your prompts, click **Set up billing** next to the
   project in AI Studio and enable billing on that Cloud project. Set a
   budget alert in Google Cloud **Billing → Budgets & alerts**.
4. Paste into **Settings → Gemini → API key → Save**.

Note: Google Search grounding (the "web search" feature for Gemini) is
billed per grounded request on top of tokens, and has a daily free
allowance on paid projects. Check the Gemini API pricing page for the
current figures.

### 4. xAI (Grok)

1. Go to <https://console.x.ai/> and sign in (an X account or email).
2. Create a team if prompted, then open **Billing** and add a payment method
   or prepaid credits. Set a spending limit.
3. Open **API Keys**, click **Create API key**, give it a name and copy it.
4. Paste into **Settings → Grok → API key → Save**.

Note: xAI's web search runs through their Responses API server-side tools.
If a Grok call fails with a permissions error mentioning tools, check in
the xAI console that the key is allowed to use the Responses API and tools.

## Model IDs

Settings holds three model IDs per provider (Best, Balanced, Cheap). The
app ships with these defaults, current as of September 2026; **they will go
stale**, and every one is editable in Settings:

| Provider | Best | Balanced | Cheap |
|---|---|---|---|
| Claude | `claude-opus-5` | `claude-sonnet-5` | `claude-haiku-4-5-20251001` |
| ChatGPT | `gpt-5.6-sol` | `gpt-5.6-terra` | `gpt-5.6-luna` |
| Gemini | `gemini-3.1-pro` | `gemini-3.8-flash` | `gemini-3.1-flash-lite` |
| Grok | `grok-4.6` | `grok-4.5` | `grok-4.3` |

If a call fails with "model not found" or similar, open the provider's
model list, copy the exact ID and paste it into Settings:

- Anthropic: <https://docs.anthropic.com/en/docs/about-models/overview>
- OpenAI: <https://platform.openai.com/docs/models>
- Google: <https://ai.google.dev/gemini-api/docs/models>
- xAI: <https://docs.x.ai/developers/models>

## Using it

1. **New question** → type the question. Tap the sliders icon to set, for
   this question only: which providers take part, the tier for each, who
   judges, how many debate rounds (0 to 3), whether to stop early once all
   models report agreement, and whether web search is on.
2. Ask. The card shows which stage is running and which models have
   finished. You can cancel with the stop icon.
3. The unified answer appears with a one-line summary (judge, model count,
   rounds, whether agreement was reached, tokens used). **Details** expands
   every model's initial answer, each round's critique and revised answer,
   the AGREE/DISAGREE verdict each gave, token counts, timings and the web
   sources each one cited.
4. Type again in the same thread for a follow-up; previous questions and
   unified answers are sent along as context.

### What "consensus" means here

The models cannot verify each other, and a critique round can push them
toward whichever answer sounds most confident, including a shared wrong
one. So the judge is instructed to prefer claims that are backed by cited
sources or held by several models, and to list the disagreements that
remain rather than hide them. Read the "Where the models disagreed" section
before relying on the answer for anything that matters.

### Cost

Each question makes: one call per provider, plus one call per provider per
debate round, plus one judge call. With four providers and one round that
is nine calls. Rough cost at Balanced tier with web search on is in the
range of $0.10 to $0.30 per question; Best tier and extra rounds multiply
that. The Details panel shows the token counts for every call so you can
see where the cost goes.

## Getting a build

Every push to branch `claude/lucid-hypatia-8h37r1` runs
`.github/workflows/build-consensus-apk.yml`, which builds the debug APK on
GitHub's servers and publishes it under the release tag
`consensus-debug-latest`. Open the repo on GitHub → **Releases** →
`consensus-debug-latest` → download `consensus-debug.apk` → sideload it.

To build locally instead:

```
./gradlew :consensus:assembleDebug
```

The APK lands at `consensus/build/outputs/apk/debug/consensus-debug.apk`.

## Sideloading

```
adb install consensus/build/outputs/apk/debug/consensus-debug.apk
```

Or copy the APK to the phone and open it with a file manager; allow
installs from that app when prompted. The debug build is signed with the
fixed keystore checked into `consensus/debug.keystore`, so every CI build
installs as an update over the previous one. Consensus and Travel Benefits
have different package names and coexist on the same phone.

## Limitations

- **Written without a local Android build.** The sandbox this was
  authored in has no Android SDK, so compilation is verified only by the
  GitHub Actions workflow. It has not been run against the live APIs, so
  the first real question may surface request-format errors from a
  provider. Errors are shown verbatim in the card and in Details, which
  is enough to fix the client for that provider.
- **No streaming.** Each stage waits for every model to finish. With web
  search on and Best-tier models, a full run can take several minutes.
- **A run dies with the process.** If Android kills the app mid-run, the
  question is marked as interrupted on next launch. Keep the app in the
  foreground for long runs.
- **Minimal Markdown rendering** (headings, lists, bold, code). Tables
  render as raw text.

## Code map

- `domain/ConsensusOrchestrator.kt` – the fan-out / critique / judge pipeline.
- `domain/Prompts.kt` – the system prompts for each stage.
- `data/remote/` – one small OkHttp client per API format: Anthropic
  Messages, OpenAI-style Responses (OpenAI and xAI), Gemini generateContent.
- `data/repository/ConversationRepository.kt` – threads, exchanges and the
  application-scoped runner.
- `ui/thread/` – the conversation screen and per-question options sheet.
- `ui/settings/` – keys, model IDs and defaults.
