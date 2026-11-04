import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDate, toApiLocalDateTime } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';

export function AdminOffers() {
  const list = useFetch(() => api<Row[]>('/admin/offers'));
  const [creating, setCreating] = useState(false);
  const action = useAction();
  const paged = usePaged(list.data, 12);
  const toggle = async (path: string, active: boolean) => {
    await action.run(() => api(path, { method: 'PUT', body: { active } }));
    list.reload();
  };
  return (
    <>
      <PageHeader title="Offers & coupons" subtitle="Customers redeem coupons at checkout; usage limits are enforced in the database." actions={<Button kind="primary" onClick={() => setCreating(true)}>New offer + coupon</Button>} />
      <ErrorBox error={action.error} />
      <Card>
        <Async state={list}>
          {() => (
            <>
              <Table rows={paged.items} rowKey={(r) => `${r.id}-${r.couponId ?? 0}`} empty="No offers yet." columns={[
                { header: 'Offer', render: (r) => <>{r.name}<div className="muted small">{r.offerCode}</div></> },
                { header: 'Discount', render: (r) => `${r.discountPercent}%` },
                { header: 'Window', render: (r) => `${formatDate(r.startsAt)} → ${formatDate(r.endsAt)}` },
                { header: 'Offer', render: (r) => <Badge value={r.active ? 'ACTIVE' : 'SUSPENDED'} /> },
                { header: 'Coupon', render: (r) => r.couponCode ?? <span className="muted">none</span> },
                { header: 'Used', render: (r) => (r.couponCode ? `${r.usageCount}/${r.usageLimit}` : '') },
                { header: '', render: (r) => (
                  <div className="row">
                    <Button className="btn small" onClick={() => toggle(`/admin/offers/${r.id}/active`, !r.active)}>{r.active ? 'Disable offer' : 'Enable offer'}</Button>
                    {r.couponId && <Button className="btn small" onClick={() => toggle(`/admin/coupons/${r.couponId}/active`, !r.couponActive)}>{r.couponActive ? 'Disable coupon' : 'Enable coupon'}</Button>}
                  </div>) },
              ]} />
              <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
            </>
          )}
        </Async>
      </Card>
      {creating && <OfferForm onClose={(c) => (setCreating(false), c && list.reload())} />}
    </>
  );
}

function OfferForm({ onClose }: { onClose: (changed: boolean) => void }) {
  const [f, setF] = useState({ offerCode: '', name: '', discountPercent: '10', startsAt: '', endsAt: '', couponCode: '', usageLimit: '100' });
  const action = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const valid = f.offerCode && f.name && f.startsAt && f.endsAt && new Date(f.endsAt) > new Date(f.startsAt) && Number(f.discountPercent) > 0 && Number(f.discountPercent) <= 100;
  const save = async () => {
    const created = await action.run(() => api('/admin/offers', { method: 'POST', body: { offerCode: f.offerCode, name: f.name, discountPercent: Number(f.discountPercent), startsAt: toApiLocalDateTime(f.startsAt), endsAt: toApiLocalDateTime(f.endsAt), active: true } }));
    if (created && f.couponCode.trim()) {
      const ok = await action.run(() => api(`/admin/offers/${created.id}/coupons`, { method: 'POST', body: { couponCode: f.couponCode.trim(), usageLimit: Number(f.usageLimit) } }));
      if (!ok) return;
    }
    if (created) onClose(true);
  };
  return (
    <Modal title="New offer" onClose={() => onClose(false)}>
      <div className="form-grid">
        <Field label="Offer code"><input value={f.offerCode} onChange={set('offerCode')} /></Field>
        <Field label="Name"><input value={f.name} onChange={set('name')} /></Field>
        <Field label="Discount %"><input type="number" min="0.01" max="100" step="0.01" value={f.discountPercent} onChange={set('discountPercent')} /></Field>
        <Field label="Starts"><input type="datetime-local" value={f.startsAt} onChange={set('startsAt')} /></Field>
        <Field label="Ends" error={f.endsAt && f.startsAt && new Date(f.endsAt) <= new Date(f.startsAt) ? 'Must be after the start' : undefined}><input type="datetime-local" value={f.endsAt} onChange={set('endsAt')} /></Field>
        <Field label="Coupon code (optional)"><input value={f.couponCode} onChange={set('couponCode')} /></Field>
        <Field label="Usage limit"><input type="number" min="1" value={f.usageLimit} onChange={set('usageLimit')} /></Field>
      </div>
      <ErrorBox error={action.error} />
      <Notice kind="info">Coupons are limited to one redemption per customer.</Notice>
      <Button kind="primary" busy={action.busy} disabled={!valid} onClick={save}>Create</Button>
    </Modal>
  );
}
