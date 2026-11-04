import { useEffect, useState } from 'react';
import { api, type Row } from '../api';
import { formatDate } from '../format';
import { Async, Badge, Button, Card, ErrorBox, Field, Notice, PageHeader, Table, useAction, useFetch } from '../ui';

export function Profile() {
  const profile = useFetch(() => api<Row>('/customers/me'));
  const numbers = useFetch(() => api<Row[]>('/mobile-numbers'));
  const [first, setFirst] = useState('');
  const [last, setLast] = useState('');
  const [lang, setLang] = useState('en');
  const [phone, setPhone] = useState('');
  const save = useAction();
  const add = useAction();
  const row = useAction();

  useEffect(() => {
    if (profile.data) (setFirst(profile.data.firstName), setLast(profile.data.lastName));
  }, [profile.data]);

  const saveProfile = async () => {
    await save.run(() => api('/customers/me', { method: 'PUT', body: { firstName: first, lastName: last, displayLanguage: lang } }), 'Profile saved.');
    profile.reload();
  };
  const addNumber = async () => {
    const n = await add.run(() => api('/mobile-numbers', { method: 'POST', body: { phoneNumber: phone } }), 'Number added.');
    if (n) (setPhone(''), numbers.reload());
  };
  const act = async (fn: () => Promise<unknown>, msg: string) => {
    await row.run(fn, msg);
    numbers.reload();
  };

  return (
    <>
      <PageHeader title="Profile & numbers" />
      <Card title="Profile">
        <Async state={profile}>
          {(p) => (
            <>
              <dl className="kv">
                <dt>Email</dt><dd>{p.email}</dd>
                <dt>Account</dt><dd><Badge value={p.accountStatus} /></dd>
                <dt>Member since</dt><dd>{formatDate(p.createdAt)}</dd>
              </dl>
              <div className="form-grid" style={{ marginTop: '1rem' }}>
                <Field label="First name"><input value={first} onChange={(e) => setFirst(e.target.value)} /></Field>
                <Field label="Last name"><input value={last} onChange={(e) => setLast(e.target.value)} /></Field>
                <Field label="Language">
                  <select value={lang} onChange={(e) => setLang(e.target.value)}>
                    <option value="en">English</option><option value="hi">हिन्दी</option><option value="ta">தமிழ்</option>
                  </select>
                </Field>
              </div>
              <ErrorBox error={save.error} />
              <Notice>{save.done}</Notice>
              <Button kind="primary" busy={save.busy} disabled={!first.trim() || !last.trim()} onClick={saveProfile}>Save profile</Button>
            </>
          )}
        </Async>
      </Card>
      <Card title="Mobile numbers">
        <ErrorBox error={row.error} />
        <Notice>{row.done}</Notice>
        <Async state={numbers}>
          {(rows) => (
            <Table
              rows={rows}
              rowKey={(r) => r.id}
              empty="No numbers on this account."
              columns={[
                { header: 'Number', render: (r) => r.phoneNumber },
                { header: 'Status', render: (r) => <Badge value={r.status} /> },
                { header: 'Added', render: (r) => formatDate(r.createdAt) },
                {
                  header: '',
                  render: (r) =>
                    r.status === 'DISCONNECTED' ? null : (
                      <div className="row">
                        {r.status === 'ACTIVE' && <Button className="btn small" onClick={() => act(() => api(`/mobile-numbers/${r.id}/primary`, { method: 'PUT' }), 'Primary number updated.')}>Make primary</Button>}
                        <Button className="btn small danger" onClick={() => window.confirm(`Disconnect ${r.phoneNumber}?`) && act(() => api(`/mobile-numbers/${r.id}`, { method: 'DELETE' }), 'Number disconnected.')}>Disconnect</Button>
                      </div>
                    ),
                },
              ]}
            />
          )}
        </Async>
        <h3>Add a number</h3>
        <div className="row">
          <input className="search grow" placeholder="+919876543210" value={phone} onChange={(e) => setPhone(e.target.value)} aria-label="New mobile number" />
          <Button kind="primary" busy={add.busy} disabled={!/^\+?[1-9][0-9]{7,14}$/.test(phone)} onClick={addNumber}>Add</Button>
        </div>
        <ErrorBox error={add.error} />
        <Notice>{add.done}</Notice>
      </Card>
    </>
  );
}
