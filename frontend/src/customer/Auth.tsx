import { type FormEvent, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, getSession, setSession } from '../api';
import { Button, Card, ErrorBox, Field, Notice, useAction } from '../ui';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function finishLogin(t: { accessToken: string; refreshToken: string; roles: string[] }, email: string) {
  setSession({ accessToken: t.accessToken, refreshToken: t.refreshToken, roles: t.roles, email });
}

export function Login() {
  const nav = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);
  const action = useAction();
  if (getSession()) return <Navigate to={from} replace />;

  const emailError = touched && !EMAIL.test(email) ? 'Enter a valid email address' : undefined;
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!EMAIL.test(email) || !password) return;
    const t = await action.run(() => api('/auth/login', { method: 'POST', body: { email, password }, auth: false }));
    if (t) {
      finishLogin(t, email);
      nav(from, { replace: true });
    }
  };
  return (
    <div className="auth">
      <Card title="Sign in to ConnectOne">
        <form onSubmit={submit} noValidate>
          <Field label="Email" error={emailError}>
            <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
          </Field>
          <Field label="Password" error={touched && !password ? 'Password is required' : undefined}>
            <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <ErrorBox error={action.error} />
          <Button kind="primary" busy={action.busy} type="submit">
            Sign in
          </Button>
        </form>
        <p className="muted small">
          New to ConnectOne? <Link to="/register">Create an account</Link>
        </p>
      </Card>
    </div>
  );
}

export function Register() {
  const nav = useNavigate();
  const [form, setForm] = useState({ email: '', password: '', firstName: '', lastName: '', phoneNumber: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [challenge, setChallenge] = useState<{ id: string; devOtp?: string } | null>(null);
  const [code, setCode] = useState('');
  const action = useAction();
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const validate = () => {
    const e: Record<string, string> = {};
    if (!EMAIL.test(form.email)) e.email = 'Enter a valid email address';
    if (form.password.length < 12 || !/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password) || !/[0-9]/.test(form.password))
      e.password = 'At least 12 characters with upper, lower case and a number';
    if (!form.firstName.trim()) e.firstName = 'Required';
    if (!form.lastName.trim()) e.lastName = 'Required';
    if (!/^\+?[1-9][0-9]{7,14}$/.test(form.phoneNumber)) e.phoneNumber = 'Use international format, e.g. +919876543210';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const register = async (ev: FormEvent) => {
    ev.preventDefault();
    if (!validate()) return;
    const r = await action.run(() => api('/auth/register', { method: 'POST', body: form, auth: false }));
    if (r) setChallenge({ id: r.challengeId, devOtp: r.developmentOtp });
  };
  const verify = async (ev: FormEvent) => {
    ev.preventDefault();
    const t = await action.run(() => api('/auth/verify-otp', { method: 'POST', body: { challengeId: challenge!.id, code }, auth: false }));
    if (t) {
      finishLogin(t, form.email);
      nav('/', { replace: true });
    }
  };

  if (challenge) {
    return (
      <div className="auth">
        <Card title="Verify your number">
          <p className="muted">We sent a 6-digit code (simulated provider).</p>
          {challenge.devOtp && <Notice kind="info">Development mode: your code is <strong>{challenge.devOtp}</strong></Notice>}
          <form onSubmit={verify}>
            <Field label="One-time code">
              <input inputMode="numeric" maxLength={6} pattern="[0-9]{6}" value={code} onChange={(e) => setCode(e.target.value)} autoFocus />
            </Field>
            <ErrorBox error={action.error} />
            <Button kind="primary" busy={action.busy} type="submit" disabled={code.length !== 6}>
              Verify and continue
            </Button>
          </form>
        </Card>
      </div>
    );
  }
  return (
    <div className="auth">
      <Card title="Create your ConnectOne account">
        <form onSubmit={register} noValidate>
          <div className="form-grid">
            <Field label="First name" error={errors.firstName}>
              <input value={form.firstName} onChange={set('firstName')} autoComplete="given-name" />
            </Field>
            <Field label="Last name" error={errors.lastName}>
              <input value={form.lastName} onChange={set('lastName')} autoComplete="family-name" />
            </Field>
          </div>
          <Field label="Email" error={errors.email}>
            <input type="email" value={form.email} onChange={set('email')} autoComplete="email" />
          </Field>
          <Field label="Mobile number" error={errors.phoneNumber}>
            <input value={form.phoneNumber} onChange={set('phoneNumber')} autoComplete="tel" placeholder="+919876543210" />
          </Field>
          <Field label="Password" error={errors.password}>
            <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" />
          </Field>
          <ErrorBox error={action.error} />
          <Button kind="primary" busy={action.busy} type="submit">
            Register
          </Button>
        </form>
        <p className="muted small">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </Card>
    </div>
  );
}
