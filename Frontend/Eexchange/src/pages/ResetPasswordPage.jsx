import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import { ArrowRight, Eye, EyeOff, KeyRound, Lock, ShieldAlert } from 'lucide-react';
import { authApi, getErrorMessage } from '../lib/api';
import './ResetPasswordPage.css';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [checking, setChecking] = useState(Boolean(token));
  const [valid, setValid] = useState(false);
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    let active = true;
    if (!token) {
      setChecking(false);
      setValid(false);
      return () => { active = false; };
    }

    authApi
      .validateResetToken(token)
      .then(() => {
        if (active) {
          setValid(true);
          setError('');
        }
      })
      .catch((requestError) => {
        if (active) setError(getErrorMessage(requestError, 'This reset link is invalid or expired.'));
      })
      .finally(() => {
        if (active) setChecking(false);
      });

    return () => { active = false; };
  }, [token]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (form.password.length < 8) {
      setError('Your new password needs at least 8 characters.');
      return;
    }
    if (form.password !== form.confirm) {
      setError('The two passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await authApi.resetPassword({ token, newPassword: form.password });
      setDone(true);
      toast.success('Password reset! You can log in now.');
      window.setTimeout(() => navigate('/auth', { replace: true }), 1800);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not reset the password.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="reset-page">
      <motion.div
        className="reset-card"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        {checking ? (
          <div className="page-loader" style={{ minHeight: 240 }}>
            <span className="blob" />
            <span>Checking your reset link…</span>
          </div>
        ) : !valid ? (
          <>
            <span className="reset-icon bad"><ShieldAlert size={26} /></span>
            <h1>Link expired</h1>
            <p>{error || 'This password reset link is missing or no longer valid.'}</p>
            <Link to="/auth" className="btn btn-primary btn-block btn-lg">
              Request a new link <ArrowRight size={17} />
            </Link>
          </>
        ) : done ? (
          <>
            <span className="reset-icon good"><KeyRound size={26} /></span>
            <h1>All set!</h1>
            <p>Your password was updated. Taking you to the login page…</p>
            <Link to="/auth" className="btn btn-primary btn-block btn-lg">
              Go to login <ArrowRight size={17} />
            </Link>
          </>
        ) : (
          <>
            <span className="reset-icon"><KeyRound size={26} /></span>
            <span className="eyebrow">Fresh start</span>
            <h1>Set a new password.</h1>
            <p>Pick something you’ll remember — at least 8 characters.</p>

            {error && <div className="alert error">{error}</div>}

            <form onSubmit={handleSubmit} noValidate>
              <div className="form-group">
                <label htmlFor="new-password">New password</label>
                <div className="input-icon-wrap password-wrap">
                  <Lock size={17} />
                  <input
                    id="new-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="8+ characters"
                    value={form.password}
                    onChange={(event) => setForm({ ...form, password: event.target.value })}
                    required
                    autoFocus
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword((value) => !value)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="confirm-password">Confirm new password</label>
                <div className="input-icon-wrap">
                  <Lock size={17} />
                  <input
                    id="confirm-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="Same again"
                    value={form.confirm}
                    onChange={(event) => setForm({ ...form, confirm: event.target.value })}
                    required
                  />
                </div>
              </div>

              <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                {loading ? <span className="spinner" /> : <>Save new password <ArrowRight size={17} /></>}
              </button>
            </form>
          </>
        )}
      </motion.div>
    </div>
  );
}
