import { Link } from 'react-router-dom';
import { api, type Page, type Row } from '../api';
import { formatDate, formatDateTime, formatMoney } from '../format';
import { Async, Badge, Card, PageHeader, Stat, Table, useFetch } from '../ui';

export function Dashboard() {
  const profile = useFetch(() => api<Row>('/customers/me'));
  const activity = useFetch(() => api<Row[]>('/customers/me/activity', { query: { limit: 8 } }));
  const subs = useFetch(() => api<Row[]>('/subscriptions/me'));
  const notes = useFetch(() => api<Row[]>('/notifications/me'));
  const recharges = useFetch(() => api<Page>('/recharges/me', { query: { size: 5 } }));

  return (
    <>
      <PageHeader title={profile.data ? `Welcome, ${profile.data.firstName}` : 'Welcome'} subtitle="Your ConnectOne account at a glance." />
      <div className="grid stats">
        <Async state={subs}>{(s) => <Stat label="Active subscriptions" value={s.filter((x) => x.status === 'ACTIVE').length} tone="good" />}</Async>
        <Async state={notes}>{(n) => <Stat label="Unread notifications" value={n.filter((x) => !x.readAt).length} tone={n.some((x) => !x.readAt) ? 'warn' : undefined} />}</Async>
        <Async state={recharges}>{(r) => <Stat label="Recharges to date" value={r.page.totalElements} />}</Async>
      </div>
      <div className="grid" style={{ marginTop: '1rem' }}>
        <Card title="Quick actions">
          <div className="stack">
            <Link to="/recharge" className="btn primary" style={{ display: 'block', textAlign: 'center' }}>Recharge a number</Link>
            <Link to="/bookings" className="btn" style={{ display: 'block', textAlign: 'center' }}>Book a ticket</Link>
            <Link to="/support" className="btn" style={{ display: 'block', textAlign: 'center' }}>Contact support</Link>
          </div>
        </Card>
        <Card title="Subscriptions">
          <Async state={subs}>
            {(rows) => (
              <Table
                rows={rows.slice(0, 4)}
                rowKey={(r) => r.id}
                empty="No subscriptions."
                columns={[
                  { header: 'Plan', render: (r) => r.planName },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  { header: 'Expires', render: (r) => formatDate(r.expiresAt) },
                ]}
              />
            )}
          </Async>
        </Card>
      </div>
      <Card title="Recent recharges">
        <Async state={recharges}>
          {(p) => (
            <Table
              rows={p.content}
              rowKey={(r) => r.id}
              empty="No recharges yet."
              columns={[
                { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                { header: 'Plan', render: (r) => r.planCode },
                { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
                { header: 'Status', render: (r) => <Badge value={r.status} /> },
              ]}
            />
          )}
        </Async>
      </Card>
      <Card title="Recent activity">
        <Async state={activity}>
          {(rows) => (
            <Table rows={rows} rowKey={(r) => r.id} empty="No activity yet." columns={[{ header: 'When', render: (r) => formatDateTime(r.createdAt) }, { header: 'What', render: (r) => r.summary }]} />
          )}
        </Async>
      </Card>
    </>
  );
}
