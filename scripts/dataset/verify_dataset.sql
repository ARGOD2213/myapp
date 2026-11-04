-- Consistency checks over the generated dataset.
\echo ---- invariants (every count must be 0)
SELECT 'refund total exceeds payment' AS check_name, count(*) FROM (
  SELECT p.id FROM payment_record p JOIN refund_record f ON f.payment_id = p.id AND f.status IN ('PENDING','SUCCEEDED')
  GROUP BY p.id, p.amount HAVING sum(f.amount) > p.amount) x
UNION ALL SELECT 'recharge without payment', count(*) FROM recharge_transaction r WHERE NOT EXISTS (SELECT 1 FROM payment_record p WHERE p.recharge_id = r.id)
UNION ALL SELECT 'recharge mobile not owned by customer', count(*) FROM recharge_transaction r JOIN mobile_number m ON m.id = r.mobile_number_id WHERE m.customer_id <> r.customer_id
UNION ALL SELECT 'payment customer differs from recharge customer', count(*) FROM payment_record p JOIN recharge_transaction r ON r.id = p.recharge_id WHERE r.customer_id <> p.customer_id
UNION ALL SELECT 'coupon over limit', count(*) FROM coupon WHERE usage_count > usage_limit;
\echo ---- deliberate scenarios (every count should be greater than 0)
SELECT 'duplicate successful recharge payments' AS scenario, count(*) FROM (SELECT recharge_id FROM payment_record WHERE status='SUCCEEDED' AND recharge_id IS NOT NULL GROUP BY 1 HAVING count(*) > 1) x
UNION ALL SELECT 'payments needing reconciliation', count(*) FROM payment_record WHERE reconciliation_state <> 'NONE'
UNION ALL SELECT 'recharge SUCCEEDED / payment FAILED', count(*) FROM recharge_transaction r JOIN payment_record p ON p.recharge_id=r.id WHERE r.status='SUCCEEDED' AND p.status='FAILED'
UNION ALL SELECT 'recharge PENDING / payment SUCCEEDED', count(*) FROM recharge_transaction r JOIN payment_record p ON p.recharge_id=r.id WHERE r.status='PENDING' AND p.status='SUCCEEDED'
UNION ALL SELECT 'payments with no notification', count(*) FROM payment_record p WHERE p.status IN ('SUCCEEDED','FAILED') AND NOT EXISTS (SELECT 1 FROM notification_record n WHERE n.customer_id = p.customer_id AND n.idempotency_key IN ('payment-' || p.id || '-SUCCEEDED', 'payment-' || p.id || '-FAILED'))
UNION ALL SELECT 'tickets IN_PROGRESS > 30 days', count(*) FROM support_ticket WHERE status='IN_PROGRESS' AND updated_at < now() - interval '30 days'
UNION ALL SELECT 'customers with no recharges', count(*) FROM customer_profile c WHERE NOT EXISTS (SELECT 1 FROM recharge_transaction r WHERE r.customer_id=c.id)
UNION ALL SELECT 'max recharges for one customer', max(n) FROM (SELECT count(*) n FROM recharge_transaction GROUP BY customer_id) x
UNION ALL SELECT 'cancelled bookings with captured payment', count(*) FROM booking_record b JOIN payment_record p ON p.booking_id=b.id WHERE b.status='CANCELLED' AND p.status='SUCCEEDED';
