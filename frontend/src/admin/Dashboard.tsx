import { Link } from 'react-router-dom';
import { api, type Row } from '../api';
import { formatMoney } from '../format';
import { Async, Card, PageHeader, Stat, useFetch } from '../ui';

const sum = (rows: Row[] = []) => rows.reduce((a, r) => a + Number(r.count), 0);
const get = (rows: Row[] = [], key: string) => Number(rows.find((r) => r.key === key)?.count ?? 0);

function Breakdown({ title, rows, to }: { title: string; rows: Row[]; to?: string }) {
  return (
    <Card title={title} actions={to ? <Link to={to}>Open →</Link> : undefined}>
      <table>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key}>
              <td>{r.key.replaceAll('_', ' ').toLowerCase()}</td>
              <td className="num">{Number(r.count).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

export function AdminDashboard() {
  const dash = useFetch(() => api<Row>('/admin/dashboard'));
  return (
    <>
      <PageHeader title="Operations dashboard" subtitle="Live figures from the ConnectOne database." actions={<button className="btn" onClick={dash.reload}>Refresh</button>} />
      <Async state={dash}>
        {(d) => (
          <>
            <div className="grid stats">
              <Stat label="Customers" value={sum(d.customersByStatus).toLocaleString()} />
              <Stat label="Revenue, last 30 days" value={formatMoney(d.revenueLast30Days)} tone="good" />
              <Stat label="Refunded, last 30 days" value={formatMoney(d.refundedLast30Days)} />
              <Stat label="Payments needing reconciliation" value={d.paymentsNeedingReconciliation} tone={d.paymentsNeedingReconciliation > 0 ? 'bad' : 'good'} />
              <Stat label="Tickets stuck in progress > 14 days" value={d.staleInProgressTickets} tone={d.staleInProgressTickets > 0 ? 'warn' : 'good'} />
              <Stat label="Failed notifications" value={get(d.notificationsByStatus, 'FAILED').toLocaleString()} tone="warn" />
              <Stat label="Upcoming confirmed bookings" value={d.upcomingBookings.toLocaleString()} />
            </div>
            <div className="grid" style={{ marginTop: '1rem' }}>
              <Breakdown title="Payments (last 24h)" rows={d.paymentsLast24h} to="/admin/payments" />
              <Breakdown title="Subscriptions" rows={d.subscriptionsByStatus} />
              <Breakdown title="Support tickets" rows={d.ticketsByStatus} to="/admin/tickets" />
              <Breakdown title="Notifications" rows={d.notificationsByStatus} />
              <Breakdown title="Customers" rows={d.customersByStatus} to="/admin/customers" />
            </div>
          </>
        )}
      </Async>
    </>
  );
}
