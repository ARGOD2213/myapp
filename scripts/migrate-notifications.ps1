# One-off data migration used when a service is extracted: copies the LEGACY notification_record rows from core-service's
# database (connectone_dev) into notification-service's own database (connectone_notifications), adding user_id.
# Runs entirely inside the ConnectOne postgres container. Safe to re-run: the target is emptied first.
# (This is the "strangler" data step: after it, only notification-service reads/writes notifications.)
$ErrorActionPreference = 'Continue'
$sql = @'
psql -U "$POSTGRES_USER" -d connectone_notifications -q -c "TRUNCATE notification_record RESTART IDENTITY" &&
psql -U "$POSTGRES_USER" -d connectone_dev -q -c "COPY (SELECT n.customer_id, c.user_id, n.channel, n.event_type, n.status, n.subject, n.message, n.attempt_count, n.idempotency_key, n.next_attempt_at, n.last_error, n.read_at, n.created_at, n.delivered_at FROM notification_record n JOIN customer_profile c ON c.id = n.customer_id ORDER BY n.id) TO STDOUT WITH CSV" |
psql -U "$POSTGRES_USER" -d connectone_notifications -q -c "COPY notification_record (customer_id, user_id, channel, event_type, status, subject, message, attempt_count, idempotency_key, next_attempt_at, last_error, read_at, created_at, delivered_at) FROM STDIN WITH CSV" &&
psql -U "$POSTGRES_USER" -d connectone_notifications -q -c "ANALYZE notification_record" &&
psql -U "$POSTGRES_USER" -d connectone_notifications -At -c "select 'migrated ' || count(*) || ' notifications' from notification_record"
'@
# the script is piped via stdin: passing it as an argument would let PowerShell mangle the nested quotes
($sql -replace "`r", '') | docker exec -i connectone-postgres sh -s
if ($LASTEXITCODE -ne 0) { throw "notification migration failed" }
