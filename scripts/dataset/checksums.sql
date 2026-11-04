select 'payment' t, md5(string_agg(concat_ws(',', id, customer_id, recharge_id, booking_id, subscription_id, amount, status, reconciliation_state, idempotency_key, coalesce(provider_reference,''), created_at), '|' order by id)) from payment_record
union all select 'recharge', md5(string_agg(concat_ws(',', r.id, r.customer_id, r.mobile_number_id, p.plan_code, r.amount, r.status, r.created_at), '|' order by r.id)) from recharge_transaction r join recharge_plan p on p.id = r.plan_id
union all select 'refund', md5(string_agg(concat_ws(',', id, payment_id, amount, status, created_at), '|' order by id)) from refund_record
union all select 'booking', md5(string_agg(concat_ws(',', b.id, b.customer_id, i.item_code, b.seat_or_slot, b.status, b.created_at), '|' order by b.id)) from booking_record b join booking_catalog_item i on i.id = b.catalog_item_id
union all select 'ticket', md5(string_agg(concat_ws(',', id, customer_id, assigned_user_id, status, priority, created_at, updated_at), '|' order by id)) from support_ticket
union all select 'notification', md5(string_agg(concat_ws(',', id, customer_id, channel, event_type, status, idempotency_key), '|' order by id)) from notification_record
union all select 'watch', md5(string_agg(concat_ws(',', w.id, w.customer_id, c.content_code, w.progress_seconds, w.watched_at), '|' order by w.id)) from streaming_watch_history w join streaming_content c on c.id = w.content_id
union all select 'subscription', md5(string_agg(concat_ws(',', s.id, s.customer_id, p.plan_code, s.status, s.starts_at, s.expires_at), '|' order by s.id)) from customer_subscription s join subscription_plan p on p.id = s.plan_id
union all select 'activity', md5(string_agg(concat_ws(',', id, customer_id, activity_type, created_at), '|' order by id)) from customer_activity
union all select 'ticket_comment', md5(string_agg(concat_ws(',', id, ticket_id, author_user_id, created_at), '|' order by id)) from ticket_comment
union all select 'audit', md5(string_agg(concat_ws(',', id, actor_user_id, event_type, entity_id, created_at), '|' order by id)) from audit_event
union all select 'callback', md5(string_agg(concat_ws(',', callback_id, payment_id, outcome), '|' order by callback_id)) from payment_callback
union all select 'redemption', md5(string_agg(concat_ws(',', id, coupon_id, customer_id, payment_id, discount_amount), '|' order by id)) from coupon_redemption;
