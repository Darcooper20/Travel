# Plaid backend for Travel Benefits

A ~150-line Cloudflare Worker that keeps your Plaid `client_id`/`secret` and
each linked bank's `access_token` off the phone. The Android app only ever
talks to this worker, authenticated with a shared bearer token you choose.
Cloudflare's free tier is more than enough for one household.

## One-time setup

1. **Plaid account.** Sign up at dashboard.plaid.com. New US/CA developers get
   a Trial plan (auto-approved, up to 10 linked items, Transactions included).
   Copy your `client_id` and the **production** secret (or sandbox while
   testing).
2. **Register the Android package** in the Plaid dashboard under
   *Team Settings → API → Allowed Android package names*:
   `com.travelbenefits.app.debug` (or `com.travelbenefits.app` for a release
   build). Required for OAuth banks such as Chase.
3. **Deploy the worker.**
   ```
   npm install -g wrangler
   cd plaid-backend
   wrangler login
   wrangler kv namespace create ITEMS      # paste the id into wrangler.toml
   wrangler secret put PLAID_CLIENT_ID
   wrangler secret put PLAID_SECRET
   wrangler secret put APP_TOKEN           # e.g. `openssl rand -hex 32`
   wrangler deploy                         # prints https://travel-benefits-plaid.<you>.workers.dev
   ```
4. **In the app**, Settings → *Bank & card transactions (Plaid)*: paste the
   worker URL and the APP_TOKEN, then **Link an account**. Map each linked
   card account to the matching wallet card (the app pre-matches by the last
   four digits).

## What the app does with it

- Syncs transactions (90 days on first link, incremental after) every 12 hours
  and on demand.
- Maps Plaid's personal-finance categories onto the app's spending
  categories and works out, per category and per month, what you earned on
  the card you used versus the best card in your wallet - the "you put
  $1,200 of dining on the wrong card" report.
- Uses real spend for welcome-bonus progress on mapped cards.

Plaid returns balances and transactions, not rewards points, so loyalty
balances still come from email or manual entry.

## Cost and privacy

- Trial plan: free up to 10 items. Beyond that, Plaid's pay-as-you-go
  pricing applies (Transactions is billed per connected account per month).
- Your transaction data flows Plaid → this worker → your phone. The worker
  stores only access tokens (in KV) and never logs transactions.
- Revoke at any time: remove the item in the app (calls `/item/remove`) or
  delete the worker.
