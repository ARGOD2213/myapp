import { useState } from 'react';
import { api, idempotencyKey, type Page, type Row } from '../api';
import { formatDateTime, formatMoney } from '../format';
import { Async, Badge, Button, Card, Empty, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useDebounced, useFetch } from '../ui';
import { PaymentPanel } from './PaymentPanel';

export function Recharge() {
  const [q, setQ] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const dq = useDebounced(q);
  const plans = useFetch(() => api<Row[]>('/recharge-plans', { query: { query: dq, maxPrice }, auth: false }), [dq, maxPrice]);
  const numbers = useFetch(() => api<Row[]>('/mobile-numbers'));
  const [page, setPage] = useState(0);
  const history = useFetch(() => api<Page>('/recharges/me', { query: { page, size: 8 } }), [page]);
  const [selected, setSelected] = useState<Row | null>(null);

  return (
    <>
      <PageHeader title="Recharge" subtitle="Pick a plan, choose the number, pay." />
      <Card title="Plans">
        <div className="filters">
          <input className="search" placeholder="Search plans" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search plans" />
          <input className="search" type="number" min="0" placeholder="Max price (₹)" value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} aria-label="Maximum price" />
        </div>
        <Async state={plans}>
          {(rows) =>
            rows.length === 0 ? (
              <Empty>No plans match your filters.</Empty>
            ) : (
              <div className="grid">
                {rows.map((p) => (
                  <div className="card plan" key={p.id}>
                    <strong>{p.name}</strong>
                    <span className="price">{formatMoney(p.price)}</span>
                    <span className="muted small">
                      {p.validityDays} days · {(p.dataAllowanceMb / 1024).toFixed(1)} GB · {p.voiceMinutes} min
                    </span>
                    <Button kind="primary" onClick={() => setSelected(p)}>
                      Recharge
                    </Button>
                  </div>
                ))}
              </div>
            )
          }
        </Async>
      </Card>
      <Card title="Recent recharges">
        <Async state={history}>
          {(p) => (
            <>
              <Table
                rows={p.content}
                rowKey={(r) => r.id}
                empty="No recharges yet."
                columns={[
                  { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                  { header: 'Plan', render: (r) => r.planCode },
                  { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  { header: 'Valid until', render: (r) => formatDateTime(r.expiresAt) },
                ]}
              />
              <Pager page={p.page.number} totalPages={p.page.totalPages} total={p.page.totalElements} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
      {selected && <RechargeDialog plan={selected} numbers={numbers.data ?? []} onClose={() => setSelected(null)} onChanged={history.reload} />}
    </>
  );
}

function RechargeDialog({ plan, numbers, onClose, onChanged }: { plan: Row; numbers: Row[]; onClose: () => void; onChanged: () => void }) {
  const active = numbers.filter((n) => n.status === 'ACTIVE');
  const [mobileId, setMobileId] = useState<number | ''>(active[0]?.id ?? '');
  const [coupon, setCoupon] = useState('');
  const [quote, setQuote] = useState<Row | null>(null);
  const [key] = useState(() => idempotencyKey('rch'));
  const [created, setCreated] = useState<Row | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const check = useAction();
  const start = useAction();

  const applyCoupon = async () => {
    const q = await check.run(() => api('/coupons/validate', { method: 'POST', body: { code: coupon, amount: plan.price } }));
    setQuote(q ?? null);
  };
  const submit = async () => {
    const r = await start.run(() =>
      api('/recharges', { method: 'POST', body: { mobileNumberId: mobileId, planId: plan.id, idempotencyKey: key, couponCode: quote ? coupon : undefined } }),
    );
    if (r) {
      setCreated(r);
      onChanged();
    }
  };
  return (
    <Modal title={`Recharge · ${plan.name}`} onClose={onClose}>
      {!created ? (
        <>
          <Field label="Mobile number">
            <select value={mobileId} onChange={(e) => setMobileId(Number(e.target.value))}>
              {active.map((n) => (
                <option key={n.id} value={n.id}>
                  {n.phoneNumber}
                </option>
              ))}
            </select>
          </Field>
          {active.length === 0 && <Notice kind="warn">You have no active mobile number. Add one under Profile &amp; numbers.</Notice>}
          <Field label="Coupon code (optional)">
            <div className="row">
              <input className="search grow" value={coupon} onChange={(e) => (setCoupon(e.target.value), setQuote(null))} />
              <Button onClick={applyCoupon} busy={check.busy} disabled={!coupon.trim()}>
                Apply
              </Button>
            </div>
          </Field>
          <ErrorBox error={check.error} />
          <p>
            Total:{' '}
            <strong>{formatMoney(quote ? quote.payable : plan.price)}</strong>
            {quote && <span className="muted"> (saves {formatMoney(quote.discountAmount)})</span>}
          </p>
          <ErrorBox error={start.error} />
          <Button kind="primary" busy={start.busy} onClick={submit} disabled={!mobileId}>
            Continue to payment
          </Button>
        </>
      ) : result ? (
        <>
          <Notice kind={result === 'SUCCEEDED' ? 'success' : 'warn'}>{result === 'SUCCEEDED' ? 'Recharge successful.' : `Payment ${result.toLowerCase()}.`}</Notice>
          <Button onClick={onClose}>Close</Button>
        </>
      ) : (
        <PaymentPanel paymentId={created.paymentId} amount={created.amount} status={created.status} onDone={(s) => (setResult(s), onChanged())} />
      )}
    </Modal>
  );
}
