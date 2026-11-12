import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Bookmark,
  Home,
  Inbox,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  Plus,
  Settings,
  UserRound,
  X,
} from 'lucide-react';
import Logo from './Logo';
import { useAuth } from '../context/AuthContext';
import useUnreadCount from '../hooks/useUnreadCount';
import { initials } from '../lib/format';
import './Navbar.css';

const userLinks = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/feed', label: 'Browse', icon: Home },
  { to: '/saved', label: 'Wishlist', icon: Bookmark },
  { to: '/interests', label: 'Inbox', icon: Inbox, badge: true },
  { to: '/my-posts', label: 'My listings', icon: Package },
];

export default function Navbar() {
  const { user, signOut } = useAuth();
  const { count } = useUnreadCount(Boolean(user));
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    setMenuOpen(false);
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const onClick = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleLogout = () => {
    signOut();
    navigate('/');
  };

  return (
    <header className="site-nav">
      <div className="nav-inner">
        <Logo />

        <nav className="nav-links" aria-label="Main navigation">
          {user ? (
            userLinks.map(({ to, label, icon: Icon, badge }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
              >
                <Icon size={17} />
                <span>{label}</span>
                {badge && count > 0 && <span className="nav-badge">{count > 9 ? '9+' : count}</span>}
              </NavLink>
            ))
          ) : (
            <>
              <Link to="/#why" className="nav-link">
                Why EExchange
              </Link>
              <Link to="/#categories" className="nav-link">
                Categories
              </Link>
              <Link to="/#how" className="nav-link">
                How it works
              </Link>
            </>
          )}
        </nav>

        <div className="nav-actions">
          {user ? (
            <>
              <Link to="/sell" className="btn btn-primary btn-sm nav-sell">
                <Plus size={16} /> Sell something
              </Link>

              <div className="nav-user-wrap" ref={menuRef}>
                <button
                  type="button"
                  className="nav-user"
                  onClick={() => setMenuOpen((open) => !open)}
                  aria-expanded={menuOpen}
                  aria-label="Open account menu"
                >
                  <span className="nav-avatar">{initials(user.name)}</span>
                  <span className="nav-user-copy">
                    <strong>{user.name?.split(' ')[0] || 'Student'}</strong>
                    <small>{user.collegeName || 'Campus verified'}</small>
                  </span>
                </button>

                <AnimatePresence>
                  {menuOpen && (
                    <motion.div
                      className="user-menu"
                      initial={{ opacity: 0, y: 8, scale: 0.97 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 6, scale: 0.98 }}
                      transition={{ duration: 0.18 }}
                    >
                      <div className="user-menu-head">
                        <span className="nav-avatar lg">{initials(user.name)}</span>
                        <div>
                          <strong>{user.name}</strong>
                          <small>{user.email}</small>
                        </div>
                      </div>
                      <Link to="/dashboard" className="user-menu-item">
                        <LayoutDashboard size={16} /> Dashboard
                      </Link>
                      <Link to="/profile" className="user-menu-item">
                        <UserRound size={16} /> My profile
                      </Link>
                      <Link to="/profile?tab=security" className="user-menu-item">
                        <Settings size={16} /> Settings
                      </Link>
                      <Link to="/my-posts" className="user-menu-item">
                        <Package size={16} /> My listings
                      </Link>
                      <button type="button" className="user-menu-item danger" onClick={handleLogout}>
                        <LogOut size={16} /> Log out
                      </button>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </>
          ) : (
            <>
              <Link to="/auth" className="btn btn-ghost btn-sm nav-login">
                Log in
              </Link>
              <Link to="/auth?mode=signup" className="btn btn-primary btn-sm nav-join">
                Join free
              </Link>
            </>
          )}

          <button
            type="button"
            className="icon-btn mobile-toggle"
            onClick={() => setMobileOpen((open) => !open)}
            aria-label="Toggle menu"
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            className="mobile-menu"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="mobile-menu-inner">
              {user ? (
                <>
                  <div className="mobile-user">
                    <span className="nav-avatar">{initials(user.name)}</span>
                    <div>
                      <strong>{user.name}</strong>
                      <small>{user.collegeName}</small>
                    </div>
                  </div>
                  {userLinks.map(({ to, label, icon: Icon, badge }) => (
                    <NavLink key={to} to={to} className="mobile-link">
                      <Icon size={18} />
                      {label}
                      {badge && count > 0 && <span className="nav-badge">{count}</span>}
                    </NavLink>
                  ))}
                  <NavLink to="/sell" className="btn btn-primary btn-block">
                    <Plus size={16} /> Sell something
                  </NavLink>
                  <NavLink to="/profile" className="mobile-link">
                    <UserRound size={18} /> Profile & settings
                  </NavLink>
                  <button type="button" className="mobile-link danger" onClick={handleLogout}>
                    <LogOut size={18} /> Log out
                  </button>
                </>
              ) : (
                <>
                  <Link to="/#why" className="mobile-link">Why EExchange</Link>
                  <Link to="/#categories" className="mobile-link">Categories</Link>
                  <Link to="/#how" className="mobile-link">How it works</Link>
                  <Link to="/auth" className="btn btn-light btn-block">Log in</Link>
                  <Link to="/auth?mode=signup" className="btn btn-primary btn-block">Join free</Link>
                </>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
