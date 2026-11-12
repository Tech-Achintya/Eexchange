import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  ArrowLeft,
  Eye,
  EyeOff,
  Flag,
  KeyRound,
  Loader2,
  LockKeyhole,
  Mail,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { useAdmin } from '../../context/AdminContext';
import Logo from '../../components/Logo';
import '../../components/Admin.css';

export default function AdminLoginPage() {
  const { isAdminAuthenticated, signIn } = useAdmin();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ usernameOrEmail: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (isAdminAuthenticated) return <Navigate to="/admin" replace />;

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');

    if (!form.usernameOrEmail.trim() || !form.password) {
      setError('Enter your admin username or email and password.');
      return;
    }

    setSubmitting(true);
    try {
      const response = await adminApi.login({
        usernameOrEmail: form.usernameOrEmail.trim(),
        password: form.password,
      });
      const nextAdmin = signIn(response.data);
      toast.success(`Welcome back, ${nextAdmin?.username || 'admin'}!`);
      const from = location.state?.from;
      navigate(from && from.startsWith('/admin') ? from : '/admin', { replace: true });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not sign you in. Check your credentials.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="admin-login-shell">
      <section className="admin-login-art">
        <Logo to="/" />

        <motion.div
          className="admin-login-art-copy"
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="admin-login-hq"><ShieldCheck size={13} /> Admin HQ</span>
          <h2>Keep the campus marketplace safe &amp; sunny.</h2>
          <p>
            Review listings, help students when something looks off, and keep
            EExchange a warm, trustworthy place to trade.
          </p>

          <div className="admin-login-points">
            <div className="admin-login-point">
              <span><Users size={14} /></span> Look after student accounts
            </div>
            <div className="admin-login-point">
              <span><ShieldCheck size={14} /></span> Keep listings fair and useful
            </div>
            <div className="admin-login-point">
              <span><Flag size={14} /></span> Resolve reports with context
            </div>
          </div>
        </motion.div>

        <div className="admin-login-foot">
          <span>Campus marketplace moderation</span>
          <Link to="/">Back to shop</Link>
        </div>
      </section>

      <section className="admin-login-form-side">
        <motion.div
          className="admin-login-card"
          initial={{ opacity: 0, y: 20, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.4, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="admin-login-badge"><LockKeyhole size={12} /> Team access only</span>
          <h1>Admin sign in</h1>
          <p>Use the credentials provided by your campus operations team.</p>

          {error && <div className="alert error">{error}</div>}

          <form onSubmit={handleSubmit} noValidate>
            <div className="form-group">
              <label htmlFor="admin-identifier">Username or email</label>
              <div className="input-icon-wrap">
                <Mail size={17} />
                <input
                  id="admin-identifier"
                  name="usernameOrEmail"
                  type="text"
                  autoComplete="username"
                  placeholder="you@eexchange.app"
                  value={form.usernameOrEmail}
                  onChange={handleChange}
                  autoFocus
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="admin-password">Password</label>
              <div className="input-icon-wrap" style={{ position: 'relative' }}>
                <KeyRound size={17} />
                <input
                  id="admin-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Your admin password"
                  value={form.password}
                  onChange={handleChange}
                  style={{ paddingRight: 46 }}
                />
                <button
                  type="button"
                  className="admin-password-toggle"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  style={{
                    position: 'absolute',
                    right: 8,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--muted)',
                    display: 'grid',
                    placeItems: 'center',
                    width: 30,
                    height: 30,
                    borderRadius: 8,
                  }}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
              {submitting ? <Loader2 size={18} className="admin-spin" /> : <ShieldCheck size={18} />}
              {submitting ? 'Checking your pass…' : 'Enter Admin HQ'}
            </button>
          </form>

          <div className="admin-login-help">
            <ShieldCheck size={14} />
            <span>
              This area is separate from student accounts. Moderators can handle users,
              listings and reports. Audit history is reserved for super admins.
            </span>
          </div>

          <Link to="/" className="admin-login-back">
            <ArrowLeft size={14} /> Back to EExchange
          </Link>
        </motion.div>
      </section>
    </div>
  );
}
