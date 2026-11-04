import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, getSession, type Page, type Row } from '../api';
import { formatDate, formatDateTime, formatMoney } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Notice, PageHeader, Pager, Stat, Table, useAction, useDebounced, useFetch } from '../ui';

export function AdminCustomers() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const dq = useDebounced(q);
  const list = useFetch(() => api<Page>('/customers', { query: { q: dq, status, page, size: 15 } }), [dq, status, page]);
  return (
    <>
      <PageHeader title="Customers" subtitle="Search by name, email or phone number." />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search customers" value={q} onChange={(e) => (setQ(e.target.value), setPage(0))} aria-label="Search customers" />
          <select value={status} onChange={(e) => (setStatus(e.target.value), setPage(0))} aria-label="Status">
            <option value="">All statuses</option>
            <option>ACTIVE</option>
            <option>SUSPENDED</option>
            <option>CLOSED</option>
          </select>
        </div>
        <Async state={list}>
          {(p) => (
            <>
              <Table
                rows={p.content}
                rowKey={(r) => r.customerId}
                empty="No customers match."
                columns={[
                  { header: 'ID', render: (r) => <Link to={`/admin/customers/${r.customerId}`}>{r.customerId}</Link> },
                  { header: 'Name', render: (r) => `${r.firstName} ${r.lastName}` },
                  { header: 'Email', render: (r) => r.email },
                  { header: 'Phone', render: (r) => r.phoneNumber ?? '—' },
                  { header: 'Status', render: (r) => <Badge value={r.accountStatus} /> },
                  { header: 'Joined', render: (r) => formatDate(r.createdAt) },
                ]}
              />
              <Pager page={p.page.number} totalPages={p.page.totalPages} total={p.page.totalElements} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
    </>
  );
}

export function AdminCustomerDetail() {
  const { id } = useParams();
  const detail = useFetch(() => api<Row>(`/admin/customers/${id}`), [id]);
  const action = useAction();
  const isAdmin = getSession()?.roles.includes('ADMIN');
  const setStatus = async (status: string) => {
    await action.run(() => api(`/admin/customers/${id}/status`, { method: 'PUT', body: { status } }), `Customer set to ${status}.`);
    detail.reload();
  };
  return (
    <Async state={detail}>
      {(d) => (
        <>
          <PageHeader title={`${d.profile.firstName} ${d.profile.lastName}`} subtitle={`Customer #${d.profile.id} · ${d.profile.email}`} actions={<Link to="/admin/customers" className="btn">← All customers</Link>} />
          <ErrorBox error={action.error} />
          <Notice>{action.done}</Notice>
          <div className="grid stats">
            <Stat label="Recharges" value={d.totals.recharges} />
            <Stat label="Payments" value={d.totals.payments} />
            <Stat label="Paid (succeeded)" value={formatMoney(d.totals.paidAmount)} tone="good" />
            <Stat label="Bookings" value={d.totals.bookings} />
            <Stat label="Tickets" value={d.totals.tickets} />
          </div>
          <Card title="Account" actions={isAdmin && (
            <>
              {d.profile.accountStatus !== 'ACTIVE' && <Button className="btn small" onClick={() => setStatus('ACTIVE')}>Activate</Button>}
              {d.profile.accountStatus !== 'SUSPENDED' && <Button className="btn small" onClick={() => setStatus('SUSPENDED')}>Suspend</Button>}
              {d.profile.accountStatus !== 'CLOSED' && <Button className="btn small danger" onClick={() => window.confirm('Close this account?') && setStatus('CLOSED')}>Close</Button>}
            </>
          )}>
            <dl className="kv">
              <dt>Account status</dt><dd><Badge value={d.profile.accountStatus} /></dd>
              <dt>Login status</dt><dd><Badge value={d.profile.loginStatus} /> {d.profile.failedLoginAttempts > 0 && <span className="muted small">({d.profile.failedLoginAttempts} failed attempts)</span>}</dd>
              <dt>Phone</dt><dd>{d.profile.phoneNumber ?? '—'}</dd>
              <dt>Joined</dt><dd>{formatDate(d.profile.createdAt)}</dd>
              <dt>Numbers</dt><dd>{d.mobileNumbers.map((n: Row) => <span key={n.id} style={{ marginRight: 10 }}>{n.phoneNumber} <Badge value={n.status} /></span>)}</dd>
            </dl>
          </Card>
          <Card title="Recent payments">
            <Table rows={d.recentPayments as Row[]} rowKey={(r) => r.id} empty="No payments." columns={[
              { header: '#', render: (r) => r.id },
              { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
              { header: 'For', render: (r) => (r.rechargeId ? 'Recharge' : r.bookingId ? 'Booking' : 'Subscription') },
              { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
              { header: 'Status', render: (r) => <Badge value={r.status} /> },
              { header: 'Recon', render: (r) => <Badge value={r.reconciliationState} /> },
            ]} />
            <p className="small"><Link to={`/admin/payments?customerId=${d.profile.id}`}>All payments for this customer →</Link></p>
          </Card>
          <Card title="Subscriptions">
            <Table rows={d.subscriptions as Row[]} rowKey={(r) => r.id} empty="None." columns={[
              { header: 'Plan', render: (r) => r.plan }, { header: 'Status', render: (r) => <Badge value={r.status} /> },
              { header: 'Starts', render: (r) => formatDate(r.startsAt) }, { header: 'Expires', render: (r) => formatDate(r.expiresAt) },
            ]} />
          </Card>
          <Card title="Support tickets">
            <Table rows={d.recentTickets as Row[]} rowKey={(r) => r.id} empty="None." columns={[
              { header: 'Ticket', render: (r) => r.ticketCode }, { header: 'Subject', render: (r) => r.subject },
              { header: 'Priority', render: (r) => <Badge value={r.priority} /> }, { header: 'Status', render: (r) => <Badge value={r.status} /> },
            ]} />
          </Card>
          <Card title="Activity">
            <Table rows={d.activity as Row[]} rowKey={(r) => r.id} empty="No activity." columns={[{ header: 'When', render: (r) => formatDateTime(r.createdAt) }, { header: 'What', render: (r) => r.summary }]} />
          </Card>
        </>
      )}
    </Async>
  );
}
