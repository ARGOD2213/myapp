import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDateTime, formatMoney, toApiLocalDateTime } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Modal, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';

export function AdminCatalogue() {
  const [tab, setTab] = useState<'items' | 'bookings' | 'streaming'>('items');
  return (
    <>
      <PageHeader title="Bookings & streaming" subtitle="Manage schedules, review bookings and the streaming catalogue." />
      <div className="tabs">
        <button className={tab === 'items' ? 'on' : ''} onClick={() => setTab('items')}>Schedules</button>
        <button className={tab === 'bookings' ? 'on' : ''} onClick={() => setTab('bookings')}>Recent bookings</button>
        <button className={tab === 'streaming' ? 'on' : ''} onClick={() => setTab('streaming')}>Streaming content</button>
      </div>
      {tab === 'items' ? <Items /> : tab === 'bookings' ? <BookingList /> : <Streaming />}
    </>
  );
}

function Items() {
  const list = useFetch(() => api<Row[]>('/admin/booking-items'));
  const [edit, setEdit] = useState<Row | 'new' | null>(null);
  const paged = usePaged(list.data, 12);
  return (
    <Card title="Schedules" actions={<Button kind="primary" onClick={() => setEdit('new')}>New schedule</Button>}>
      <Async state={list}>
        {() => (
          <>
            <Table rows={paged.items} rowKey={(r) => r.id} empty="No schedules." columns={[
              { header: 'Type', render: (r) => <Badge value={r.itemType} /> }, { header: 'Title', render: (r) => <>{r.title}<div className="muted small">{r.itemCode}</div></> },
              { header: 'Starts', render: (r) => formatDateTime(r.startsAt) }, { header: 'Seats', render: (r) => `${r.claimed}/${r.capacity}` },
              { header: 'Price', className: 'num', render: (r) => formatMoney(r.price) }, { header: 'Open', render: (r) => <Badge value={r.available ? 'ACTIVE' : 'CANCELLED'} /> },
              { header: '', render: (r) => <Button className="btn small" onClick={() => setEdit(r)}>Edit</Button> },
            ]} />
            <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
          </>
        )}
      </Async>
      {edit && <ItemForm item={edit === 'new' ? null : edit} onClose={(c) => (setEdit(null), c && list.reload())} />}
    </Card>
  );
}

function ItemForm({ item, onClose }: { item: Row | null; onClose: (changed: boolean) => void }) {
  const [f, setF] = useState({ itemType: 'EVENT', itemCode: '', title: item?.title ?? '', location: '', startsAt: '', capacity: '50', price: String(item?.price ?? '0'), available: item?.available ?? true });
  const action = useAction();
  const set = (k: keyof typeof f, v: string | boolean) => setF({ ...f, [k]: v });
  const valid = f.title.trim() && Number(f.price) >= 0 && (item || (f.itemCode.trim() && f.startsAt && Number(f.capacity) > 0));
  const save = async () => {
    const r = await action.run(() => item
      ? api(`/admin/booking-items/${item.id}`, { method: 'PUT', body: { title: f.title, price: Number(f.price), available: f.available } })
      : api('/admin/booking-items', { method: 'POST', body: { itemType: f.itemType, itemCode: f.itemCode, title: f.title, location: f.location || undefined, startsAt: toApiLocalDateTime(f.startsAt), capacity: Number(f.capacity), price: Number(f.price) } }));
    if (r !== undefined) onClose(true);
  };
  return (
    <Modal title={item ? `Edit ${item.itemCode}` : 'New schedule'} onClose={() => onClose(false)}>
      <div className="form-grid">
        {!item && <Field label="Type"><select value={f.itemType} onChange={(e) => set('itemType', e.target.value)}><option>BUS</option><option>MOVIE</option><option>EVENT</option></select></Field>}
        {!item && <Field label="Code"><input value={f.itemCode} onChange={(e) => set('itemCode', e.target.value)} /></Field>}
        <Field label="Title"><input value={f.title} onChange={(e) => set('title', e.target.value)} /></Field>
        {!item && <Field label="Location"><input value={f.location} onChange={(e) => set('location', e.target.value)} /></Field>}
        {!item && <Field label="Starts (must be in the future)"><input type="datetime-local" value={f.startsAt} onChange={(e) => set('startsAt', e.target.value)} /></Field>}
        {!item && <Field label="Capacity"><input type="number" min="1" value={f.capacity} onChange={(e) => set('capacity', e.target.value)} /></Field>}
        <Field label="Price (₹)"><input type="number" min="0" step="0.01" value={f.price} onChange={(e) => set('price', e.target.value)} /></Field>
      </div>
      {item && <label className="row"><input type="checkbox" checked={f.available} onChange={(e) => set('available', e.target.checked)} /> Open for booking</label>}
      <ErrorBox error={action.error} />
      <Button kind="primary" busy={action.busy} disabled={!valid} onClick={save}>Save</Button>
    </Modal>
  );
}

function BookingList() {
  const list = useFetch(() => api<Row[]>('/admin/bookings'));
  const paged = usePaged(list.data, 15);
  return (
    <Card title="Most recent bookings (200)">
      <Async state={list}>
        {() => (
          <>
            <Table rows={paged.items} rowKey={(r) => r.id} empty="No bookings." columns={[
              { header: 'Code', render: (r) => r.bookingCode }, { header: 'Customer', render: (r) => r.customerId }, { header: 'What', render: (r) => `${r.title} (${r.itemType})` },
              { header: 'Seat', render: (r) => r.seatOrSlot }, { header: 'Amount', className: 'num', render: (r) => formatMoney(r.amount) },
              { header: 'Status', render: (r) => <Badge value={r.status} /> }, { header: 'Created', render: (r) => formatDateTime(r.createdAt) },
            ]} />
            <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
          </>
        )}
      </Async>
    </Card>
  );
}

function Streaming() {
  const list = useFetch(() => api<Row[]>('/admin/streaming-content'));
  const [edit, setEdit] = useState<Row | null>(null);
  const paged = usePaged(list.data, 12);
  return (
    <Card title="Streaming content">
      <Async state={list}>
        {() => (
          <>
            <Table rows={paged.items} rowKey={(r) => r.id} empty="No content." columns={[
              { header: 'Title', render: (r) => <>{r.title}<div className="muted small">{r.contentCode}</div></> }, { header: 'Category', render: (r) => r.category },
              { header: 'Type', render: (r) => r.contentType }, { header: 'Min', className: 'num', render: (r) => r.durationMinutes },
              { header: 'Live', render: (r) => <Badge value={r.active ? 'ACTIVE' : 'SUSPENDED'} /> }, { header: '', render: (r) => <Button className="btn small" onClick={() => setEdit(r)}>Edit</Button> },
            ]} />
            <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
          </>
        )}
      </Async>
      {edit && <ContentForm row={edit} onClose={(c) => (setEdit(null), c && list.reload())} />}
    </Card>
  );
}

function ContentForm({ row, onClose }: { row: Row; onClose: (changed: boolean) => void }) {
  const [title, setTitle] = useState(row.title);
  const [description, setDescription] = useState(row.description);
  const [active, setActive] = useState<boolean>(row.active);
  const action = useAction();
  const save = async () => {
    const r = await action.run(() => api(`/admin/streaming-content/${row.id}`, { method: 'PUT', body: { title, description, active } }));
    if (r !== undefined) onClose(true);
  };
  return (
    <Modal title={`Edit ${row.contentCode}`} onClose={() => onClose(false)}>
      <Field label="Title"><input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} /></Field>
      <Field label="Description"><textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
      <label className="row"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Visible to subscribers</label>
      <ErrorBox error={action.error} />
      <Button kind="primary" busy={action.busy} disabled={!title.trim() || !description.trim()} onClick={save}>Save</Button>
    </Modal>
  );
}
