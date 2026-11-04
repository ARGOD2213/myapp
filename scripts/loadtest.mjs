// Minimal dependency-free load test for the ConnectOne API (Node 22).
// Usage: node scripts/loadtest.mjs [--base http://localhost:18080] [--users 10] [--seconds 10]
// Env:   LOADTEST_PASSWORD = password of the seeded accounts (CONNECTONE_SEED_PASSWORD value)
// Each virtual user logs in once, then loops over a weighted mix of read endpoints for the given duration.
const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, all) => (v.startsWith('--') ? [...a, [v.slice(2), all[i + 1]]] : a), []));
const BASE = args.base ?? 'http://localhost:18080';
const USERS = Number(args.users ?? 10);
const SECONDS = Number(args.seconds ?? 10);
const PASSWORD = process.env.LOADTEST_PASSWORD;
if (!PASSWORD) throw new Error('Set LOADTEST_PASSWORD');

const post = (path, body, token) =>
  fetch(`${BASE}/api/v1${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) }, body: JSON.stringify(body) });

const scenarios = [
  { name: 'GET /recharge-plans (public, cacheable)', weight: 3, auth: false, call: () => fetch(`${BASE}/api/v1/recharge-plans`) },
  { name: 'GET /customers/me', weight: 2, call: (t) => get('/customers/me', t) },
  { name: 'GET /recharges/me?size=10', weight: 3, call: (t) => get('/recharges/me?size=10', t) },
  { name: 'GET /payments/me (unpaged)', weight: 2, call: (t) => get('/payments/me', t) },
  { name: 'GET /notifications/me', weight: 2, call: (t) => get('/notifications/me', t) },
  { name: 'GET /bookings/catalogue', weight: 1, call: (t) => get('/bookings/catalogue', t) },
];
const adminScenarios = [
  { name: 'ADMIN GET /admin/payments?status=FAILED', weight: 1, call: (t) => get('/admin/payments?status=FAILED&size=20', t) },
  { name: 'ADMIN GET /customers?q=aarav', weight: 1, call: (t) => get('/customers?q=aarav&size=20', t) },
  { name: 'ADMIN GET /admin/dashboard', weight: 1, call: (t) => get('/admin/dashboard', t) },
];
const get = (path, token) => fetch(`${BASE}/api/v1${path}`, { headers: { Authorization: `Bearer ${token}` } });

const stats = new Map(); // name -> { ms: number[], errors: number }
const record = (name, ms, ok) => {
  const s = stats.get(name) ?? { ms: [], errors: 0 };
  s.ms.push(ms);
  if (!ok) s.errors++;
  stats.set(name, s);
};

async function login(email) {
  const t0 = performance.now();
  const res = await post('/auth/login', { email, password: PASSWORD });
  record('POST /auth/login (bcrypt)', performance.now() - t0, res.ok);
  return (await res.json()).accessToken;
}

async function user(i, deadline) {
  const admin = i === 0;
  const email = admin ? 'admin@connectone.test' : `cust${String(1 + (i % 50)).padStart(5, '0')}@connectone.test`;
  const token = await login(email);
  const pool = admin ? adminScenarios : scenarios;
  const bag = pool.flatMap((s) => Array(s.weight).fill(s));
  while (performance.now() < deadline) {
    const s = bag[Math.floor(Math.random() * bag.length)];
    const t0 = performance.now();
    try {
      const res = await s.call(token);
      await res.arrayBuffer();
      record(s.name, performance.now() - t0, res.ok);
    } catch {
      record(s.name, performance.now() - t0, false);
    }
  }
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
const started = performance.now();
await Promise.all(Array.from({ length: USERS }, (_, i) => user(i, started + SECONDS * 1000)));
const elapsed = (performance.now() - started) / 1000;

console.log(`\nConnectOne load test: ${USERS} virtual users, ${SECONDS}s, base ${BASE}\n`);
console.log('endpoint'.padEnd(46), 'count'.padStart(6), 'err'.padStart(4), 'p50'.padStart(7), 'p95'.padStart(7), 'max'.padStart(7), '(ms)');
let total = 0;
for (const [name, s] of [...stats].sort()) {
  const sorted = [...s.ms].sort((a, b) => a - b);
  total += sorted.length;
  console.log(name.padEnd(46), String(sorted.length).padStart(6), String(s.errors).padStart(4), pct(sorted, 50).toFixed(0).padStart(7), pct(sorted, 95).toFixed(0).padStart(7), sorted.at(-1).toFixed(0).padStart(7));
}
console.log(`\n${total} requests in ${elapsed.toFixed(1)}s = ${(total / elapsed).toFixed(1)} req/s`);
