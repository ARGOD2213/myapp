import { useState } from 'react';
import { api, idempotencyKey, type Row } from '../api';
import { formatDate, formatMoney } from '../format';
import { Async, Badge, Button, Card, Empty, ErrorBox, Modal, Notice, PageHeader, Table, useAction, useFetch } from '../ui';
import { PaymentPanel } from './PaymentPanel';

export function Subscriptions() {
  const plans = useFetch(() => api<Row[]>('/subscriptions/plans'));
  const mine = useFetch(() => api<Row[]>('/subscriptions/me'));
  const [payment, setPayment] = useState<Row | null>(null);
  const action = useAction();

  const buy = async (plan: Row) => {
    const s = await action.run(() => api('/subscriptions', { method: 'POST', body: { planId: plan.id, autoRenew: false, idempotencyKey: idempotencyKey('sub') } }));
    if (s) (setPayment(s), mine.reload());
  };
  const renew = async (sub: Row) => {
    const s = await action.run(() => api(`/subscriptions/${sub.id}/renew`, { method: 'POST', body: { idempotencyKey: idempotencyKey('renew') } }));
    if (s) (setPayment(s), mine.reload());
  };
  const cancel = async (sub: Row) => {
    if (window.confirm('Cancel this subscription?')) {
      await action.run(() => api(`/subscriptions/${sub.id}/cancel`, { method: 'POST' }), 'Subscription cancelled.');
      mine.reload();
    }
  };

  return (
    <>
      <PageHeader title="Subscriptions" subtitle="Streaming plans and their renewal state." />
      <ErrorBox error={action.error} />
      <Notice>{action.done}</Notice>
      <Card title="Your subscriptions">
        <Async state={mine}>
          {(rows) => (
            <Table
              rows={rows}
              rowKey={(r) => r.id}
              empty="You have no subscriptions yet."
              columns={[
                { header: 'Plan', render: (r) => r.planName },
                { header: 'Status', render: (r) => <Badge value={r.status} /> },
                { header: 'Started', render: (r) => formatDate(r.startsAt) },
                { header: 'Expires', render: (r) => formatDate(r.expiresAt) },
                { header: 'Auto-renew', render: (r) => (r.autoRenew ? 'On' : 'Off') },
                {
                  header: '',
                  render: (r) => (
                    <div className="row">
                      {['ACTIVE', 'EXPIRED', 'RENEWAL_FAILED'].includes(r.status) && (
                        <Button className="btn small" onClick={() => renew(r)}>
                          Renew
                        </Button>
                      )}
                      {r.status === 'PENDING' && (
                        <Button className="btn small" onClick={() => setPayment(r)}>
                          Pay
                        </Button>
                      )}
                      {['ACTIVE', 'PENDING', 'RENEWAL_FAILED'].includes(r.status) && (
                        <Button className="btn small danger" onClick={() => cancel(r)}>
                          Cancel
                        </Button>
                      )}
                    </div>
                  ),
                },
              ]}
            />
          )}
        </Async>
      </Card>
      <Card title="Available plans">
        <Async state={plans}>
          {(rows) =>
            rows.length === 0 ? (
              <Empty>No plans are available.</Empty>
            ) : (
              <div className="grid">
                {rows.map((p) => (
                  <div className="card plan" key={p.id}>
                    <strong>{p.name}</strong>
                    <span className="price">{formatMoney(p.price)}</span>
                    <span className="muted small">every {p.billingPeriodDays} days</span>
                    <Button kind="primary" onClick={() => buy(p)}>
                      Subscribe
                    </Button>
                  </div>
                ))}
              </div>
            )
          }
        </Async>
      </Card>
      {payment && (
        <Modal title="Complete subscription payment" onClose={() => (setPayment(null), mine.reload())}>
          <PaymentPanel paymentId={payment.paymentId} status="INITIATED" onDone={() => (setPayment(null), mine.reload())} />
        </Modal>
      )}
    </>
  );
}
