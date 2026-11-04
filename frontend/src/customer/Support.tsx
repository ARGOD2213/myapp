import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDateTime } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, Notice, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';

const CATEGORIES = ['PAYMENT', 'RECHARGE', 'STREAMING', 'BOOKING', 'ACCOUNT', 'GENERAL'];

export function Support() {
  const tickets = useFetch(() => api<Row[]>('/support/tickets/me'));
  const [open, setOpen] = useState<number | null>(null);
  const paged = usePaged(tickets.data, 8);
  return (
    <>
      <PageHeader title="Support" subtitle="Raise a ticket and follow its progress." />
      <NewTicket onCreated={tickets.reload} />
      <Card title="My tickets">
        <Async state={tickets}>
          {() => (
            <>
              <Table
                rows={paged.items}
                rowKey={(r) => r.id}
                empty="You have not raised any tickets."
                columns={[
                  { header: 'Ticket', render: (r) => <a href="#t" onClick={(e) => (e.preventDefault(), setOpen(r.id))}>{r.ticketCode}</a> },
                  { header: 'Subject', render: (r) => r.subject },
                  { header: 'Category', render: (r) => r.category },
                  { header: 'Priority', render: (r) => <Badge value={r.priority} /> },
                  { header: 'Status', render: (r) => <Badge value={r.status} /> },
                  { header: 'Updated', render: (r) => formatDateTime(r.updatedAt) },
                ]}
              />
              <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
            </>
          )}
        </Async>
      </Card>
      {open !== null && <TicketDialog id={open} onClose={() => (setOpen(null), tickets.reload())} />}
    </>
  );
}

function NewTicket({ onCreated }: { onCreated: () => void }) {
  const [form, setForm] = useState({ subject: '', description: '', category: 'GENERAL', priority: 'NORMAL' });
  const action = useAction();
  const valid = form.subject.trim() && form.description.trim();
  const submit = async () => {
    const t = await action.run(() => api('/support/tickets', { method: 'POST', body: form }), 'Ticket created.');
    if (t) (setForm({ ...form, subject: '', description: '' }), onCreated());
  };
  return (
    <Card title="New ticket">
      <div className="form-grid">
        <Field label="Subject">
          <input maxLength={160} value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} />
        </Field>
        <Field label="Category">
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="Priority">
          <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>
            {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((c) => <option key={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Describe the problem">
        <textarea rows={3} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </Field>
      <ErrorBox error={action.error} />
      <Notice>{action.done}</Notice>
      <Button kind="primary" busy={action.busy} disabled={!valid} onClick={submit}>Submit ticket</Button>
    </Card>
  );
}

function TicketDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const detail = useFetch(() => api<Row>(`/support/tickets/${id}`), [id]);
  const history = useFetch(() => api<Row[]>(`/support/tickets/${id}/history`), [id]);
  const [body, setBody] = useState('');
  const action = useAction();
  const send = async () => {
    const r = await action.run(() => api(`/support/tickets/${id}/comments`, { method: 'POST', body: { body, internal: false } }));
    if (r !== undefined) (setBody(''), detail.reload(), history.reload());
  };
  return (
    <Modal title="Ticket" onClose={onClose}>
      <Async state={detail}>
        {(d) => (
          <>
            <h3 style={{ margin: 0 }}>{d.ticket.subject}</h3>
            <p className="muted small">{d.ticket.ticketCode} · <Badge value={d.ticket.status} /> · {d.ticket.category}</p>
            <p>{d.ticket.description}</p>
            {d.comments.map((c: Row) => (
              <div className="comment" key={c.id}>
                <div>{c.body}</div>
                <div className="muted small">{formatDateTime(c.createdAt)}</div>
              </div>
            ))}
            {!['CLOSED'].includes(d.ticket.status) && (
              <>
                <Field label="Add a comment"><textarea rows={2} value={body} onChange={(e) => setBody(e.target.value)} /></Field>
                <ErrorBox error={action.error} />
                <Button kind="primary" busy={action.busy} disabled={!body.trim()} onClick={send}>Send</Button>
              </>
            )}
          </>
        )}
      </Async>
      <h4>Timeline</h4>
      <Async state={history}>
        {(rows) => (
          <ul className="small">
            {rows.map((h, i) => <li key={i}>{formatDateTime(h.createdAt)} — {h.eventType}: {h.details}</li>)}
          </ul>
        )}
      </Async>
    </Modal>
  );
}
