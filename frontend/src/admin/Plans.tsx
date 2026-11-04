import { useState } from 'react';
import { api, type Row } from '../api';
import { formatMoney } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, PageHeader, Table, useAction, useFetch } from '../ui';

type Kind = 'recharge' | 'subscription';

export function AdminPlans() {
  const [tab, setTab] = useState<Kind>('recharge');
  return (
    <>
      <PageHeader title="Plans" subtitle="Recharge and subscription plan catalogue." />
      <div className="tabs">
        <button className={tab === 'recharge' ? 'on' : ''} onClick={() => setTab('recharge')}>Recharge plans</button>
        <button className={tab === 'subscription' ? 'on' : ''} onClick={() => setTab('subscription')}>Subscription plans</button>
      </div>
      <PlanTable key={tab} kind={tab} />
    </>
  );
}

function PlanTable({ kind }: { kind: Kind }) {
  const list = useFetch(() => api<Row[]>(`/admin/${kind}-plans`), [kind]);
  const [edit, setEdit] = useState<Row | 'new' | null>(null);
  return (
    <Card title={kind === 'recharge' ? 'Recharge plans' : 'Subscription plans'} actions={<Button kind="primary" onClick={() => setEdit('new')}>New plan</Button>}>
      <Async state={list}>
        {(rows) => (
          <Table rows={rows} rowKey={(r) => r.id} empty="No plans." columns={[
            { header: 'Code', render: (r) => r.planCode }, { header: 'Name', render: (r) => r.name },
            { header: 'Price', className: 'num', render: (r) => formatMoney(r.price) },
            kind === 'recharge'
              ? { header: 'Validity / data / voice', render: (r) => `${r.validityDays}d · ${(r.dataAllowanceMb / 1024).toFixed(1)} GB · ${r.voiceMinutes} min` }
              : { header: 'Billing period', render: (r) => `${r.billingPeriodDays} days` },
            { header: 'Active', render: (r) => <Badge value={r.active ? 'ACTIVE' : 'SUSPENDED'} /> },
            { header: '', render: (r) => <Button className="btn small" onClick={() => setEdit(r)}>Edit</Button> },
          ]} />
        )}
      </Async>
      {edit && <PlanForm kind={kind} plan={edit === 'new' ? null : edit} onClose={(changed) => (setEdit(null), changed && list.reload())} />}
    </Card>
  );
}

function PlanForm({ kind, plan, onClose }: { kind: Kind; plan: Row | null; onClose: (changed: boolean) => void }) {
  const [f, setF] = useState({
    planCode: plan?.planCode ?? '', name: plan?.name ?? '', price: String(plan?.price ?? ''), active: plan?.active ?? true,
    validityDays: String(plan?.validityDays ?? 28), dataAllowanceMb: String(plan?.dataAllowanceMb ?? 1024), voiceMinutes: String(plan?.voiceMinutes ?? 100),
    billingPeriodDays: String(plan?.billingPeriodDays ?? 30),
  });
  const action = useAction();
  const set = (k: keyof typeof f, v: string | boolean) => setF({ ...f, [k]: v });
  const body = kind === 'recharge'
    ? { planCode: f.planCode, name: f.name, price: Number(f.price), validityDays: Number(f.validityDays), dataAllowanceMb: Number(f.dataAllowanceMb), voiceMinutes: Number(f.voiceMinutes), active: f.active }
    : { planCode: f.planCode, name: f.name, price: Number(f.price), billingPeriodDays: Number(f.billingPeriodDays), active: f.active };
  const valid = f.planCode.trim() && f.name.trim() && Number(f.price) > 0;
  const save = async () => {
    const path = `/admin/${kind}-plans${plan ? `/${plan.id}` : ''}`;
    const r = await action.run(() => api(path, { method: plan ? 'PUT' : 'POST', body }), 'Saved.');
    if (r !== undefined) onClose(true);
  };
  return (
    <Modal title={plan ? `Edit ${plan.planCode}` : 'New plan'} onClose={() => onClose(false)}>
      <div className="form-grid">
        <Field label="Code"><input value={f.planCode} onChange={(e) => set('planCode', e.target.value)} maxLength={32} /></Field>
        <Field label="Name"><input value={f.name} onChange={(e) => set('name', e.target.value)} maxLength={100} /></Field>
        <Field label="Price (₹)"><input type="number" step="0.01" min="0.01" value={f.price} onChange={(e) => set('price', e.target.value)} /></Field>
        {kind === 'recharge' ? (
          <>
            <Field label="Validity (days)"><input type="number" min="1" value={f.validityDays} onChange={(e) => set('validityDays', e.target.value)} /></Field>
            <Field label="Data (MB)"><input type="number" min="0" value={f.dataAllowanceMb} onChange={(e) => set('dataAllowanceMb', e.target.value)} /></Field>
            <Field label="Voice (minutes)"><input type="number" min="0" value={f.voiceMinutes} onChange={(e) => set('voiceMinutes', e.target.value)} /></Field>
          </>
        ) : (
          <Field label="Billing period (days)"><input type="number" min="1" value={f.billingPeriodDays} onChange={(e) => set('billingPeriodDays', e.target.value)} /></Field>
        )}
      </div>
      <label className="row"><input type="checkbox" checked={f.active} onChange={(e) => set('active', e.target.checked)} /> Active (visible to customers)</label>
      <ErrorBox error={action.error} />
      <Button kind="primary" busy={action.busy} disabled={!valid} onClick={save}>Save plan</Button>
    </Modal>
  );
}
