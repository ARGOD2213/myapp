import { useState } from 'react';
import { api, getSession, type Row } from '../api';
import { formatDateTime } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useFetch } from '../ui';

// Mirrors SupportService's transition table (the server remains the authority).
const NEXT: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS', 'WAITING'],
  IN_PROGRESS: ['WAITING', 'RESOLVED'],
  WAITING: ['IN_PROGRESS', 'RESOLVED'],
  RESOLVED: ['IN_PROGRESS', 'CLOSED'],
  CLOSED: [],
};

export function AdminTickets() {
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const size = 20;
  const list = useFetch(() => api<Row[]>('/support/tickets', { query: { status, priority, category, page, size } }), [status, priority, category, page]);
  const [open, setOpen] = useState<number | null>(null);
  const reset = (fn: () => void) => (fn(), setPage(0));
  return (
    <>
      <PageHeader title="Support tickets" subtitle="Work the queue: assign, comment, move through the workflow." />
      <Card>
        <div className="filters">
          <select value={status} onChange={(e) => reset(() => setStatus(e.target.value))} aria-label="Status">
            <option value="">All statuses</option>{Object.keys(NEXT).map((s) => <option key={s}>{s}</option>)}
          </select>
          <select value={priority} onChange={(e) => reset(() => setPriority(e.target.value))} aria-label="Priority">
            <option value="">All priorities</option>{['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((s) => <option key={s}>{s}</option>)}
          </select>
          <select value={category} onChange={(e) => reset(() => setCategory(e.target.value))} aria-label="Category">
            <option value="">All categories</option>{['PAYMENT', 'RECHARGE', 'STREAMING', 'BOOKING', 'ACCOUNT', 'GENERAL'].map((s) => <option key={s}>{s}</option>)}
          </select>
        </div>
        <Async state={list}>
          {(rows) => (
            <>
              <Table rows={rows} rowKey={(r) => r.id} empty="No tickets match." columns={[
                { header: 'Ticket', render: (r) => <a href="#t" onClick={(e) => (e.preventDefault(), setOpen(r.id))}>{r.ticketCode}</a> },
                { header: 'Subject', render: (r) => r.subject }, { header: 'Customer', render: (r) => r.customerId },
                { header: 'Priority', render: (r) => <Badge value={r.priority} /> }, { header: 'Status', render: (r) => <Badge value={r.status} /> },
                { header: 'Assignee', render: (r) => (r.assignedUserId ? `#${r.assignedUserId}` : <span className="muted">unassigned</span>) },
                { header: 'Updated', render: (r) => formatDateTime(r.updatedAt) },
              ]} />
              <Pager page={page} totalPages={rows.length === size ? page + 2 : page + 1} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
      {open !== null && <TicketWorkbench id={open} onClose={() => (setOpen(null), list.reload())} />}
    </>
  );
}

function TicketWorkbench({ id, onClose }: { id: number; onClose: () => void }) {
  const detail = useFetch(() => api<Row>(`/support/tickets/${id}`), [id]);
  const history = useFetch(() => api<Row[]>(`/support/tickets/${id}/history`), [id]);
  const agents = useFetch(() => (getSession()?.roles.includes('ADMIN') ? api<Row[]>('/admin/support-agents') : Promise.resolve([])));
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);
  const action = useAction();
  const refresh = () => (detail.reload(), history.reload());

  const comment = async () => {
    const r = await action.run(() => api(`/support/tickets/${id}/comments`, { method: 'POST', body: { body, internal } }));
    if (r !== undefined) (setBody(''), refresh());
  };
  const move = async (status: string) => {
    await action.run(() => api(`/support/tickets/${id}/status`, { method: 'PUT', body: { status } }), `Moved to ${status}.`);
    refresh();
  };
  const assign = async (agentUserId: number) => {
    await action.run(() => api(`/support/tickets/${id}/assignment`, { method: 'PUT', body: { agentUserId } }), 'Assigned.');
    refresh();
  };
  return (
    <Modal title="Ticket workbench" onClose={onClose}>
      <Async state={detail}>
        {(d) => (
          <>
            <h3 style={{ margin: 0 }}>{d.ticket.subject}</h3>
            <p className="muted small">{d.ticket.ticketCode} · customer {d.ticket.customerId} · <Badge value={d.ticket.priority} /> · <Badge value={d.ticket.status} /></p>
            <p>{d.ticket.description}</p>
            <div className="row">
              {(NEXT[d.ticket.status] ?? []).map((s) => <Button key={s} className="btn small" onClick={() => move(s)}>→ {s.replace('_', ' ').toLowerCase()}</Button>)}
              {(agents.data?.length ?? 0) > 0 && (
                <select defaultValue="" onChange={(e) => e.target.value && assign(Number(e.target.value))} aria-label="Assign to agent">
                  <option value="">Assign to…</option>
                  {agents.data!.map((a) => <option key={a.id} value={a.id}>{a.email}</option>)}
                </select>
              )}
            </div>
            <ErrorBox error={action.error} />
            <Notice>{action.done}</Notice>
            {d.comments.map((c: Row) => (
              <div className={`comment ${c.isInternal ? 'internal' : ''}`} key={c.id}>
                {c.isInternal && <Badge value="HIGH" />} {c.body}
                <div className="muted small">user #{c.authorUserId} · {formatDateTime(c.createdAt)}</div>
              </div>
            ))}
            {d.ticket.status !== 'CLOSED' && (
              <>
                <Field label="Reply"><textarea rows={2} value={body} onChange={(e) => setBody(e.target.value)} /></Field>
                <label className="row small"><input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} /> Internal note (hidden from the customer)</label>
                <Button kind="primary" busy={action.busy} disabled={!body.trim()} onClick={comment}>Post</Button>
              </>
            )}
          </>
        )}
      </Async>
      <h4>Timeline</h4>
      <Async state={history}>{(rows) => <ul className="small">{rows.map((h, i) => <li key={i}>{formatDateTime(h.createdAt)} — {h.eventType}: {h.details}</li>)}</ul>}</Async>
    </Modal>
  );
}

export function AdminAudit() {
  const [page, setPage] = useState(0);
  const list = useFetch(() => api<Row[]>('/admin/audit', { query: { page, size: 25 } }), [page]);
  return (
    <>
      <PageHeader title="Audit log" subtitle="Administrative and business events. Quote the correlation id when tracing a request in the API logs." />
      <Card>
        <Async state={list}>
          {(rows) => (
            <>
              <Table rows={rows} rowKey={(r) => r.id} empty="No audit events." columns={[
                { header: 'When', render: (r) => formatDateTime(r.createdAt) }, { header: 'Actor', render: (r) => (r.actorUserId ? `#${r.actorUserId}` : 'system') },
                { header: 'Event', render: (r) => r.eventType }, { header: 'Entity', render: (r) => `${r.entityType} ${r.entityId ?? ''}` },
                { header: 'Correlation id', render: (r) => <code className="small">{r.correlationId ?? '—'}</code> },
              ]} />
              <Pager page={page} totalPages={rows.length === 25 ? page + 2 : page + 1} onChange={setPage} />
            </>
          )}
        </Async>
      </Card>
    </>
  );
}
