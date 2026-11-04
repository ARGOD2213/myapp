import { useState } from 'react';
import { api, type Row } from '../api';
import { formatDateTime } from '../format';
import { Async, Badge, Button, Card, Empty, ErrorBox, Notice, PageHeader, Pager, Table, useAction, useDebounced, useFetch, usePaged } from '../ui';

export function Streaming() {
  const [q, setQ] = useState('');
  const [type, setType] = useState('');
  const dq = useDebounced(q);
  const content = useFetch(() => api<Row[]>('/streaming/content', { query: { q: dq, type } }), [dq, type]);
  const history = useFetch(() => api<Row[]>('/streaming/history/me'));
  const action = useAction();
  const [playing, setPlaying] = useState<Row | null>(null);
  const paged = usePaged(content.data, 9);
  const pagedH = usePaged(history.data, 8);

  const play = async (c: Row) => {
    const r = await action.run(() => api(`/streaming/content/${c.id}/play`, { method: 'POST', query: { progressSeconds: 0 } }));
    if (r) (setPlaying({ ...c, ...r }), history.reload());
  };

  return (
    <>
      <PageHeader title="Streaming" subtitle="Content included in your active subscription." />
      <ErrorBox error={action.error} />
      {action.error?.status === 403 && <Notice kind="info">Playback needs an active subscription that covers this category. See Subscriptions.</Notice>}
      {playing && (
        <Notice kind="info">
          Now playing <strong>{playing.title}</strong> (simulated playback; no video is hosted by ConnectOne).{' '}
          <button className="btn small" onClick={() => setPlaying(null)}>
            Stop
          </button>
        </Notice>
      )}
      <Card title="Catalogue">
        <div className="filters">
          <input className="search" placeholder="Search titles" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search content" />
          <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Content type">
            <option value="">All types</option>
            <option value="MOVIE">Movies</option>
            <option value="SERIES">Series</option>
            <option value="CLIP">Clips</option>
          </select>
        </div>
        <Async state={content}>
          {() =>
            paged.total === 0 ? (
              <Empty>No content found.</Empty>
            ) : (
              <>
                <div className="grid">
                  {paged.items.map((c) => (
                    <div className="card plan" key={c.id}>
                      <strong>{c.title}</strong>
                      <span className="muted small">
                        {c.category} · {c.contentType.toLowerCase()} · {c.durationMinutes} min · {c.rating}
                      </span>
                      <Button kind="primary" busy={action.busy} onClick={() => play(c)}>
                        ▶ Play
                      </Button>
                    </div>
                  ))}
                </div>
                <Pager page={paged.page} totalPages={paged.totalPages} total={paged.total} onChange={paged.setPage} />
              </>
            )
          }
        </Async>
      </Card>
      <Card title="Watch history">
        <Async state={history}>
          {() => (
            <>
              <Table
                rows={pagedH.items}
                rowKey={(r) => r.id}
                empty="You have not watched anything yet."
                columns={[
                  { header: 'Watched', render: (r) => formatDateTime(r.watchedAt) },
                  { header: 'Title', render: (r) => r.title },
                  { header: 'Code', render: (r) => <Badge value={r.contentCode} /> },
                  { header: 'Progress', render: (r) => `${Math.floor(r.progressSeconds / 60)} min` },
                ]}
              />
              <Pager page={pagedH.page} totalPages={pagedH.totalPages} total={pagedH.total} onChange={pagedH.setPage} />
            </>
          )}
        </Async>
      </Card>
    </>
  );
}
