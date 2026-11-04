import { useState, useSyncExternalStore } from 'react';
import { Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { api, getSession, isStaff, setSession, subscribeSession } from './api';
import { AdminCustomerDetail, AdminCustomers } from './admin/Customers';
import { AdminCatalogue } from './admin/Catalogue';
import { AdminDashboard } from './admin/Dashboard';
import { AdminOffers } from './admin/Offers';
import { AdminPayments, AdminRefunds } from './admin/Payments';
import { AdminPlans } from './admin/Plans';
import { AdminStaff } from './admin/Staff';
import { AdminAudit, AdminTickets } from './admin/Tickets';
import { Login, Register } from './customer/Auth';
import { Bookings } from './customer/Bookings';
import { Dashboard } from './customer/Dashboard';
import { Notifications } from './customer/Notifications';
import { Payments } from './customer/Payments';
import { Profile } from './customer/Profile';
import { Recharge } from './customer/Recharge';
import { Streaming } from './customer/Streaming';
import { Subscriptions } from './customer/Subscriptions';
import { Support } from './customer/Support';

const useSession = () => useSyncExternalStore(subscribeSession, getSession);

interface NavItem {
  to: string;
  label: string;
  roles?: string[];
}

const CUSTOMER_NAV: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/recharge', label: 'Recharge' },
  { to: '/payments', label: 'Payments & refunds' },
  { to: '/subscriptions', label: 'Subscriptions' },
  { to: '/bookings', label: 'Bookings' },
  { to: '/streaming', label: 'Streaming' },
  { to: '/notifications', label: 'Notifications' },
  { to: '/support', label: 'Support' },
  { to: '/profile', label: 'Profile & numbers' },
];

const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', roles: ['ADMIN'] },
  { to: '/admin/customers', label: 'Customers' },
  { to: '/admin/payments', label: 'Payments' },
  { to: '/admin/refunds', label: 'Refunds' },
  { to: '/admin/tickets', label: 'Support tickets' },
  { to: '/admin/plans', label: 'Plans', roles: ['ADMIN'] },
  { to: '/admin/offers', label: 'Offers & coupons', roles: ['ADMIN'] },
  { to: '/admin/catalogue', label: 'Bookings & streaming', roles: ['ADMIN'] },
  { to: '/admin/staff', label: 'Agents & roles', roles: ['ADMIN'] },
  { to: '/admin/audit', label: 'Audit log', roles: ['ADMIN'] },
];

function Shell({ items, title }: { items: NavItem[]; title: string }) {
  const session = useSession();
  const [open, setOpen] = useState(false);
  const logout = async () => {
    try {
      if (session) await api('/auth/logout', { method: 'POST', body: { refreshToken: session.refreshToken }, auth: false });
    } catch {
      /* the token is discarded locally either way */
    }
    setSession(null);
  };
  const visible = items.filter((i) => !i.roles || i.roles.some((r) => session?.roles.includes(r)));
  return (
    <div className="shell">
      <div className="topbar">
        <strong>ConnectOne</strong>
        <button className="btn small" onClick={() => setOpen(!open)} aria-expanded={open}>
          Menu
        </button>
      </div>
      <aside className={`sidebar ${open ? 'open' : ''}`} onClick={() => setOpen(false)}>
        <div className="brand">
          ConnectOne<small>{title}</small>
        </div>
        <nav className="nav">
          {visible.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === '/' || i.to === '/admin'}>
              {i.label}
            </NavLink>
          ))}
          {isStaff(session?.roles) && !title.startsWith('Back') && <NavLink to="/admin">Back office →</NavLink>}
          {title.startsWith('Back') && !session?.roles.includes('ADMIN') ? null : title.startsWith('Back') && <NavLink to="/">← Customer app</NavLink>}
          <h4>{session?.email ?? 'Signed in'}</h4>
          <a href="#logout" onClick={(e) => (e.preventDefault(), logout())}>
            Sign out
          </a>
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}

function RequireAuth() {
  const session = useSession();
  const location = useLocation();
  if (!session) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  return <Outlet />;
}

function RequireStaff() {
  const session = useSession();
  return isStaff(session?.roles) ? <Outlet /> : <Navigate to="/" replace />;
}

function RequireAdmin() {
  const session = useSession();
  return session?.roles.includes('ADMIN') ? <Outlet /> : <Navigate to="/admin/customers" replace />;
}

function Home() {
  const session = useSession();
  // staff accounts have no customer profile: land them in the back office
  return isStaff(session?.roles) ? <Navigate to={session?.roles.includes('ADMIN') ? '/admin' : '/admin/customers'} replace /> : <Dashboard />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<RequireAuth />}>
        <Route element={<Shell items={CUSTOMER_NAV} title="Customer" />}>
          <Route path="/" element={<Home />} />
          <Route path="/recharge" element={<Recharge />} />
          <Route path="/payments" element={<Payments />} />
          <Route path="/subscriptions" element={<Subscriptions />} />
          <Route path="/bookings" element={<Bookings />} />
          <Route path="/streaming" element={<Streaming />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/support" element={<Support />} />
          <Route path="/profile" element={<Profile />} />
        </Route>
        <Route element={<RequireStaff />}>
          <Route element={<Shell items={ADMIN_NAV} title="Back office" />}>
            <Route path="/admin/customers" element={<AdminCustomers />} />
            <Route path="/admin/customers/:id" element={<AdminCustomerDetail />} />
            <Route path="/admin/payments" element={<AdminPayments />} />
            <Route path="/admin/refunds" element={<AdminRefunds />} />
            <Route path="/admin/tickets" element={<AdminTickets />} />
            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/plans" element={<AdminPlans />} />
              <Route path="/admin/offers" element={<AdminOffers />} />
              <Route path="/admin/catalogue" element={<AdminCatalogue />} />
              <Route path="/admin/staff" element={<AdminStaff />} />
              <Route path="/admin/audit" element={<AdminAudit />} />
            </Route>
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
