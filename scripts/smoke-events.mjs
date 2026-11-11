// End-to-end smoke test of the event pipeline THROUGH THE GATEWAY (works in DEV and STAGING-LIKE):
//   recharge -> payment confirm -> core outbox -> Kafka -> notification-service -> GET /notifications/me
// Usage: SMOKE_PASSWORD=<seed password> node scripts/smoke-events.mjs [--base http://localhost:18080] [--customer 4]
// Exit code 0 = pipeline healthy, 1 = failed (prints which step).
const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, all) => (v.startsWith('--') ? [...a, [v.slice(2), all[i + 1]]] : a), []));
const BASE = args.base ?? 'http://localhost:18080';
const N = String(args.customer ?? 4).padStart(5, '0');
const PASSWORD = process.env.SMOKE_PASSWORD;
if (!PASSWORD) throw new Error('Set SMOKE_PASSWORD');

const call = async (path, { token, body, cid } = {}) => {
  const res = await fetch(`${BASE}/api/v1${path}`, {
    method: body ? 'POST' : 'GET',
    headers: { ...(body && { 'Content-Type': 'application/json' }), ...(token && { Authorization: `Bearer ${token}` }), ...(cid && { 'X-Correlation-Id': cid }) },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : null, cid: res.headers.get('x-correlation-id') };
};
const fail = (msg) => { console.error('FAIL:', msg); process.exit(1); };
const rnd = () => Math.random().toString(16).slice(2, 12);

const login = await call('/auth/login', { body: { email: `cust${N}@connectone.test`, password: PASSWORD } });
if (login.status !== 200) fail(`login -> ${login.status}`);
const token = login.json.accessToken;
const before = new Set((await call('/notifications/me', { token })).json.map((n) => n.id));
const [nums, plans] = [(await call('/mobile-numbers', { token })).json, (await call('/recharge-plans')).json];
const cid = `smoke-${rnd()}`;
const recharge = await call('/recharges', { token, cid, body: { mobileNumberId: nums[0].id, planId: plans[0].id, idempotencyKey: `smoke-${rnd()}` } });
if (recharge.status !== 201) fail(`recharge -> ${recharge.status}`);
const pay = await call(`/payments/${recharge.json.paymentId}/confirm`, { token, cid, body: { callbackId: `cb-${rnd()}`, outcome: 'SUCCEEDED' } });
if (pay.status !== 200 || pay.json.status !== 'SUCCEEDED') fail(`confirm -> ${pay.status} ${pay.json?.status}`);
const t0 = Date.now();
let fresh = [];
while (Date.now() - t0 < 30000 && fresh.length < 2) {
  await new Promise((r) => setTimeout(r, 500));
  fresh = (await call('/notifications/me', { token })).json.filter((n) => !before.has(n.id));
}
if (fresh.length < 2) fail(`only ${fresh.length} notification(s) arrived within 30 s (is Kafka / notification-service up?)`);
console.log(`OK: payment ${recharge.json.paymentId} -> ${fresh.map((n) => n.eventType).sort().join(', ')} delivered via Kafka in ${((Date.now() - t0) / 1000).toFixed(1)} s (correlation id ${cid})`);
