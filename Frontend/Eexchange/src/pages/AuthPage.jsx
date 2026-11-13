import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Eye,
  EyeOff,
  Lock,
  Mail,
  ShieldCheck,
  UserRound,
  Wallet,
} from 'lucide-react';
import { authApi, getErrorMessage } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import CampusExchangeAuthArt from '../components/CampusExchangeAuthArt';
import './AuthPage.css';

const emptySignup = { name: '', email: '', password: '', confirm: '' };

// Matches app.otp.cooldown-seconds on the backend (30s resend window).
const RESEND_COOLDOWN_SECONDS = 30;

export default function AuthPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialMode = searchParams.get('mode') === 'signup' ? 'signup' : 'login';
  const [view, setView] = useState(initialMode);
  const [loginForm, setLoginForm] = useState({ email: '', password: '' });
  const [signupForm, setSignupForm] = useState(emptySignup);
  const [otp, setOtp] = useState('');
  const [forgotEmail, setForgotEmail] = useState('');
  const [pendingEmail, setPendingEmail] = useState('');
  const [collegeName, setCollegeName] = useState('');
  const [forgotSent, setForgotSent] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);

  const redirectTo = useMemo(() => {
    const from = location.state?.from;
    return typeof from === 'string' && from.startsWith('/') ? from : '/dashboard';
  }, [location.state]);

  useEffect(() => {
    if (user) navigate('/dashboard', { replace: true });
  }, [user, navigate]);

  useEffect(() => {
    const requested = searchParams.get('mode');
    if (requested === 'signup' && view === 'login') setView('signup');
  }, [searchParams, view]);

  useEffect(() => {
    if (resendCooldown <= 0) return undefined;
    const timer = window.setInterval(() => {
      setResendCooldown((value) => Math.max(0, value - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendCooldown]);

  const switchView = (next) => {
    setError('');
    setShowPassword(false);
    setForgotSent(false);
    setView(next);
    setSearchParams(next === 'signup' ? { mode: 'signup' } : {}, { replace: true });
  };

  const validateEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

  const handleLogin = async (event) => {
    event.preventDefault();
    setError('');
    if (!validateEmail(loginForm.email.trim())) {
      setError('That email looks a little off — double-check it?');
      return;
    }
    if (!loginForm.password) {
      setError('Your password is missing.');
      return;
    }
    setLoading(true);
    try {
      const response = await authApi.login({
        email: loginForm.email.trim(),
        password: loginForm.password,
      });
      signIn(response.data);
      toast.success(`Welcome back, ${response.data?.name?.split(' ')[0] || 'friend'}! 👋`);
      navigate(redirectTo, { replace: true });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not log you in.'));
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (event) => {
    event.preventDefault();
    setError('');
    const email = signupForm.email.trim();
    if (signupForm.name.trim().length < 2) {
      setError('Tell us your name — at least 2 characters.');
      return;
    }
    if (!validateEmail(email)) {
      setError('Please use a valid email address.');
      return;
    }
    if (signupForm.password.length < 8) {
      setError('Passwords need at least 8 characters.');
      return;
    }
    if (signupForm.password !== signupForm.confirm) {
      setError('The two passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      const response = await authApi.signup({
        email,
        name: signupForm.name.trim(),
      });
      setPendingEmail(email);
      setCollegeName(response.data?.collegeName || '');
      setOtp('');
      setView('otp');
      setResendCooldown(RESEND_COOLDOWN_SECONDS);
      toast.success('OTP sent! Check your college inbox 📬');
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Signup failed. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (event) => {
    event.preventDefault();
    setError('');
    if (!/^\d{6}$/.test(otp.trim())) {
      setError('The OTP is exactly 6 digits.');
      return;
    }
    setLoading(true);
    try {
      const response = await authApi.verifyOtp({
        email: pendingEmail,
        otp: otp.trim(),
        name: signupForm.name.trim(),
        password: signupForm.password,
      });
      signIn(response.data);
      toast.success('You are in! Welcome to EExchange 🎉');
      navigate(redirectTo, { replace: true });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'That code did not work.'));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (resendCooldown > 0 || loading) return;
    setError('');
    setLoading(true);
    try {
      await authApi.resendOtp({ email: pendingEmail, purpose: 'signup' });
      setResendCooldown(RESEND_COOLDOWN_SECONDS);
      toast.success('A fresh OTP is on its way.');
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not resend the OTP.'));
    } finally {
      setLoading(false);
    }
  };

  const handleForgot = async (event) => {
    event.preventDefault();
    setError('');
    if (!validateEmail(forgotEmail.trim())) {
      setError('Enter the email you signed up with.');
      return;
    }
    setLoading(true);
    try {
      const response = await authApi.forgotPassword({ email: forgotEmail.trim() });
      setForgotSent(true);
      toast.success(response?.message || 'Reset link sent!');
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not send the reset link.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-shell">
        <CampusExchangeAuthArt />

        <section className="auth-form-side">
          <AnimatePresence mode="wait">
            {view === 'login' && (
              <motion.div
                key="login"
                className="auth-form-box"
                initial={{ opacity: 0, x: 14 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -14 }}
                transition={{ duration: 0.25 }}
              >
                <div className="auth-head">
                  <span className="eyebrow">Welcome back</span>
                  <h1>Pick up where<br />you left off.</h1>
                  <p>Log in to see what your campus is trading today.</p>
                </div>

                {error && <div className="alert error">{error}</div>}

                <form onSubmit={handleLogin} noValidate>
                  <div className="form-group">
                    <label htmlFor="login-email">College email</label>
                    <div className="input-icon-wrap">
                      <Mail size={17} />
                      <input
                        id="login-email"
                        type="email"
                        autoComplete="email"
                        placeholder="you@yourcollege.edu"
                        value={loginForm.email}
                        onChange={(event) => setLoginForm({ ...loginForm, email: event.target.value })}
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="login-password">Password</label>
                    <div className="input-icon-wrap password-wrap">
                      <Lock size={17} />
                      <input
                        id="login-password"
                        type={showPassword ? 'text' : 'password'}
                        autoComplete="current-password"
                        placeholder="Your secret password"
                        value={loginForm.password}
                        onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })}
                        required
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

                  <div className="auth-inline">
                    <button type="button" className="link-btn" onClick={() => switchView('forgot')}>
                      Forgot password?
                    </button>
                  </div>

                  <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                    {loading ? <span className="spinner" /> : <>Log me in <ArrowRight size={17} /></>}
                  </button>
                </form>

                <p className="auth-switch">
                  New to EExchange?{' '}
                  <button type="button" onClick={() => switchView('signup')}>Create an account</button>
                </p>
              </motion.div>
            )}

            {view === 'signup' && (
              <motion.div
                key="signup"
                className="auth-form-box"
                initial={{ opacity: 0, x: 14 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -14 }}
                transition={{ duration: 0.25 }}
              >
                <div className="auth-head">
                  <span className="eyebrow">Join free</span>
                  <h1>One account.<br />Your whole campus.</h1>
                  <p>Use your institutional email so we can verify you.</p>
                </div>

                {error && <div className="alert error">{error}</div>}

                <form onSubmit={handleSignup} noValidate>
                  <div className="form-group">
                    <label htmlFor="signup-name">Your name</label>
                    <div className="input-icon-wrap">
                      <UserRound size={17} />
                      <input
                        id="signup-name"
                        autoComplete="name"
                        placeholder="What should we call you?"
                        value={signupForm.name}
                        onChange={(event) => setSignupForm({ ...signupForm, name: event.target.value })}
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="signup-email">College email</label>
                    <div className="input-icon-wrap">
                      <Mail size={17} />
                      <input
                        id="signup-email"
                        type="email"
                        autoComplete="email"
                        placeholder="you@yourcollege.edu"
                        value={signupForm.email}
                        onChange={(event) => setSignupForm({ ...signupForm, email: event.target.value })}
                        required
                      />
                    </div>
                    <span className="field-hint">We only use this to verify your college and send your OTP.</span>
                  </div>

                  <div className="form-row">
                    <div className="form-group">
                      <label htmlFor="signup-password">Password</label>
                      <div className="input-icon-wrap password-wrap">
                        <Lock size={17} />
                        <input
                          id="signup-password"
                          type={showPassword ? 'text' : 'password'}
                          autoComplete="new-password"
                          placeholder="8+ characters"
                          value={signupForm.password}
                          onChange={(event) => setSignupForm({ ...signupForm, password: event.target.value })}
                          required
                        />
                      </div>
                    </div>
                    <div className="form-group">
                      <label htmlFor="signup-confirm">Confirm</label>
                      <div className="input-icon-wrap">
                        <Lock size={17} />
                        <input
                          id="signup-confirm"
                          type={showPassword ? 'text' : 'password'}
                          autoComplete="new-password"
                          placeholder="Same again"
                          value={signupForm.confirm}
                          onChange={(event) => setSignupForm({ ...signupForm, confirm: event.target.value })}
                          required
                        />
                      </div>
                    </div>
                  </div>

                  <label className="show-pass-check">
                    <input
                      type="checkbox"
                      checked={showPassword}
                      onChange={(event) => setShowPassword(event.target.checked)}
                    />
                    Show passwords
                  </label>

                  <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                    {loading ? <span className="spinner" /> : <>Send my OTP <ArrowRight size={17} /></>}
                  </button>
                </form>

                <p className="auth-switch">
                  Already have an account?{' '}
                  <button type="button" onClick={() => switchView('login')}>Log in</button>
                </p>
              </motion.div>
            )}

            {view === 'otp' && (
              <motion.div
                key="otp"
                className="auth-form-box"
                initial={{ opacity: 0, x: 14 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -14 }}
                transition={{ duration: 0.25 }}
              >
                <button type="button" className="back-link" onClick={() => switchView('signup')}>
                  <ArrowLeft size={15} /> Back to details
                </button>

                <div className="auth-head">
                  <span className="eyebrow">Check your inbox</span>
                  <h1>Almost<br />there!</h1>
                  <p>
                    We sent a 6-digit code to <strong>{pendingEmail}</strong>.
                    {collegeName && <> Looks like you’re from <strong>{collegeName}</strong> 🎓</>}
                  </p>
                </div>

                {error && <div className="alert error">{error}</div>}

                <form onSubmit={handleVerifyOtp} noValidate>
                  <div className="form-group">
                    <label htmlFor="otp">Verification code</label>
                    <input
                      id="otp"
                      className="otp-input"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      placeholder="000000"
                      value={otp}
                      onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
                      autoFocus
                      required
                    />
                  </div>

                  <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                    {loading ? <span className="spinner" /> : <>Verify & create account <ArrowRight size={17} /></>}
                  </button>
                </form>

                <div className="resend-row">
                  <span>Didn’t get it?</span>
                  <button
                    type="button"
                    className="link-btn"
                    onClick={handleResend}
                    disabled={resendCooldown > 0 || loading}
                  >
                    {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend code'}
                  </button>
                </div>
                <p className="otp-tip">
                  Tip: the code is valid for 15 minutes. Check spam if your college
                  email is being shy.
                </p>
              </motion.div>
            )}

            {view === 'forgot' && (
              <motion.div
                key="forgot"
                className="auth-form-box"
                initial={{ opacity: 0, x: 14 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -14 }}
                transition={{ duration: 0.25 }}
              >
                <button type="button" className="back-link" onClick={() => switchView('login')}>
                  <ArrowLeft size={15} /> Back to login
                </button>

                <div className="auth-head">
                  <span className="eyebrow">Password rescue</span>
                  <h1>Happens to<br />the best of us.</h1>
                  <p>Enter your email and we’ll send a link to reset your password.</p>
                </div>

                {error && <div className="alert error">{error}</div>}
                {forgotSent && (
                  <div className="alert success">
                    If that email is registered, a reset link is on its way. The link
                    expires in 15 minutes.
                  </div>
                )}

                <form onSubmit={handleForgot} noValidate>
                  <div className="form-group">
                    <label htmlFor="forgot-email">College email</label>
                    <div className="input-icon-wrap">
                      <Mail size={17} />
                      <input
                        id="forgot-email"
                        type="email"
                        autoComplete="email"
                        placeholder="you@yourcollege.edu"
                        value={forgotEmail}
                        onChange={(event) => setForgotEmail(event.target.value)}
                        required
                      />
                    </div>
                  </div>

                  <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                    {loading ? <span className="spinner" /> : <>Send reset link <ArrowRight size={17} /></>}
                  </button>
                </form>

                <p className="auth-switch">
                  Remembered it?{' '}
                  <button type="button" onClick={() => switchView('login')}>Back to login</button>
                </p>
              </motion.div>
            )}
          </AnimatePresence>

          <p className="auth-legal">
            By continuing you agree to keep campus trading kind, honest and drama-free.
          </p>
        </section>
      </div>
    </div>
  );
}
