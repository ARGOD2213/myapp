import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDate } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';

export function AdminStaff() {
  const [tab, setTab] = useState<'agents' | 'roles'>('agents');
  return (
    <>
      <PageHeader title="Agents & roles" subtitle="Support-agent accounts, roles and their permissions." />
      <div className="tabs">
        <button className={tab === 'agents' ? 'on' : ''} onClick={() => setTab('agents')}>Support agents</button>
        <button className={tab === 'roles' ? 'on' : ''} onClick={() => setTab('roles')}>Roles & permissions</button>
      </div>
      {tab === 'agents' ? <Agents /> : <Roles />}
    </>
  );
}

function Agents() {
  const list = useFetch(() => api<Row[]>('/admin/support-agents'));
  const [creating, setCreating] = useState(false);
  const action = useAction();
  const paged = usePaged(list.data, 12);
  const setStatus = async (a: Row, status: string) => {
    await action.run(() => api(`/admin/support-agents/${a.id}/status`, { method: 'PUT', body: { status } }));
    list.reload();
  };
  return (
    <Card title="Support agents" actions={<Button kind="primary" onClick={() => setCreating(true)}>New agent</Button>}>
      <ErrorBox error={action.error} />
      <Async state={list}>
        {() => (
          <>
            <Table rows={paged.items} rowKey={(r) => r.id} empty="No agents." columns={[
              { header: 'ID', render: (r) => r.id }, { header: 'Email', render: (r) => r.email }, { header: 'Status', render: (r) => <Badge value={r.accountStatus} /> },
              { header: 'Created', render: (r) => formatDate(r.createdAt) },
              { header: '', render: (r) => r.accountStatus === 'ACTIVE'
                ? <Button className="btn small danger" onClick={() => setStatus(r, 'DISABLED')}>Disable</Button>
                : <Button className="btn small" onClick={() => setStatus(r, 'ACTIVE')}>Enable</Button> },
            ]} />
            <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
          </>
        )}
      </Async>
      {creating && <AgentForm onClose={(c) => (setCreating(false), c && list.reload())} />}
    </Card>
  );
}

function AgentForm({ onClose }: { onClose: (changed: boolean) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const action = useAction();
  const valid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && password.length >= 12;
  const save = async () => {
    const r = await action.run(() => api('/admin/support-agents', { method: 'POST', body: { email, password } }));
    if (r) onClose(true);
  };
  return (
    <Modal title="New support agent" onClose={() => onClose(false)}>
      <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <Field label="Initial password" hint="At least 12 characters. Share it securely; the agent should change it."><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" /></Field>
      <ErrorBox error={action.error} />
      <Button kind="primary" busy={action.busy} disabled={!valid} onClick={save}>Create agent</Button>
    </Modal>
  );
}

function Roles() {
  const roles = useFetch(() => api<Row[]>('/admin/roles'));
  const permissions = useFetch(() => api<Row[]>('/admin/permissions'));
  const action = useAction();
  const grant = async (roleId: number, permissionId: number) => {
    await action.run(() => api(`/admin/roles/${roleId}/permissions`, { method: 'POST', body: { permissionId } }), 'Permission granted.');
    roles.reload();
  };
  return (
    <Card title="Roles & permissions">
      <ErrorBox error={action.error} />
      <Notice>{action.done}</Notice>
      <Async state={roles}>
        {(rows) => {
          const byRole = new Map<number, { code: string; perms: Row[] }>();
          rows.forEach((r) => {
            const g = byRole.get(r.roleId) ?? { code: r.roleCode, perms: [] as Row[] };
            if (r.permissionCode) g.perms.push(r);
            byRole.set(r.roleId, g);
          });
          return (
            <div className="stack">
              {[...byRole.entries()].map(([roleId, g]) => {
                const missing = (permissions.data ?? []).filter((p) => !g.perms.some((x) => x.permissionId === p.id));
                return (
                  <div key={roleId}>
                    <strong>{g.code}</strong>
                    <div className="row" style={{ margin: '.4rem 0' }}>
                      {g.perms.map((p) => <Badge key={p.permissionId} value={p.permissionCode} />)}
                      {g.perms.length === 0 && <span className="muted small">no permissions</span>}
                    </div>
                    {missing.length > 0 && (
                      <select defaultValue="" aria-label={`Grant permission to ${g.code}`} onChange={(e) => e.target.value && window.confirm(`Grant to ${g.code}?`) && grant(roleId, Number(e.target.value))}>
                        <option value="">Grant permission…</option>
                        {missing.map((p) => <option key={p.id} value={p.id}>{p.permissionCode}</option>)}
                      </select>
                    )}
                  </div>
                );
              })}
            </div>
          );
        }}
      </Async>
    </Card>
  );
}
