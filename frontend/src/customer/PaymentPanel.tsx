import { api, idempotencyKey } from '../api';
import { formatMoney } from '../format';
import { Badge, Button, ErrorBox, Notice, useAction } from '../ui';

/**
 * Stand-in for the payment gateway page. The backend's simulated provider accepts the outcome the caller reports,
 * so "Pay" / "Fail" post a gateway callback to POST /payments/{id}/confirm exactly as a real gateway webhook would.
 */
export function PaymentPanel({ paymentId, amount, status, onDone }: { paymentId: number; amount?: number; status?: string; onDone: (status: string) => void }) {
  const action = useAction();
  const settle = async (outcome: 'SUCCEEDED' | 'FAILED') => {
    const p = await action.run(() => api(`/payments/${paymentId}/confirm`, { method: 'POST', body: { callbackId: idempotencyKey('cb'), outcome } }));
    if (p) onDone(p.status);
  };
  return (
    <div className="alert info">
      <div className="row">
        <strong>Payment #{paymentId}</strong>
        {amount !== undefined && <span>{formatMoney(amount)}</span>}
        <Badge value={status ?? 'INITIATED'} />
      </div>
      <p className="small">Simulated gateway: choose the outcome the provider should report.</p>
      <div className="row">
        <Button kind="primary" busy={action.busy} onClick={() => settle('SUCCEEDED')}>
          Pay now
        </Button>
        <Button kind="danger" busy={action.busy} onClick={() => settle('FAILED')}>
          Simulate failure
        </Button>
      </div>
      <ErrorBox error={action.error} />
      <Notice>{action.done}</Notice>
    </div>
  );
}
