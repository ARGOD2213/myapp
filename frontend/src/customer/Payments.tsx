import { useState } from 'react';
import { api, idempotencyKey, type Row } from '../api';
import { formatDateTime, formatMoney } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';
import { PaymentPanel } from './PaymentPanel';

export function Payments() {
  const payments = useFetch(() => api<Row[]>('/payments/me'));
  const refunds = useFetch(() => api<Row[]>('/refunds/me'));
  const [refunding, setRefunding] = useState<Row | null>(null);
  const [paying, setPaying] = useState<Row | null>(null);
  const reloadAll = () => (payments.reload(), refunds.reload());
  const pagedP = usePaged(payments.data, 10);
  const pagedR = usePaged(refunds.data, 8);

  return (
    <>
      <PageHeader title="Payments & refunds" subtitle="Every charge on your account and the status of any refund." />
      <Card title="Payment history">
        <Async state={payments}>
          {() => (
            <>
              <Table
                rows={pagedP.items}
                rowKey={(r) => r.id}
                empty="No payments yet."
                columns={[
                  { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                  { header: '#', render: (r) => r.id },
                  { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount, r.currency) },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  { header: 'Reference', render: (r) => r.providerReference ?? '—' },
                  {
                    header: '',
                    render: (r) =>
                      r.status === 'SUCCEEDED' ? (
                        <Button className="btn small" onClick={() => setRefunding(r)}>
                          Request refund
                        </Button>
                      ) : r.status === 'INITIATED' ? (
                        <Button className="btn small" onClick={() => setPaying(r)}>
                          Complete payment
                        </Button>
                      ) : null,
                  },
                ]}
              />
              <Pager page={pagedP.page} totalPages={pagedP.totalPages} total={pagedP.total} onChange={pagedP.setPage} />
            </>
          )}
        </Async>
      </Card>
      <Card title="Refund status">
        <Async state={refunds}>
          {() => (
            <>
              <Table
                rows={pagedR.items}
                rowKey={(r) => r.id}
                empty="No refunds."
                columns={[
                  { header: 'Date', render: (r) => formatDateTime(r.createdAt) },
                  { header: 'Payment', render: (r) => `#${r.paymentId}` },
                  { header: 'Refund', className: 'num', render: (r) => formatMoney(r.amount) },
                  { header: 'Of payment', className: 'num', render: (r) => formatMoney(r.paymentAmount) },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  { header: 'Reason', render: (r) => r.reason },
                ]}
              />
              <Pager page={pagedR.page} totalPages={pagedR.totalPages} total={pagedR.total} onChange={pagedR.setPage} />
            </>
          )}
        </Async>
      </Card>
      {refunding && <RefundDialog payment={refunding} onClose={() => setRefunding(null)} onDone={reloadAll} />}
      {paying && (
        <Modal title={`Complete payment #${paying.id}`} onClose={() => (setPaying(null), reloadAll())}>
          <PaymentPanel paymentId={paying.id} amount={paying.amount} status={paying.status} onDone={() => (setPaying(null), reloadAll())} />
        </Modal>
      )}
    </>
  );
}

function RefundDialog({ payment, onClose, onDone }: { payment: Row; onClose: () => void; onDone: () => void }) {
  const [amount, setAmount] = useState(String(payment.amount));
  const [reason, setReason] = useState('');
  const [key] = useState(() => idempotencyKey('refund'));
  const action = useAction();
  const invalid = !(Number(amount) > 0) || Number(amount) > Number(payment.amount) || !reason.trim();
  const submit = async () => {
    const r = await action.run(() => api(`/payments/${payment.id}/refunds`, { method: 'POST', body: { amount: Number(amount), reason: reason.trim(), idempotencyKey: key } }), 'Refund requested.');
    if (r) onDone();
  };
  return (
    <Modal title={`Refund payment #${payment.id}`} onClose={onClose}>
      <Field label="Amount" hint={`Up to ${formatMoney(payment.amount)}. Partial refunds are allowed.`} error={Number(amount) > Number(payment.amount) ? 'More than the payment amount' : undefined}>
        <input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
      </Field>
      <Field label="Reason">
        <textarea rows={3} maxLength={250} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
      <ErrorBox error={action.error} />
      {action.done ? <p className="alert success">{action.done}</p> : null}
      <Button kind="primary" busy={action.busy} disabled={invalid || !!action.done} onClick={submit}>
        Submit refund request
      </Button>
    </Modal>
  );
}
