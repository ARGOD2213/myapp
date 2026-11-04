import { useState } from 'react';
import { api, idempotencyKey, type Row } from '../api';
import { formatDateTime, formatMoney } from '../format';
import { Async, Badge, Button, Card, Empty, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useDebounced, useFetch, usePaged } from '../ui';
import { PaymentPanel } from './PaymentPanel';

export function Bookings() {
  const [type, setType] = useState('');
  const [q, setQ] = useState('');
  const dq = useDebounced(q);
  const catalogue = useFetch(() => api<Row[]>('/bookings/catalogue', { query: { type, query: dq } }), [type, dq]);
  const mine = useFetch(() => api<Row[]>('/bookings/me'));
  const [item, setItem] = useState<Row | null>(null);
  const cancelAction = useAction();
  const pagedCat = usePaged(catalogue.data, 9);
  const pagedMine = usePaged(mine.data, 8);

  const cancel = async (b: Row) => {
    if (!window.confirm(`Cancel booking ${b.bookingCode}? Paid bookings are refunded.`)) return;
    await cancelAction.run(() => api(`/bookings/${b.id}/cancel`, { method: 'POST' }), 'Booking cancelled.');
    mine.reload();
  };

  return (
    <>
      <PageHeader title="Bookings" subtitle="Bus, movie and event tickets." />
      <Card title="My bookings">
        <ErrorBox error={cancelAction.error} />
        <Notice>{cancelAction.done}</Notice>
        <Async state={mine}>
          {() => (
            <>
              <Table
                rows={pagedMine.items}
                rowKey={(r) => r.id}
                empty="You have not booked anything yet."
                columns={[
                  { header: 'Code', render: (r) => r.bookingCode },
                  { header: 'What', render: (r) => `${r.title} (${r.itemType.toLowerCase()})` },
                  { header: 'Seat', render: (r) => r.seatOrSlot },
                  { header: 'Starts', render: (r) => formatDateTime(r.startsAt) },
                  { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  {
                    header: '',
                    render: (r) =>
                      ['PENDING', 'CONFIRMED'].includes(r.status) ? (
                        <Button className="btn small danger" onClick={() => cancel(r)}>
                          Cancel
                        </Button>
                      ) : null,
                  },
                ]}
              />
              <Pager page={pagedMine.page} totalPages={pagedMine.totalPages} total={pagedMine.total} onChange={pagedMine.setPage} />
            </>
          )}
        </Async>
      </Card>
      <Card title="Upcoming schedules">
        <div className="filters">
          <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Type">
            <option value="">All types</option>
            <option value="BUS">Bus</option>
            <option value="MOVIE">Movie</option>
            <option value="EVENT">Event</option>
          </select>
          <input className="search" placeholder="Search title" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search schedules" />
        </div>
        <Async state={catalogue}>
          {() =>
            pagedCat.total === 0 ? (
              <Empty>No schedules match.</Empty>
            ) : (
              <>
                <div className="grid">
                  {pagedCat.items.map((c) => (
                    <div className="card plan" key={c.id}>
                      <div className="row">
                        <Badge value={c.itemType} />
                        <strong>{c.title}</strong>
                      </div>
                      <span className="muted small">
                        {c.location} · {formatDateTime(c.startsAt)}
                      </span>
                      <span className="price">{formatMoney(c.price)}</span>
                      <Button kind="primary" onClick={() => setItem(c)}>
                        Choose seat
                      </Button>
                    </div>
                  ))}
                </div>
                <Pager page={pagedCat.page} totalPages={pagedCat.totalPages} total={pagedCat.total} onChange={pagedCat.setPage} />
              </>
            )
          }
        </Async>
      </Card>
      {item && <BookDialog item={item} onClose={() => (setItem(null), mine.reload())} />}
    </>
  );
}

function BookDialog({ item, onClose }: { item: Row; onClose: () => void }) {
  const seats = useFetch(() => api<string[]>(`/bookings/catalogue/${item.id}/availability`));
  const [seat, setSeat] = useState('');
  const [coupon, setCoupon] = useState('');
  const [key] = useState(() => idempotencyKey('bk'));
  const [booking, setBooking] = useState<Row | null>(null);
  const [outcome, setOutcome] = useState<string | null>(null);
  const action = useAction();

  const book = async () => {
    const b = await action.run(() => api('/bookings', { method: 'POST', body: { catalogItemId: item.id, seatOrSlot: seat, idempotencyKey: key, couponCode: coupon.trim() || undefined } }));
    if (b) setBooking(b);
  };
  return (
    <Modal title={item.title} onClose={onClose}>
      {outcome ? (
        <Notice kind={outcome === 'SUCCEEDED' ? 'success' : 'warn'}>{outcome === 'SUCCEEDED' ? 'Booking confirmed.' : 'Payment failed; the seat was released.'}</Notice>
      ) : booking ? (
        <PaymentPanel paymentId={booking.paymentId} amount={booking.amount} onDone={setOutcome} />
      ) : (
        <Async state={seats}>
          {(list) => (
            <>
              <p className="muted">
                {item.location} · {formatDateTime(item.startsAt)} · {formatMoney(item.price)} · {list.length} seats left
              </p>
              <Field label="Seat">
                <select value={seat} onChange={(e) => setSeat(e.target.value)}>
                  <option value="">Select a seat…</option>
                  {list.slice(0, 300).map((s) => (
                    <option key={s}>{s}</option>
                  ))}
                </select>
              </Field>
              <Field label="Coupon code (optional)">
                <input value={coupon} onChange={(e) => setCoupon(e.target.value)} />
              </Field>
              <ErrorBox error={action.error} />
              <Button kind="primary" busy={action.busy} disabled={!seat} onClick={book}>
                Reserve and pay
              </Button>
            </>
          )}
        </Async>
      )}
    </Modal>
  );
}
