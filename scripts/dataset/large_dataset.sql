-- ConnectOne large synthetic engineering dataset (deterministic, set-based).
-- Run only through scripts/seed-dataset.ps1 (it enforces the target database).
-- Variables: cust (customer count, default 10000), base (anchor date), SEED_PW env (synthetic account password).
-- All data is synthetic. Every row derives from md5-based hashes of its id, so the same (cust, base)
-- always generates the same data set.
\set ON_ERROR_STOP on
SET client_min_messages = warning;
\if :{?cust}
\else
\set cust 10000
\endif
\if :{?base}
\else
SELECT to_char(date_trunc('day', now()), 'YYYY-MM-DD') AS base \gset
\endif
\set pw `echo "$SEED_PW"`
\echo Generating dataset: customers=:cust base=:base

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE FUNCTION pg_temp.u(i bigint, salt text) RETURNS double precision LANGUAGE sql IMMUTABLE AS
$$ SELECT (('x' || substr(md5(salt || ':' || i::text), 1, 8))::bit(32)::bigint)::double precision / 4294967296.0 $$;

-- skewed customer pick: low ids are "heavy" users, the tail of the id range never transacts
CREATE FUNCTION pg_temp.skew(i bigint, salt text, n bigint, expo double precision) RETURNS bigint LANGUAGE sql IMMUTABLE AS
$$ SELECT 1 + floor((n * 0.9) * power(pg_temp.u(i, salt), expo))::bigint $$;

CREATE FUNCTION pg_temp.ts(base timestamp, i bigint, salt text, max_days double precision) RETURNS timestamp LANGUAGE sql IMMUTABLE AS
$$ SELECT base - (pg_temp.u(i, salt) * max_days * 86400)::bigint * interval '1 second' $$;

BEGIN;

-- ---------------------------------------------------------------- reset (dataset-owned data only)
TRUNCATE app_user, offer, payment_callback, access_token_revocation RESTART IDENTITY CASCADE;
DELETE FROM subscription_content_entitlement WHERE plan_id IN (SELECT id FROM subscription_plan WHERE plan_code LIKE 'DS-%');
DELETE FROM subscription_plan WHERE plan_code LIKE 'DS-%';
DELETE FROM recharge_plan WHERE plan_code LIKE 'DS-%';
DELETE FROM streaming_content WHERE content_code LIKE 'DS-%';
DELETE FROM booking_catalog_item WHERE item_code LIKE 'DS-%';

-- ---------------------------------------------------------------- catalogues
INSERT INTO recharge_plan (plan_code, name, price, validity_days, data_allowance_mb, voice_minutes, active)
SELECT 'DS-RCH-' || lpad(g::text, 3, '0'),
       'Plan ' || (ARRAY['Lite','Plus','Max','Pro','Value','Super'])[1 + g % 6] || ' ' || (49 + g * 20),
       (49 + g * 20)::numeric, (ARRAY[1,7,14,28,56,84,365])[1 + g % 7],
       (500 + g * 300), (g % 4) * 500, g <= 12
FROM generate_series(1, 14) g;

INSERT INTO subscription_plan (plan_code, name, price, billing_period_days, active) VALUES
 ('DS-SUB-BASIC',  'Stream Basic',   99.00,   30, TRUE),
 ('DS-SUB-PREM',   'Stream Premium', 299.00,  30, TRUE),
 ('DS-SUB-FAMILY', 'Stream Family',  499.00,  90, TRUE),
 ('DS-SUB-SPORTS', 'Sports Pass',    149.00,  30, TRUE),
 ('DS-SUB-LEGACY', 'Legacy Bundle',  249.00,  30, FALSE);

INSERT INTO streaming_category (category_code, name) VALUES
 ('DOCS', 'Documentaries'), ('SPORTS', 'Sports'), ('KIDS', 'Kids') ON CONFLICT DO NOTHING;

INSERT INTO streaming_content (category_id, content_code, title, description, content_type, rating, duration_minutes, playback_url, active)
SELECT (SELECT id FROM streaming_category ORDER BY id OFFSET (g % 6) LIMIT 1),
       'DS-CONTENT-' || lpad(g::text, 3, '0'), 'Synthetic Title ' || g,
       'Synthetic catalogue metadata; playback is not hosted by ConnectOne.',
       (ARRAY['MOVIE','SERIES','CLIP'])[1 + g % 3], (ARRAY['GENERAL','PG','ADULT'])[1 + g % 3],
       20 + (g * 7) % 140, 'https://media.example.test/ds/' || g, g % 17 <> 0
FROM generate_series(1, 40) g;

INSERT INTO subscription_content_entitlement (plan_id, category_id)
SELECT p.id, c.id FROM subscription_plan p CROSS JOIN streaming_category c
WHERE p.plan_code IN ('DS-SUB-BASIC','DS-SUB-PREM','DS-SUB-FAMILY','DS-SUB-LEGACY')
  AND (p.plan_code <> 'DS-SUB-BASIC' OR c.category_code IN ('MOVIES','SERIES'))
ON CONFLICT DO NOTHING;
INSERT INTO subscription_content_entitlement (plan_id, category_id)
SELECT p.id, c.id FROM subscription_plan p CROSS JOIN streaming_category c
WHERE p.plan_code = 'DS-SUB-SPORTS' AND c.category_code = 'SPORTS' ON CONFLICT DO NOTHING;

INSERT INTO booking_catalog_item (item_type, item_code, title, location, starts_at, capacity, available, category, price, description)
SELECT t.item_type, 'DS-' || t.item_type || '-' || lpad(k::text, 3, '0'),
       t.label || ' ' || k, (ARRAY['Mumbai','Delhi','Bengaluru','Chennai','Hyderabad','Pune'])[1 + k % 6],
       (:'base'::timestamp + (((k * 37 + t.shift) % 540) - 400) * interval '1 day' + (k % 12) * interval '1 hour'),
       t.cap, TRUE, t.item_type, t.price + (k % 5) * 25, 'Synthetic schedule entry'
FROM (VALUES ('BUS','Express',40,250.00,0), ('MOVIE','Screening',80,180.00,100), ('EVENT','Live Show',200,500.00,200)) AS t(item_type, label, cap, price, shift)
CROSS JOIN generate_series(1, 100) k;

-- ---------------------------------------------------------------- identities
CREATE TEMP TABLE pwd AS SELECT crypt(:'pw', gen_salt('bf', 10)) AS h;

INSERT INTO app_user (id, email, password_hash, account_status, created_at, updated_at)
SELECT 1, 'admin@connectone.test', h, 'ACTIVE', :'base'::timestamp - interval '800 days', :'base'::timestamp - interval '800 days' FROM pwd;
INSERT INTO app_user (id, email, password_hash, account_status, created_at, updated_at)
SELECT 1 + g, 'agent' || lpad(g::text, 2, '0') || '@connectone.test', h, 'ACTIVE',
       :'base'::timestamp - interval '700 days', :'base'::timestamp - interval '700 days'
FROM pwd CROSS JOIN generate_series(1, 25) g;
INSERT INTO app_user (id, email, password_hash, account_status, locked_until, failed_login_attempts, created_at, updated_at)
SELECT 1000 + c, 'cust' || lpad(c::text, 5, '0') || '@connectone.test', h,
       CASE WHEN c % 500 = 0 THEN 'LOCKED' WHEN c % 300 = 0 THEN 'PENDING' WHEN c % 997 = 0 THEN 'DISABLED' ELSE 'ACTIVE' END,
       CASE WHEN c % 500 = 0 THEN :'base'::timestamp + interval '1 day' END,
       CASE WHEN c % 500 = 0 THEN 5 ELSE 0 END,
       pg_temp.ts(:'base'::timestamp, c, 'ucreated', 900), pg_temp.ts(:'base'::timestamp, c, 'ucreated', 900)
FROM pwd CROSS JOIN generate_series(1, :cust) c;

INSERT INTO user_role (user_id, role_id)
SELECT 1, id FROM app_role WHERE role_code = 'ADMIN';
INSERT INTO user_role (user_id, role_id)
SELECT 1 + g, r.id FROM generate_series(1, 25) g CROSS JOIN app_role r WHERE r.role_code = 'SUPPORT_AGENT';
INSERT INTO user_role (user_id, role_id)
SELECT 1000 + c, r.id FROM generate_series(1, :cust) c CROSS JOIN app_role r WHERE r.role_code = 'CUSTOMER';

INSERT INTO customer_profile (id, user_id, first_name, last_name, phone_number, account_status, created_at, updated_at)
SELECT c, 1000 + c,
       (ARRAY['Aarav','Vivaan','Aditya','Diya','Ananya','Ishaan','Kavya','Rohan','Meera','Arjun','Saanvi','Karan','Neha','Rahul','Priya','Siddharth','Tara','Vikram','Zoya','Nikhil'])[1 + (c * 7) % 20],
       (ARRAY['Sharma','Verma','Iyer','Reddy','Nair','Gupta','Khan','Patel','Singh','Das','Mehta','Joshi','Rao','Kulkarni','Bose'])[1 + (c * 11) % 15],
       '+91' || (9000000000 + c),
       CASE WHEN c % 100 = 0 THEN 'CLOSED' WHEN c % 33 = 0 THEN 'SUSPENDED' ELSE 'ACTIVE' END,
       pg_temp.ts(:'base'::timestamp, c, 'ucreated', 900), pg_temp.ts(:'base'::timestamp, c, 'ucreated', 900)
FROM generate_series(1, :cust) c;

INSERT INTO customer_address (customer_id, address_type, line1, city, region, postal_code, country_code)
SELECT c, 'HOME', (c % 200 + 1) || ' Synthetic Street', (ARRAY['Mumbai','Delhi','Bengaluru','Chennai','Hyderabad','Pune'])[1 + c % 6],
       'Region ' || (c % 12), lpad((400000 + c % 99999)::text, 6, '0'), 'IN'
FROM generate_series(1, :cust) c WHERE c % 5 < 3 ORDER BY c;

-- first numbers: mobile id = customer id; second numbers: id = cust + c/5 for every fifth customer
INSERT INTO mobile_number (id, customer_id, phone_number, status, is_primary, created_at)
SELECT c, c, '+91' || (9000000000 + c),
       CASE WHEN c % 100 = 0 THEN 'DISCONNECTED' WHEN c % 40 = 0 THEN 'SUSPENDED' ELSE 'ACTIVE' END, TRUE,
       pg_temp.ts(:'base'::timestamp, c, 'ucreated', 900)
FROM generate_series(1, :cust) c;
INSERT INTO mobile_number (id, customer_id, phone_number, status, is_primary, created_at)
SELECT :cust + c / 5, c, '+91' || (8000000000 + c), CASE WHEN c % 70 = 0 THEN 'SUSPENDED' ELSE 'ACTIVE' END, FALSE,
       pg_temp.ts(:'base'::timestamp, c, 'ucreated', 600)
FROM generate_series(5, :cust, 5) c;

-- ---------------------------------------------------------------- recharges
CREATE TEMP TABLE plan_pool AS
SELECT row_number() OVER (ORDER BY id) AS n, id, price, validity_days FROM recharge_plan WHERE active;

INSERT INTO recharge_transaction (id, customer_id, mobile_number_id, plan_id, amount, status, idempotency_key, created_at, updated_at, expires_at)
SELECT r, cu, CASE WHEN cu % 5 = 0 AND pg_temp.u(r, 'mob') < 0.5 THEN :cust + cu / 5 ELSE cu END,
       p.id, p.price, st, 'ds-rch-' || r, ca, ca + interval '3 seconds',
       CASE WHEN st = 'SUCCEEDED' THEN ca + p.validity_days * interval '1 day' END
FROM (
  SELECT r, pg_temp.skew(r, 'rcust', :cust, 2.2) AS cu,
         1 + (r * 13) % (SELECT count(*) FROM plan_pool) AS pn,
         CASE WHEN x < 0.80 THEN 'SUCCEEDED' WHEN x < 0.88 THEN 'FAILED' WHEN x < 0.90 THEN 'PENDING' WHEN x < 0.92 THEN 'PROCESSING'
              WHEN x < 0.94 THEN 'CANCELLED' WHEN x < 0.95 THEN 'EXPIRED' ELSE 'REVERSED' END AS st,
         CASE WHEN x >= 0.88 AND x < 0.92 THEN :'base'::timestamp - pg_temp.u(r, 'rrecent') * interval '3 hours'
              ELSE pg_temp.ts(:'base'::timestamp, r, 'rtime', 730) END AS ca
  FROM (SELECT r, pg_temp.u(r, 'rstat') AS x FROM generate_series(1, :cust * 3) r) s
) q JOIN plan_pool p ON p.n = q.pn;

-- ---------------------------------------------------------------- subscriptions
INSERT INTO customer_subscription (id, customer_id, plan_id, status, starts_at, expires_at, auto_renew, version, created_at)
SELECT s, cu, pl.id, st,
       CASE st WHEN 'PENDING' THEN :'base'::timestamp - interval '1 hour'
               WHEN 'ACTIVE' THEN :'base'::timestamp - (pg_temp.u(s, 'sact') * (pl.billing_period_days - 1)) * interval '1 day'
               WHEN 'RENEWAL_FAILED' THEN :'base'::timestamp - (pl.billing_period_days - 2 + pg_temp.u(s, 'sact') * 3) * interval '1 day'
               ELSE :'base'::timestamp - (1 + pg_temp.u(s, 'sold') * 500 + pl.billing_period_days) * interval '1 day' END,
       CASE st WHEN 'PENDING' THEN :'base'::timestamp - interval '1 hour' + pl.billing_period_days * interval '1 day'
               WHEN 'ACTIVE' THEN :'base'::timestamp - (pg_temp.u(s, 'sact') * (pl.billing_period_days - 1)) * interval '1 day' + pl.billing_period_days * interval '1 day'
               WHEN 'RENEWAL_FAILED' THEN :'base'::timestamp - (pl.billing_period_days - 2 + pg_temp.u(s, 'sact') * 3) * interval '1 day' + pl.billing_period_days * interval '1 day'
               ELSE :'base'::timestamp - (1 + pg_temp.u(s, 'sold') * 500) * interval '1 day' END,
       st IN ('ACTIVE', 'RENEWAL_FAILED') AND pg_temp.u(s, 'sauto') < 0.6, 0,
       pg_temp.ts(:'base'::timestamp, s, 'sold', 900)
FROM (
  SELECT s, pg_temp.skew(s, 'scust', :cust, 1.3) AS cu, 1 + (s * 5) % 5 AS pn,
         CASE WHEN x < 0.45 THEN 'ACTIVE' WHEN x < 0.75 THEN 'EXPIRED' WHEN x < 0.87 THEN 'CANCELLED' WHEN x < 0.92 THEN 'RENEWAL_FAILED' ELSE 'PENDING' END AS st
  FROM (SELECT s, pg_temp.u(s, 'sstat') AS x FROM generate_series(1, :cust * 6 / 5) s) z
) q JOIN (SELECT row_number() OVER (ORDER BY id) AS n, id, billing_period_days FROM subscription_plan WHERE plan_code LIKE 'DS-%') pl ON pl.n = q.pn;

-- ---------------------------------------------------------------- bookings
INSERT INTO booking_record (id, booking_code, customer_id, catalog_item_id, seat_or_slot, status, amount, version, idempotency_key, created_at, updated_at)
SELECT b, 'BK-' || lpad(b::text, 7, '0'), cu, i.id, ((b - 1) / 300 + 1)::text,
       CASE WHEN past THEN CASE WHEN x < 0.70 THEN 'CONFIRMED' WHEN x < 0.85 THEN 'CANCELLED' WHEN x < 0.93 THEN 'REFUNDED'
                                WHEN x < 0.96 THEN 'EXPIRED' WHEN x < 0.98 THEN 'REFUND_PENDING' ELSE 'PENDING' END
            ELSE CASE WHEN x < 0.60 THEN 'CONFIRMED' WHEN x < 0.75 THEN 'PENDING' WHEN x < 0.95 THEN 'CANCELLED' ELSE 'REFUNDED' END END,
       i.price, 0, 'ds-bk-' || b,
       CASE WHEN past THEN i.starts_at - (2 + pg_temp.u(b, 'bt') * 60) * interval '1 day'
            ELSE least(:'base'::timestamp - pg_temp.u(b, 'bt') * interval '30 days', i.starts_at - interval '1 hour') END,
       CASE WHEN past THEN i.starts_at - (1 + pg_temp.u(b, 'bt') * 60) * interval '1 day'
            ELSE least(:'base'::timestamp - pg_temp.u(b, 'bt') * interval '30 days', i.starts_at - interval '1 hour') END
FROM (SELECT b, pg_temp.skew(b, 'bcust', :cust, 1.5) AS cu, pg_temp.u(b, 'bstat') AS x, 1 + (b * 7) % 300 AS inum
      FROM generate_series(1, :cust * 4 / 5) b) q
JOIN (SELECT row_number() OVER (ORDER BY id) AS n, id, price, starts_at, starts_at < :'base'::timestamp AS past FROM booking_catalog_item WHERE item_code LIKE 'DS-%') i ON i.n = q.inum;

INSERT INTO booking_seat_claim (catalog_item_id, seat_or_slot, booking_id, claimed_at)
SELECT catalog_item_id, seat_or_slot, id, created_at FROM booking_record WHERE status IN ('PENDING', 'CONFIRMED', 'REFUND_PENDING') ORDER BY id;

-- ---------------------------------------------------------------- payments
-- recharge payments (1:1 with each recharge, ordered so ids are deterministic)
INSERT INTO payment_record (customer_id, recharge_id, amount, currency, status, idempotency_key, provider_reference, created_at, updated_at)
SELECT customer_id, id, amount, 'INR',
       CASE status WHEN 'SUCCEEDED' THEN 'SUCCEEDED' WHEN 'FAILED' THEN 'FAILED' WHEN 'PENDING' THEN 'INITIATED' WHEN 'PROCESSING' THEN 'PROCESSING'
                   WHEN 'CANCELLED' THEN 'CANCELLED' WHEN 'EXPIRED' THEN 'EXPIRED' ELSE 'REFUNDED' END,
       'recharge-ds-' || id,
       CASE WHEN status IN ('SUCCEEDED', 'FAILED', 'REVERSED') THEN 'SIM-' || upper(substr(md5('rp' || id), 1, 12)) END,
       created_at, updated_at
FROM recharge_transaction ORDER BY id;

-- subscription first payments
INSERT INTO payment_record (customer_id, subscription_id, amount, currency, status, idempotency_key, provider_reference, created_at, updated_at)
SELECT s.customer_id, s.id, p.price, 'INR',
       CASE s.status WHEN 'PENDING' THEN 'INITIATED' ELSE 'SUCCEEDED' END,
       'subscription-ds-' || s.id,
       CASE WHEN s.status <> 'PENDING' THEN 'SIM-' || upper(substr(md5('sp' || s.id), 1, 12)) END,
       s.starts_at - interval '1 minute' * (s.id % 60) - CASE WHEN s.status IN ('ACTIVE','RENEWAL_FAILED') AND s.id % 4 = 0 THEN p.billing_period_days * interval '1 day' ELSE interval '0' END,
       s.starts_at
FROM customer_subscription s JOIN subscription_plan p ON p.id = s.plan_id ORDER BY s.id;

-- subscription renewal payments (every 4th non-pending subscription, plus every failed renewal)
INSERT INTO payment_record (customer_id, subscription_id, amount, currency, status, idempotency_key, provider_reference, failure_reason, created_at, updated_at)
SELECT s.customer_id, s.id, p.price, 'INR',
       CASE s.status WHEN 'RENEWAL_FAILED' THEN 'FAILED' ELSE 'SUCCEEDED' END,
       'subscription-ds-renew-' || s.id,
       'SIM-' || upper(substr(md5('sr' || s.id), 1, 12)),
       CASE s.status WHEN 'RENEWAL_FAILED' THEN 'CARD_DECLINED' END,
       s.starts_at, s.starts_at
FROM customer_subscription s JOIN subscription_plan p ON p.id = s.plan_id
WHERE s.status IN ('ACTIVE', 'RENEWAL_FAILED', 'EXPIRED') AND (s.id % 4 = 0 OR s.status = 'RENEWAL_FAILED') ORDER BY s.id;

-- booking payments
INSERT INTO payment_record (customer_id, booking_id, amount, currency, status, idempotency_key, provider_reference, created_at, updated_at)
SELECT customer_id, id, amount, 'INR',
       CASE status WHEN 'CONFIRMED' THEN 'SUCCEEDED' WHEN 'CANCELLED' THEN 'FAILED' WHEN 'REFUNDED' THEN 'REFUNDED'
                   WHEN 'REFUND_PENDING' THEN 'REFUND_PENDING' WHEN 'EXPIRED' THEN 'EXPIRED' ELSE 'INITIATED' END,
       'booking-ds-' || id,
       CASE WHEN status IN ('CONFIRMED', 'CANCELLED', 'REFUNDED', 'REFUND_PENDING') THEN 'SIM-' || upper(substr(md5('bp' || id), 1, 12)) END,
       created_at, updated_at
FROM booking_record ORDER BY id;

UPDATE payment_record SET failure_reason = (ARRAY['CARD_DECLINED','INSUFFICIENT_FUNDS','PROVIDER_TIMEOUT','BANK_UNAVAILABLE'])[1 + (id % 4)]
WHERE status = 'FAILED' AND failure_reason IS NULL;

-- ---------------------------------------------------------------- controlled investigation scenarios (payments / recharges)
-- S1 duplicate payment attempts: some recharges received extra payment rows (one a second SUCCEEDED charge)
INSERT INTO payment_record (customer_id, recharge_id, amount, currency, status, idempotency_key, provider_reference, attempt_count, created_at, updated_at)
SELECT p.customer_id, p.recharge_id, p.amount, 'INR', 'SUCCEEDED', 'recharge-ds-dup-' || p.recharge_id,
       'SIM-' || upper(substr(md5('dup' || p.id), 1, 12)), 2, p.created_at + interval '40 seconds', p.created_at + interval '41 seconds'
FROM payment_record p WHERE p.recharge_id IS NOT NULL AND p.status = 'SUCCEEDED' AND p.recharge_id % 600 = 1 ORDER BY p.id;
INSERT INTO payment_record (customer_id, recharge_id, amount, currency, status, idempotency_key, attempt_count, failure_reason, created_at, updated_at)
SELECT p.customer_id, p.recharge_id, p.amount, 'INR', 'FAILED', 'recharge-ds-dupf-' || p.recharge_id, 3, 'DUPLICATE_SUBMISSION',
       p.created_at + interval '10 seconds', p.created_at + interval '11 seconds'
FROM payment_record p WHERE p.recharge_id IS NOT NULL AND p.status = 'SUCCEEDED' AND p.attempt_count = 1 AND p.recharge_id % 600 = 1 ORDER BY p.id;

-- S2 callbacks: provider reported SUCCEEDED but payment never left PROCESSING -> reconciliation required;
--    plus failed payments flagged for retry
UPDATE payment_record SET status = 'PROCESSING', reconciliation_state = 'RECONCILIATION_REQUIRED', provider_reference = NULL,
       created_at = :'base'::timestamp - interval '4 days' - (id % 100) * interval '1 hour', updated_at = created_at
WHERE id IN (SELECT p.id FROM payment_record p JOIN recharge_transaction r ON r.id = p.recharge_id
             WHERE r.status = 'SUCCEEDED' AND p.status = 'SUCCEEDED' AND p.attempt_count = 1 AND r.id % 500 = 7);
UPDATE recharge_transaction SET status = 'PROCESSING', expires_at = NULL
WHERE id IN (SELECT recharge_id FROM payment_record WHERE reconciliation_state = 'RECONCILIATION_REQUIRED');
INSERT INTO payment_callback (callback_id, payment_id, outcome, received_at)
SELECT 'ds-cb-' || id, id, 'SUCCEEDED', created_at + interval '5 minutes' FROM payment_record WHERE reconciliation_state = 'RECONCILIATION_REQUIRED' ORDER BY id;
UPDATE payment_record SET reconciliation_state = 'RETRY_REQUIRED', failure_reason = 'PROVIDER_TIMEOUT', attempt_count = 2
WHERE status = 'FAILED' AND recharge_id IS NOT NULL AND id % 300 = 5;

-- S3 inconsistent states: recharge SUCCEEDED but payment FAILED; payment SUCCEEDED but recharge PENDING
UPDATE payment_record SET status = 'FAILED', provider_reference = NULL, failure_reason = 'CARD_DECLINED'
WHERE id IN (SELECT p.id FROM payment_record p JOIN recharge_transaction r ON r.id = p.recharge_id
             WHERE r.status = 'SUCCEEDED' AND p.status = 'SUCCEEDED' AND p.attempt_count = 1 AND r.id % 1000 = 4);
UPDATE recharge_transaction SET status = 'PENDING', expires_at = NULL
WHERE id IN (SELECT r.id FROM recharge_transaction r JOIN payment_record p ON p.recharge_id = r.id
             WHERE r.status = 'SUCCEEDED' AND p.status = 'SUCCEEDED' AND p.attempt_count = 1 AND r.id % 1000 = 3);
-- subscription still PENDING although paid; subscription ACTIVE although its first payment failed
UPDATE customer_subscription SET status = 'PENDING' WHERE status = 'ACTIVE' AND id % 250 = 11;
UPDATE payment_record SET status = 'FAILED', provider_reference = NULL, failure_reason = 'CARD_DECLINED'
WHERE subscription_id IN (SELECT id FROM customer_subscription WHERE status = 'ACTIVE' AND id % 250 = 12)
  AND idempotency_key LIKE 'subscription-ds-%' AND idempotency_key NOT LIKE '%renew%';

-- S5 unusual volume: one customer fires hundreds of recharges inside a single day
UPDATE payment_record SET customer_id = LEAST(7777, :cust), created_at = :'base'::timestamp - interval '3 days' + (id % 1440) * interval '1 minute', updated_at = created_at
WHERE recharge_id IN (SELECT id FROM recharge_transaction WHERE id % 75 = 2 AND status IN ('SUCCEEDED', 'FAILED') AND customer_id <> LEAST(7777, :cust) ORDER BY id LIMIT 400);
UPDATE recharge_transaction r SET customer_id = LEAST(7777, :cust), mobile_number_id = LEAST(7777, :cust), created_at = p.created_at, updated_at = p.created_at,
       expires_at = CASE WHEN r.status = 'SUCCEEDED' THEN p.created_at + interval '28 days' END
FROM payment_record p WHERE p.recharge_id = r.id AND p.customer_id = LEAST(7777, :cust) AND r.customer_id <> LEAST(7777, :cust);

-- S8 booking edge cases
UPDATE payment_record SET status = 'SUCCEEDED', failure_reason = NULL, provider_reference = 'SIM-' || upper(substr(md5('edge' || id), 1, 12))
WHERE booking_id IN (SELECT id FROM booking_record WHERE status = 'CANCELLED' AND id % 150 = 9);        -- cancelled but money captured, never refunded
UPDATE booking_record SET status = 'CANCELLED', updated_at = created_at + interval '70 days'
WHERE id IN (SELECT b.id FROM booking_record b JOIN booking_catalog_item i ON i.id = b.catalog_item_id
             WHERE b.status = 'CONFIRMED' AND i.starts_at < :'base'::timestamp AND b.id % 120 = 5);     -- cancelled after the event started
DELETE FROM booking_seat_claim WHERE booking_id IN (SELECT id FROM booking_record WHERE status = 'CANCELLED');

-- ---------------------------------------------------------------- coupons / offers
INSERT INTO offer (offer_code, name, discount_percent, starts_at, ends_at, active)
SELECT 'DS-OFFER-' || g, (ARRAY['Welcome','Festive','Weekend','Loyalty','Winback','Flash'])[g] || ' Offer', (ARRAY[5,10,15,20,25,50])[g],
       :'base'::timestamp - (400 - g * 60) * interval '1 day', :'base'::timestamp + (g * 30 - 120) * interval '1 day', g <> 5
FROM generate_series(1, 6) g;
INSERT INTO coupon (coupon_code, offer_id, usage_limit, usage_count, active)
SELECT 'DS-CPN-' || lpad(g::text, 3, '0'), (SELECT id FROM offer ORDER BY id OFFSET (g % 6) LIMIT 1), 100 + (g % 5) * 400, 0, g % 9 <> 0
FROM generate_series(1, 20) g;
WITH picks AS (
  SELECT p.id AS payment_id, p.customer_id, 1 + (p.id % 20) AS cn
  FROM payment_record p WHERE p.recharge_id IS NOT NULL AND p.status = 'SUCCEEDED' AND p.id % 20 = 0 AND p.attempt_count = 1),
ranked AS (
  SELECT DISTINCT ON (cn, customer_id) payment_id, customer_id, cn FROM picks ORDER BY cn, customer_id, payment_id),
ins AS (
  INSERT INTO coupon_redemption (coupon_id, customer_id, redeemed_at, payment_id, discount_amount)
  SELECT c.id, r.customer_id, p.created_at, r.payment_id, round(p.amount * o.discount_percent / 100, 2)
  FROM ranked r JOIN (SELECT row_number() OVER (ORDER BY id) AS n, * FROM coupon) c ON c.n = r.cn
  JOIN offer o ON o.id = c.offer_id JOIN payment_record p ON p.id = r.payment_id
  RETURNING payment_id, coupon_id, discount_amount)
UPDATE payment_record p SET coupon_id = ins.coupon_id, discount_amount = ins.discount_amount, amount = p.amount - ins.discount_amount
FROM ins WHERE p.id = ins.payment_id AND ins.discount_amount < p.amount;
UPDATE coupon c SET usage_count = x.cnt, usage_limit = GREATEST(c.usage_limit, x.cnt)
FROM (SELECT coupon_id, count(*) AS cnt FROM coupon_redemption GROUP BY coupon_id) x WHERE x.coupon_id = c.id;

-- ---------------------------------------------------------------- refunds
-- full refunds (recharge REVERSED + booking REFUNDED)
INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key, created_at)
SELECT id, amount, 'SUCCEEDED', (ARRAY['Customer request','Duplicate charge','Service not delivered','Event cancelled'])[1 + id % 4],
       'refund-ds-' || id, updated_at + interval '1 day' + (id % 72) * interval '1 hour'
FROM payment_record WHERE status = 'REFUNDED' ORDER BY id;
-- pending refunds (every 7th booking stays refund-less: edge case)
INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key, created_at)
SELECT p.id, p.amount, 'PENDING', 'Booking cancelled by customer', 'refund-ds-' || p.id, p.updated_at + interval '1 hour'
FROM payment_record p JOIN booking_record b ON b.id = p.booking_id
WHERE p.status = 'REFUND_PENDING' AND b.id % 7 <> 0 ORDER BY p.id;
-- partial refunds on healthy recharge payments
INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key, created_at)
SELECT p.id, round((p.amount * (0.3 + pg_temp.u(p.id, 'pref') * 0.4))::numeric, 2), CASE WHEN p.id % 40 = 0 THEN 'FAILED' ELSE 'SUCCEEDED' END,
       'Partial refund: ' || (ARRAY['Data not credited','Plan mismatch','Goodwill'])[1 + p.id % 3], 'refund-ds-' || p.id || '-a', p.updated_at + interval '2 days'
FROM payment_record p JOIN recharge_transaction r ON r.id = p.recharge_id
WHERE p.status = 'SUCCEEDED' AND r.status = 'SUCCEEDED' AND p.attempt_count = 1 AND p.id % 25 = 0 ORDER BY p.id;
INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key, created_at)
SELECT p.id, round(p.amount * 0.2, 2), 'SUCCEEDED', 'Second partial refund', 'refund-ds-' || p.id || '-b', p.updated_at + interval '5 days'
FROM payment_record p JOIN refund_record f ON f.payment_id = p.id AND f.idempotency_key LIKE '%-a'
WHERE f.status = 'SUCCEEDED' AND f.amount + round(p.amount * 0.2, 2) <= p.amount AND p.id % 100 = 0 ORDER BY p.id;

-- ---------------------------------------------------------------- support
INSERT INTO support_ticket (id, ticket_code, customer_id, assigned_user_id, subject, description, priority, status, category, created_at, updated_at, closed_at, version)
SELECT t, 'TKT-' || lpad(t::text, 7, '0'), cu,
       CASE WHEN st = 'OPEN' AND t % 3 = 0 THEN NULL ELSE 2 + (t * 3) % 25 END,
       (ARRAY['Recharge not credited','Payment deducted twice','Cannot play content','Booking cancellation help','Refund status enquiry','Unable to login','Plan change request','Wrong amount charged'])[1 + t % 8],
       'Synthetic support request ' || t || '. Customer reports an issue that needs investigation.',
       (ARRAY['LOW','NORMAL','NORMAL','HIGH','URGENT'])[1 + t % 5], st,
       (ARRAY['PAYMENT','RECHARGE','STREAMING','BOOKING','ACCOUNT','GENERAL'])[1 + t % 6], ca,
       CASE WHEN st = 'OPEN' THEN ca ELSE ca + (1 + pg_temp.u(t, 'tup') * 20) * interval '1 day' END,
       CASE WHEN st = 'CLOSED' THEN ca + (2 + pg_temp.u(t, 'tup') * 20) * interval '1 day' END, 0
FROM (SELECT t, pg_temp.skew(t, 'tcust', :cust, 1.4) AS cu, pg_temp.ts(:'base'::timestamp, t, 'tc', 365) AS ca,
             CASE WHEN x < 0.20 THEN 'OPEN' WHEN x < 0.35 THEN 'IN_PROGRESS' WHEN x < 0.45 THEN 'WAITING' WHEN x < 0.75 THEN 'RESOLVED' ELSE 'CLOSED' END AS st
      FROM (SELECT t, pg_temp.u(t, 'tstat') AS x FROM generate_series(1, :cust / 2) t) z) q;
-- S9 tickets stuck IN_PROGRESS for weeks (and urgent ones nobody owns)
UPDATE support_ticket SET status = 'IN_PROGRESS', updated_at = created_at + interval '2 days', closed_at = NULL, assigned_user_id = 2 + id % 25
WHERE id % 40 = 3 AND created_at < :'base'::timestamp - interval '40 days';
UPDATE support_ticket SET priority = 'URGENT', assigned_user_id = NULL, status = 'OPEN', updated_at = created_at, closed_at = NULL
WHERE id % 200 = 8;

INSERT INTO ticket_history (ticket_id, actor_user_id, event_type, details, created_at)
SELECT id, 1000 + customer_id, 'CREATED', 'Ticket created', created_at FROM support_ticket ORDER BY id;
INSERT INTO ticket_history (ticket_id, actor_user_id, event_type, details, created_at)
SELECT id, 1, 'ASSIGNED', 'Assigned to support agent', created_at + interval '10 minutes' FROM support_ticket WHERE assigned_user_id IS NOT NULL ORDER BY id;
INSERT INTO ticket_history (ticket_id, actor_user_id, event_type, details, created_at)
SELECT id, assigned_user_id, 'STATUS_CHANGED', 'Status changed to ' || status, updated_at FROM support_ticket WHERE status <> 'OPEN' AND assigned_user_id IS NOT NULL ORDER BY id;
INSERT INTO ticket_comment (ticket_id, author_user_id, body, is_internal, created_at)
SELECT id, 1000 + customer_id, 'Customer follow-up on ' || ticket_code, FALSE, created_at + interval '5 minutes' FROM support_ticket WHERE id % 2 = 0 ORDER BY id;
INSERT INTO ticket_comment (ticket_id, author_user_id, body, is_internal, created_at)
SELECT id, assigned_user_id, CASE WHEN id % 3 = 0 THEN 'Internal note: checked payment trail.' ELSE 'We are looking into this.' END, id % 3 = 0, created_at + interval '1 hour'
FROM support_ticket WHERE assigned_user_id IS NOT NULL ORDER BY id;

-- ---------------------------------------------------------------- notifications (derived from real events)
INSERT INTO notification_record (customer_id, channel, event_type, status, subject, message, attempt_count, created_at, delivered_at, idempotency_key, next_attempt_at, last_error, read_at)
SELECT customer_id, ch,
       CASE WHEN recharge_id IS NOT NULL THEN 'RECHARGE_' || ev WHEN booking_id IS NOT NULL THEN 'BOOKING_' || ev ELSE 'SUBSCRIPTION_' || ev END,
       ns, 'Payment ' || lower(ev), 'Payment ' || id || ' ' || lower(ev) || '.',
       CASE ns WHEN 'SENT' THEN 1 WHEN 'FAILED' THEN 3 ELSE (id % 2) END, created_at + interval '2 seconds',
       CASE WHEN ns = 'SENT' THEN created_at + interval '5 seconds' END,
       'payment-' || id || '-' || ev,
       CASE WHEN ns = 'PENDING' THEN created_at + interval '10 minutes' END,
       CASE WHEN ns = 'FAILED' THEN 'Delivery provider rejected the message' END,
       CASE WHEN ns = 'SENT' AND ch = 'IN_APP' AND id % 5 < 3 THEN created_at + interval '2 hours' END
FROM (
  SELECT p.*, CASE p.status WHEN 'SUCCEEDED' THEN 'SUCCEEDED' WHEN 'REFUNDED' THEN 'REFUNDED' WHEN 'EXPIRED' THEN 'EXPIRED' ELSE 'FAILED' END AS ev,
         CASE WHEN x < 0.60 THEN 'IN_APP' WHEN x < 0.85 THEN 'SMS' ELSE 'EMAIL' END AS ch,
         CASE WHEN y < 0.93 THEN 'SENT' WHEN y < 0.96 THEN 'FAILED' ELSE 'PENDING' END AS ns
  FROM (SELECT p.*, pg_temp.u(p.id, 'nch') AS x, pg_temp.u(p.id, 'nst') AS y FROM payment_record p) p
  WHERE p.status IN ('SUCCEEDED', 'FAILED', 'REFUNDED', 'EXPIRED') AND p.id % 50 <> 7       -- every 50th payment never notified
) n ORDER BY n.id;
INSERT INTO notification_record (customer_id, channel, event_type, status, subject, message, attempt_count, created_at, delivered_at, idempotency_key)
SELECT p.customer_id, 'IN_APP', 'REFUND_SUCCEEDED', 'SENT', 'Refund processed', 'A refund of ' || f.amount || ' was processed.', 1,
       f.created_at, f.created_at + interval '3 seconds', 'refund-' || f.idempotency_key
FROM refund_record f JOIN payment_record p ON p.id = f.payment_id WHERE f.status = 'SUCCEEDED' ORDER BY f.id;

INSERT INTO customer_activity (customer_id, activity_type, summary, created_at)
SELECT customer_id, 'RECHARGE_' || CASE status WHEN 'SUCCEEDED' THEN 'COMPLETED' ELSE 'INITIATED' END,
       'Recharge request ' || id || ' ' || lower(status), created_at FROM recharge_transaction ORDER BY id;
INSERT INTO customer_activity (customer_id, activity_type, summary, created_at)
SELECT customer_id, 'BOOKING_CREATED', 'Booking ' || booking_code || ' ' || lower(status), created_at FROM booking_record ORDER BY id;

-- ---------------------------------------------------------------- streaming watch history
INSERT INTO streaming_watch_history (customer_id, content_id, watched_at, progress_seconds)
SELECT s.customer_id, c.id,
       LEAST(s.starts_at + pg_temp.u(s.id * 10 + g, 'wt') * (s.expires_at - s.starts_at), :'base'::timestamp),
       (pg_temp.u(s.id * 10 + g, 'wp') * 7200)::int
FROM customer_subscription s CROSS JOIN generate_series(1, 2) g
JOIN (SELECT row_number() OVER (ORDER BY id) AS n, id FROM streaming_content WHERE active) c
  ON c.n = 1 + ((s.id * 3 + g) % (SELECT count(*) FROM streaming_content WHERE active))
WHERE s.status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'RENEWAL_FAILED') AND s.id % 10 < 7 AND (g = 1 OR s.id % 2 = 0)
ORDER BY s.id, g;

-- ---------------------------------------------------------------- subscription events & audit
INSERT INTO subscription_event (subscription_id, event_type, details, created_at)
SELECT id, CASE status WHEN 'PENDING' THEN 'CREATED' ELSE 'ACTIVATED' END, 'Subscription ' || lower(status), starts_at FROM customer_subscription ORDER BY id;
INSERT INTO subscription_event (subscription_id, event_type, details, created_at)
SELECT id, status, 'Subscription ' || lower(status), expires_at FROM customer_subscription WHERE status IN ('EXPIRED', 'CANCELLED', 'RENEWAL_FAILED') ORDER BY id;

INSERT INTO audit_event (actor_user_id, event_type, entity_type, entity_id, correlation_id, details, created_at)
SELECT CASE WHEN g % 3 = 0 THEN 2 + g % 25 ELSE 1 END,
       (ARRAY['PLAN_UPDATED','CUSTOMER_STATUS_CHANGED','REFUND_APPROVED','COUPON_CREATED','TICKET_REASSIGNED'])[1 + g % 5],
       (ARRAY['recharge_plan','customer_profile','refund_record','coupon','support_ticket'])[1 + g % 5],
       (1 + g % 500)::text, md5('corr' || g), 'Synthetic administrative action', pg_temp.ts(:'base'::timestamp, g, 'aud', 400)
FROM generate_series(1, 2000) g;

-- ---------------------------------------------------------------- no event may be dated in the future
UPDATE refund_record SET created_at = LEAST(created_at, :'base'::timestamp);
UPDATE support_ticket SET updated_at = GREATEST(created_at, LEAST(updated_at, :'base'::timestamp)), closed_at = LEAST(closed_at, :'base'::timestamp);
UPDATE ticket_history SET created_at = LEAST(created_at, :'base'::timestamp);
UPDATE ticket_comment SET created_at = LEAST(created_at, :'base'::timestamp);
UPDATE notification_record SET created_at = LEAST(created_at, :'base'::timestamp), delivered_at = LEAST(delivered_at, :'base'::timestamp), read_at = LEAST(read_at, :'base'::timestamp);
UPDATE subscription_event SET created_at = LEAST(created_at, :'base'::timestamp);
UPDATE customer_activity SET created_at = LEAST(created_at, :'base'::timestamp);
UPDATE streaming_watch_history SET watched_at = LEAST(watched_at, :'base'::timestamp);

-- ---------------------------------------------------------------- sequences & statistics
SELECT setval(pg_get_serial_sequence('app_user', 'id'), (SELECT max(id) FROM app_user)) AS app_user_seq;
SELECT setval(pg_get_serial_sequence('customer_profile', 'id'), (SELECT max(id) FROM customer_profile)) AS profile_seq;
SELECT setval(pg_get_serial_sequence('mobile_number', 'id'), (SELECT max(id) FROM mobile_number)) AS mobile_seq;
SELECT setval(pg_get_serial_sequence('recharge_transaction', 'id'), (SELECT max(id) FROM recharge_transaction)) AS recharge_seq;
SELECT setval(pg_get_serial_sequence('customer_subscription', 'id'), (SELECT max(id) FROM customer_subscription)) AS subscription_seq;
SELECT setval(pg_get_serial_sequence('booking_record', 'id'), (SELECT max(id) FROM booking_record)) AS booking_seq;
SELECT setval(pg_get_serial_sequence('support_ticket', 'id'), (SELECT max(id) FROM support_ticket)) AS ticket_seq;

COMMIT;
ANALYZE;

\echo ---- row counts
SELECT 'customer_profile' AS entity, count(*) FROM customer_profile UNION ALL
SELECT 'mobile_number', count(*) FROM mobile_number UNION ALL
SELECT 'recharge_transaction', count(*) FROM recharge_transaction UNION ALL
SELECT 'payment_record', count(*) FROM payment_record UNION ALL
SELECT 'refund_record', count(*) FROM refund_record UNION ALL
SELECT 'customer_subscription', count(*) FROM customer_subscription UNION ALL
SELECT 'booking_record', count(*) FROM booking_record UNION ALL
SELECT 'support_ticket', count(*) FROM support_ticket UNION ALL
SELECT 'notification_record', count(*) FROM notification_record UNION ALL
SELECT 'streaming_watch_history', count(*) FROM streaming_watch_history UNION ALL
SELECT 'coupon_redemption', count(*) FROM coupon_redemption UNION ALL
SELECT 'audit_event', count(*) FROM audit_event;
