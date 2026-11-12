import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  ArrowUpRight,
  ClipboardList,
  Flag,
  LayoutDashboard,
  LogOut,
  Menu,
  PackageSearch,
  ShieldCheck,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import { useAdmin } from '../context/AdminContext';
import { initials } from '../lib/format';
import Logo from './Logo';
import './Admin.css';

const NAV_ITEMS = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Students', icon: Users },
  { to: '/admin/posts', label: 'Listings', icon: PackageSearch },
  { to: '/admin/moderation', label: 'Moderation', icon: Sparkles },
  { to: '/admin/reports', label: 'Reports', icon: Flag },
  { to: '/admin/audit-log', label: 'Audit trail', icon: ClipboardList, superOnly: true },
];

function pageName(pathname) {
  if (pathname.startsWith('/admin/users/')) return 'Student detail';
  if (pathname.startsWith('/admin/users')) return 'Students';
  if (pathname.startsWith('/admin/posts/')) return 'Listing detail';
  if (pathname.startsWith('/admin/posts')) return 'Listings';
  if (pathname.startsWith('/admin/moderation')) return 'Moderation queue';
  if (pathname.startsWith('/admin/reports')) return 'Reports';
  if (pathname.startsWith('/admin/audit-log')) return 'Audit trail';
  return 'Overview';
}

export default function AdminLayout() {
  const { admin, isSuperAdmin, signOut } = useAdmin();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const handleLogout = () => {
    signOut();
    navigate('/admin/login', { replace: true });
  };

  const navItems = NAV_ITEMS.filter((item) => !item.superOnly || isSuperAdmin);

  return (
    <div className="admin-shell">
      <aside className={`admin-sidebar ${mobileOpen ? 'open' : ''}`}>
        <div className="admin-brand-row">
          <Logo to="/admin" />
          <span className="admin-brand-tag">HQ</span>
          <button
            type="button"
            className="admin-sidebar-close"
            onClick={() => setMobileOpen(false)}
            aria-label="Close menu"
          >
            <X size={18} />
          </button>
        </div>

        <div className="admin-hello">
          <span className="admin-hello-icon"><ShieldCheck size={16} /></span>
          <div>
            <strong>Keep campus kind</strong>
            <small>Moderate with care &amp; context.</small>
          </div>
        </div>

        <nav className="admin-nav" aria-label="Admin navigation">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => `admin-nav-link ${isActive ? 'active' : ''}`}
            >
              <span className="admin-nav-icon"><Icon size={18} /></span>
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="admin-sidebar-foot">
          <Link to="/" className="admin-back-link">
            <ArrowUpRight size={16} /> Back to marketplace
          </Link>
          <div className="admin-account">
            <span className="admin-avatar">{initials(admin?.username || 'A')}</span>
            <div className="admin-account-copy">
              <strong>{admin?.username || 'Admin'}</strong>
              <small>{isSuperAdmin ? 'Super admin' : 'Moderator'}</small>
            </div>
            <button
              type="button"
              className="admin-icon-btn"
              onClick={handleLogout}
              aria-label="Log out of admin"
              title="Log out"
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      <AnimatePresence>
        {mobileOpen && (
          <motion.button
            type="button"
            className="admin-scrim"
            aria-label="Close menu"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setMobileOpen(false)}
          />
        )}
      </AnimatePresence>

      <div className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-left">
            <button
              type="button"
              className="admin-menu-btn"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu size={20} />
            </button>
            <div className="admin-breadcrumb">
              <span>Admin HQ</span>
              <i>/</i>
              <strong>{pageName(location.pathname)}</strong>
            </div>
          </div>

          <div className="admin-topbar-right">
            <span className="admin-role-chip">
              <ShieldCheck size={14} />
              {isSuperAdmin ? 'Super admin' : 'Moderator'}
            </span>
            <span className="admin-avatar sm">{initials(admin?.username || 'A')}</span>
          </div>
        </header>

        <motion.main
          key={location.pathname}
          className="admin-content"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
        >
          <Outlet />
        </motion.main>
      </div>
    </div>
  );
}
