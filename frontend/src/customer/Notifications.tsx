import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDateTime } from '../format';
import { Async, Badge, Button, Card, ErrorBox, PageHeader, Pager, Table, useAction, useFetch, usePaged } from '../ui';

export function Notifications() {
  const list = useFetch(() => api<Row[]>('/notifications/me'));
  const [unreadOnly, setUnreadOnly] = useState(false);
  const action = useAction();
  const rows = (list.data ?? []).filter((n) => !unreadOnly || !n.readAt);
  const paged = usePaged(rows, 10);

  const markRead = async (n: Row) => {
    await action.run(() => api(`/notifications/${n.id}/read`, { method: 'POST' }));
    list.reload();
  };
  return (
    <>
      <PageHeader title="Notifications" subtitle="Your most recent 200 messages." actions={<label className="row small"><input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /> Unread only</label>} />
      <ErrorBox error={action.error} />
      <Card>
        <Async state={list}>
          {() => (
            <>
              <Table
                rows={paged.items}
                rowKey={(r) => r.id}
                empty={unreadOnly ? 'No unread notifications.' : 'No notifications yet.'}
                columns={[
                  { header: 'When', render: (r) => formatDateTime(r.createdAt) },
                  { header: 'Channel', render: (r) => <Badge value={r.channel} /> },
                  { header: 'Message', render: (r) => <span style={{ fontWeight: r.readAt ? 400 : 700 }}>{r.subject ?? r.message}<div className="muted small">{r.message}</div></span> },
                  { header: 'Delivery', render: (r) => <Badge value={r.status} /> },
                  { header: '', render: (r) => (!r.readAt ? <Button className="btn small" onClick={() => markRead(r)}>Mark read</Button> : <span className="muted small">Read</span>) },
                ]}
              />
              <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
            </>
          )}
        </Async>
      </Card>
    </>
  );
}
