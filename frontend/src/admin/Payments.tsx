import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, getSession, type Page, type Row } from '../api';
import { formatDateTime, formatMoney } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, PageHeader, Pager, Table, useAction, useDebounced, useFetch } from '../ui';

const STATUSES = ['INITIATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUND_PENDING', 'REFUNDED'];

export function AdminPayments() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [recon, setRecon] = useState('');
  const [page, setPage] = useState(0);
  const dq = useDebounced(q);
  const customerId = params.get('customerId') ?? undefined;
  const list = useFetch(() => api<Page>('/admin/payments', { query: { status, reconciliation: recon, q: dq, customerId, page, size: 20 } }), [status, recon, dq, customerId, page]);
  const [target, setTarget] = useState<Row | null>(null);
  const isAdmin = getSession()?.roles.includes('ADMIN');
  const reset = (fn: () => void) => (fn(), setPage(0));

  return (
    <>
      <PageHeader title="Payment monitor" subtitle="Search any payment; resolve ones flagged for reconciliation." />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Payment # or provider reference" value={q} onChange={(e) => reset(() => setQ(e.target.value))} aria-label="Search payments" />
          <select value={status} onChange={(e) => reset(() => setStatus(e.target.value))} aria-label="Status">
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
          <select value={recon} onChange={(e) => reset(() => setRecon(e.target.value))} aria-label="Reconciliation">
            <option value="">Any reconciliation state</option>
            <option>RECONCILIATION_REQUIRED</option><option>RETRY_REQUIRED</option><option>RECONCILED</option>
          </select>
          {customerId && <button className="btn small" onClick={() => setParams({})}>Customer #{customerId} ✕</button>}
        </div>
        <Async state={list}>
          {(p) => (
            <>
              <Table
                rows={p.content}
                rowKey={(r) => r.id}
                empty="No payments match these filters."
                columns={[
                  { header: '#', render: (r) => r.id },
                  { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                  { header: 'Customer', render: (r) => <Link to={`/admin/customers/${r.customerId}`}>{r.customerId}</Link> },
                  { header: 'For', render: (r) => (r.rechargeId ? `Recharge #${r.rechargeId}` : r.bookingId ? `Booking #${r.bookingId}` : `Subscription #${r.subscriptionId}`) },
                  { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
                  { header: 'Status', render: (r) => <><Badge value={r.status} /> {r.attemptCount > 1 && <span className="muted small">attempt {r.attemptCount}</span>}</> },
                  { header: 'Reconciliation', render: (r) => <Badge value={r.reconciliationState} /> },
                  { header: 'Failure', render: (r) => r.failureReason ?? '' },
                  { header: '', render: (r) => isAdmin && ['RECONCILIATION_REQUIRED', 'RETRY_REQUIRED'].includes(r.reconciliationState) ? <Button className="btn small" onClick={() => setTarget(r)}>Resolve</Button> : null },
                ]}
              />
              <Pager page={p.page.number} totalPages={p.page.totalPages} total={p.page.totalElements} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
      {target && <ReconcileDialog payment={target} onClose={() => (setTarget(null), list.reload())} />}
    </>
  );
}

function ReconcileDialog({ payment, onClose }: { payment: Row; onClose: () => void }) {
  const [outcome, setOutcome] = useState('SUCCEEDED');
  const [note, setNote] = useState('');
  const action = useAction();
  const submit = async () => {
    const r = await action.run(() => api(`/admin/payments/${payment.id}/reconcile`, { method: 'POST', body: { outcome, note: note || undefined } }), 'Payment reconciled.');
    if (r) setTimeout(onClose, 700);
  };
  return (
    <Modal title={`Reconcile payment #${payment.id}`} onClose={onClose}>
      <p className="muted">Current state: <Badge value={payment.status} /> · <Badge value={payment.reconciliationState} />. Choose the outcome confirmed with the provider.</p>
      <Field label="Provider outcome">
        <select value={outcome} onChange={(e) => setOutcome(e.target.value)}><option>SUCCEEDED</option><option>FAILED</option></select>
      </Field>
      <Field label="Note (audited)"><input maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      <ErrorBox error={action.error} />
      {action.done && <p className="alert success">{action.done}</p>}
      <Button kind="primary" busy={action.busy} disabled={!!action.done} onClick={submit}>Apply outcome</Button>
    </Modal>
  );
}

export function AdminRefunds() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const list = useFetch(() => api<Page>('/admin/refunds', { query: { status, page, size: 20 } }), [status, page]);
  return (
    <>
      <PageHeader title="Refund monitor" />
      <Card>
        <div className="filters">
          <select value={status} onChange={(e) => (setStatus(e.target.value), setPage(0))} aria-label="Status">
            <option value="">All statuses</option><option>PENDING</option><option>SUCCEEDED</option><option>FAILED</option>
          </select>
        </div>
        <Async state={list}>
          {(p) => (
            <>
              <Table rows={p.content} rowKey={(r) => r.id} empty="No refunds." columns={[
                { header: '#', render: (r) => r.id }, { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                { header: 'Payment', render: (r) => `#${r.paymentId}` }, { header: 'Customer', render: (r) => r.customerId },
                { header: 'Refund', className: 'num', render: (r) => formatMoney(r.amount) },
                { header: 'Of', className: 'num', render: (r) => formatMoney(r.paymentAmount) },
                { header: 'Status', render: (r) => <Badge value={r.status} /> }, { header: 'Reason', render: (r) => r.reason },
              ]} />
              <Pager page={p.page.number} totalPages={p.page.totalPages} total={p.page.totalElements} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
    </>
  );
}
