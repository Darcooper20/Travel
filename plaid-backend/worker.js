/**
 * Travel Benefits - Plaid token exchange & proxy (Cloudflare Worker).
 *
 * Why this exists: Plaid API calls need your client_id + secret on every
 * request, and those must never ship inside an APK. This worker holds them,
 * stores each linked item's access_token in KV, and exposes five small
 * endpoints the app calls with a shared bearer token (APP_TOKEN) that you
 * choose. Deploy with `wrangler deploy`; see README.md in this folder.
 *
 * Endpoints (all JSON, all require `Authorization: Bearer <APP_TOKEN>`):
 *   POST /link-token                 -> { link_token }
 *   POST /exchange {public_token, institution_name?} -> { item_id, institution_name, accounts:[...] }
 *   GET  /items                      -> { items:[{item_id, institution_name, created_at}] }
 *   POST /transactions/sync {item_id, cursor?} -> { added, modified, removed, next_cursor, has_more, accounts }
 *   DELETE /items/:item_id           -> { removed: true }
 */

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

async function plaid(env, path, body) {
  const res = await fetch(`https://${env.PLAID_ENV || 'sandbox'}.plaid.com${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ client_id: env.PLAID_CLIENT_ID, secret: env.PLAID_SECRET, ...body }),
  });
  const data = await res.json();
  if (!res.ok) {
    const err = new Error(data.error_message || `Plaid ${path} failed`);
    err.status = res.status;
    err.plaid = data;
    throw err;
  }
  return data;
}

const pickAccount = (a) => ({
  account_id: a.account_id,
  name: a.name,
  official_name: a.official_name,
  mask: a.mask,
  type: a.type,
  subtype: a.subtype,
});

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const auth = request.headers.get('authorization') || '';
    if (!env.APP_TOKEN || auth !== `Bearer ${env.APP_TOKEN}`) return json({ error: 'unauthorized' }, 401);

    try {
      if (request.method === 'POST' && url.pathname === '/link-token') {
        const data = await plaid(env, '/link/token/create', {
          user: { client_user_id: env.CLIENT_USER_ID || 'owner' },
          client_name: 'Travel Benefits',
          products: ['transactions'],
          transactions: { days_requested: Number(env.DAYS_REQUESTED || 90) },
          country_codes: [env.COUNTRY_CODE || 'US'],
          language: 'en',
          android_package_name: env.ANDROID_PACKAGE_NAME,
        });
        return json({ link_token: data.link_token });
      }

      if (request.method === 'POST' && url.pathname === '/exchange') {
        const body = await request.json();
        const ex = await plaid(env, '/item/public_token/exchange', { public_token: body.public_token });
        const accounts = await plaid(env, '/accounts/get', { access_token: ex.access_token });
        let institution_name = body.institution_name || null;
        if (!institution_name && accounts.item && accounts.item.institution_id) {
          try {
            const inst = await plaid(env, '/institutions/get_by_id', {
              institution_id: accounts.item.institution_id,
              country_codes: [env.COUNTRY_CODE || 'US'],
            });
            institution_name = inst.institution && inst.institution.name;
          } catch (_) {
            /* name is cosmetic */
          }
        }
        const record = { access_token: ex.access_token, institution_name, created_at: Date.now() };
        await env.ITEMS.put(ex.item_id, JSON.stringify(record));
        return json({ item_id: ex.item_id, institution_name, accounts: accounts.accounts.map(pickAccount) });
      }

      if (request.method === 'GET' && url.pathname === '/items') {
        const list = await env.ITEMS.list();
        const items = [];
        for (const key of list.keys) {
          const rec = JSON.parse((await env.ITEMS.get(key.name)) || '{}');
          items.push({ item_id: key.name, institution_name: rec.institution_name || null, created_at: rec.created_at || null });
        }
        return json({ items });
      }

      if (request.method === 'POST' && url.pathname === '/transactions/sync') {
        const body = await request.json();
        const rec = JSON.parse((await env.ITEMS.get(body.item_id)) || 'null');
        if (!rec) return json({ error: 'unknown item' }, 404);
        const req = { access_token: rec.access_token, count: 500, options: { include_personal_finance_category: true } };
        if (body.cursor) req.cursor = body.cursor;
        const data = await plaid(env, '/transactions/sync', req);
        const accounts = await plaid(env, '/accounts/get', { access_token: rec.access_token });
        const slim = (t) => ({
          transaction_id: t.transaction_id,
          account_id: t.account_id,
          amount: t.amount,
          date: t.authorized_date || t.date,
          name: t.name,
          merchant_name: t.merchant_name,
          pending: !!t.pending,
          pfc_primary: t.personal_finance_category && t.personal_finance_category.primary,
          pfc_detailed: t.personal_finance_category && t.personal_finance_category.detailed,
        });
        return json({
          added: (data.added || []).map(slim),
          modified: (data.modified || []).map(slim),
          removed: (data.removed || []).map((r) => r.transaction_id),
          next_cursor: data.next_cursor,
          has_more: !!data.has_more,
          accounts: accounts.accounts.map(pickAccount),
        });
      }

      if (request.method === 'DELETE' && url.pathname.startsWith('/items/')) {
        const itemId = decodeURIComponent(url.pathname.slice('/items/'.length));
        const rec = JSON.parse((await env.ITEMS.get(itemId)) || 'null');
        if (rec) {
          try {
            await plaid(env, '/item/remove', { access_token: rec.access_token });
          } catch (_) {
            /* already gone at Plaid - still forget it locally */
          }
          await env.ITEMS.delete(itemId);
        }
        return json({ removed: true });
      }

      return json({ error: 'not found' }, 404);
    } catch (e) {
      return json({ error: e.message || 'error', plaid: e.plaid || null }, e.status || 500);
    }
  },
};
