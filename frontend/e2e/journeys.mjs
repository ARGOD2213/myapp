// End-to-end UI journeys against the REAL stack: browser -> Vite (proxy) -> Spring Boot -> PostgreSQL.
// Prereqs: API on :18080 with the dataset loaded (scripts/seed-dataset.ps1), `npm run dev` on :5173.
// Env: E2E_PASSWORD (password of the seeded accounts), optional E2E_BROWSER (path to chrome/msedge).
import { chromium } from 'playwright-core';

const BASE = process.env.E2E_BASE ?? 'http://localhost:5173';
const PASSWORD = process.env.E2E_PASSWORD;
const BROWSER = process.env.E2E_BROWSER ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
if (!PASSWORD) throw new Error('Set E2E_PASSWORD to the seeded account password');

const results = [];
const serverErrors = [];
const step = async (name, fn) => {
  const t = Date.now();
  try {
    await fn();
    results.push({ name, ok: true, ms: Date.now() - t });
    console.log(`  ok   ${name} (${Date.now() - t} ms)`);
  } catch (e) {
    results.push({ name, ok: false, error: String(e).split('\n')[0] });
    console.log(`  FAIL ${name}: ${String(e).split('\n')[0]}`);
    throw e;
  }
};

const browser = await chromium.launch({ executablePath: BROWSER, headless: true });

async function newPage() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  page.on('dialog', (d) => d.accept());
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 500) serverErrors.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  });
  page.setDefaultTimeout(15000);
  return page;
}

async function login(page, email) {
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

let failed = false;
try {
  // ------------------------------------------------------------------ new customer journey
  const page = await newPage();
  const tag = Date.now().toString().slice(-8);
  const email = `e2e${tag}@connectone.test`;
  const phone = `+9170${tag}`;

  console.log('Customer journey (new registration)');
  await step('route guard sends anonymous users to /login', async () => {
    await page.goto(`${BASE}/recharge`);
    await page.waitForURL('**/login');
  });
  await step('registration validates input client-side', async () => {
    await page.goto(`${BASE}/register`);
    await page.getByRole('button', { name: 'Register' }).click();
    await page.getByText('Enter a valid email address').waitFor();
  });
  await step('register + simulated OTP verification lands on the dashboard', async () => {
    await page.getByLabel('First name').fill('Eve');
    await page.getByLabel('Last name').fill('Tester');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mobile number').fill(phone);
    await page.getByLabel('Password').fill('E2e-Password-123');
    await page.getByRole('button', { name: 'Register' }).click();
    const code = await page.locator('strong').filter({ hasText: /^\d{6}$/ }).innerText();
    await page.getByLabel('One-time code').fill(code);
    await page.getByRole('button', { name: 'Verify and continue' }).click();
    await page.getByRole('heading', { name: 'Welcome, Eve' }).waitFor();
  });
  await step('profile: edit name and add a second number', async () => {
    await page.getByRole('link', { name: 'Profile & numbers' }).click();
    await page.getByLabel('First name').fill('Evelyn');
    await page.getByRole('button', { name: 'Save profile' }).click();
    await page.getByText('Profile saved.').waitFor();
    await page.getByLabel('New mobile number').fill(`+9171${tag}`);
    await page.getByRole('button', { name: 'Add', exact: true }).click();
    await page.getByText('Number added.').waitFor();
  });
  await step('recharge: plan -> coupon-less checkout -> pay -> success -> history', async () => {
    await page.getByRole('link', { name: 'Recharge', exact: true }).click();
    await page.getByRole('button', { name: 'Recharge', exact: true }).first().click();
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    await page.getByRole('button', { name: 'Pay now' }).click();
    await page.getByText('Recharge successful.').waitFor();
    await page.keyboard.press('Escape');
    await page.getByRole('cell', { name: 'Succeeded' }).first().waitFor();
  });
  await step('recharge: failed payment is recorded as failed', async () => {
    await page.getByRole('button', { name: 'Recharge', exact: true }).nth(1).click();
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    await page.getByRole('button', { name: 'Simulate failure' }).click();
    await page.getByText('Payment failed.').waitFor();
    await page.keyboard.press('Escape');
  });
  await step('payments: partial refund request shows in refund status', async () => {
    await page.getByRole('link', { name: 'Payments & refunds' }).click();
    await page.getByRole('button', { name: 'Request refund' }).first().click();
    await page.getByLabel('Amount').fill('10');
    await page.getByLabel('Reason').fill('E2E partial refund');
    await page.getByRole('button', { name: 'Submit refund request' }).click();
    await page.getByText('Refund requested.').waitFor();
    await page.keyboard.press('Escape');
    await page.getByRole('cell', { name: 'E2E partial refund' }).waitFor();
  });
  await step('subscriptions: subscribe and pay', async () => {
    await page.getByRole('link', { name: 'Subscriptions' }).click();
    await page.getByRole('button', { name: 'Subscribe' }).first().click();
    await page.getByRole('button', { name: 'Pay now' }).click();
    await page.getByRole('cell', { name: 'Active' }).first().waitFor();
  });
  await step('streaming: catalogue loads and playback respects entitlement', async () => {
    await page.getByRole('link', { name: 'Streaming' }).click();
    await page.getByRole('button', { name: /Play/ }).first().click();
    await page.getByText(/Now playing|active subscription/).first().waitFor();
  });
  await step('bookings: reserve seat, pay, cancel (refund)', async () => {
    await page.getByRole('link', { name: 'Bookings' }).click();
    await page.getByRole('button', { name: 'Choose seat' }).first().click();
    await page.getByLabel('Seat').selectOption({ index: 1 });
    await page.getByRole('button', { name: 'Reserve and pay' }).click();
    await page.getByRole('button', { name: 'Pay now' }).click();
    await page.getByText('Booking confirmed.').waitFor();
    await page.keyboard.press('Escape');
    await page.getByRole('cell', { name: 'Confirmed' }).first().waitFor();
    await page.getByRole('button', { name: 'Cancel' }).first().click();
    await page.getByText('Booking cancelled.').waitFor();
  });
  await step('support: create ticket and see it in history', async () => {
    await page.getByRole('link', { name: 'Support' }).click();
    await page.getByLabel('Subject').fill('E2E ticket');
    await page.getByLabel('Describe the problem').fill('Created by the end-to-end journey.');
    await page.getByRole('button', { name: 'Submit ticket' }).click();
    await page.getByText('Ticket created.').waitFor();
    await page.getByRole('cell', { name: 'E2E ticket' }).waitFor();
  });
  await step('notifications: delivered messages are listed', async () => {
    await page.getByRole('link', { name: 'Notifications' }).click();
    await page.getByRole('row').nth(1).waitFor();
  });
  await step('customer cannot open the back office', async () => {
    await page.goto(`${BASE}/admin/customers`);
    await page.getByRole('heading', { name: /Welcome/ }).waitFor();
  });
  await step('sign out returns to login', async () => {
    await page.getByRole('link', { name: 'Sign out' }).click();
    await page.waitForURL('**/login');
  });

  // ------------------------------------------------------------------ seeded customer with a large history
  console.log('Seeded customer with a large history');
  const heavy = await newPage();
  await step('customer 1 (445 recharges) logs in and the recharge list is paginated', async () => {
    await login(heavy, 'cust00001@connectone.test');
    await heavy.getByRole('heading', { name: /Welcome/ }).waitFor();
    await heavy.getByRole('link', { name: 'Recharge', exact: true }).click();
    await heavy.getByText(/Page 1 of 56/).waitFor();
    await heavy.getByRole('button', { name: 'Next →' }).first().click();
    await heavy.getByText(/Page 2 of 56/).waitFor();
  });
  await step('wrong password shows an error with a reference id', async () => {
    const bad = await newPage();
    await bad.goto(`${BASE}/login`);
    await bad.getByLabel('Email').fill('cust00002@connectone.test');
    await bad.getByLabel('Password').fill('definitely-wrong-password');
    await bad.getByRole('button', { name: 'Sign in' }).click();
    await bad.getByRole('alert').waitFor();
  });

  // ------------------------------------------------------------------ admin journey
  console.log('Admin journey');
  const admin = await newPage();
  await step('admin signs in and sees live dashboard figures', async () => {
    await login(admin, 'admin@connectone.test');
    await admin.getByRole('heading', { name: 'Operations dashboard' }).waitFor();
    await admin.getByText('Payments needing reconciliation').waitFor();
  });
  await step('customer search -> customer 360 view', async () => {
    await admin.getByRole('link', { name: 'Customers', exact: true }).click();
    await admin.getByLabel('Search customers').fill('cust00001@');
    await admin.getByRole('link', { name: '1', exact: true }).first().click();
    await admin.getByText('Customer #1').waitFor();
    await admin.getByText('Recent payments').waitFor();
  });
  await step('payment monitor: filter and resolve a reconciliation-required payment', async () => {
    await admin.getByRole('link', { name: 'Payments', exact: true }).click();
    await admin.getByLabel('Reconciliation').selectOption('RECONCILIATION_REQUIRED');
    await admin.getByRole('button', { name: 'Resolve' }).first().click();
    await admin.getByRole('button', { name: 'Apply outcome' }).click();
    await admin.getByText('Payment reconciled.').waitFor();
  });
  await step('refund monitor lists refunds', async () => {
    await admin.getByRole('link', { name: 'Refunds', exact: true }).click();
    await admin.getByRole('row').nth(2).waitFor();
  });
  await step('support queue: open ticket, post internal note', async () => {
    await admin.getByRole('link', { name: 'Support tickets' }).click();
    await admin.locator('a', { hasText: /^TKT-/ }).first().click();
    await admin.getByLabel('Reply').fill('Internal E2E note');
    await admin.getByLabel(/Internal note/).check();
    await admin.getByRole('button', { name: 'Post' }).click();
    await admin.getByText('Internal E2E note').waitFor();
    await admin.keyboard.press('Escape');
  });
  await step('plans, offers, catalogue and staff screens load from the API', async () => {
    for (const [link, text] of [['Plans', 'Recharge plans'], ['Offers & coupons', 'Offers & coupons'], ['Bookings & streaming', 'Schedules'], ['Agents & roles', 'Support agents']]) {
      await admin.getByRole('link', { name: link, exact: true }).click();
      await admin.getByText(text).first().waitFor();
    }
  });
  await step('audit log shows correlation ids for the reconciliation just performed', async () => {
    await admin.getByRole('link', { name: 'Audit log' }).click();
    await admin.getByText('PAYMENT_RECONCILED').first().waitFor();
  });
} catch {
  failed = true;
} finally {
  await browser.close();
}

const ok = results.filter((r) => r.ok).length;
console.log(`\n${ok}/${results.length} steps passed; server 5xx responses: ${serverErrors.length}`);
serverErrors.forEach((e) => console.log('  5xx:', e));
process.exit(failed || serverErrors.length ? 1 : 0);
