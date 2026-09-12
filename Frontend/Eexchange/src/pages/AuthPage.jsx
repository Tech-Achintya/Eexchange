import React, { useState } from 'react';
import './Auth.css';

export default function AuthPage({ onLogin }) {
  const [mode, setMode] = useState('login'); // 'login' | 'signup'
  const [signupStep, setSignupStep] = useState('email'); // 'email' | 'otp' | 'details'
  const [form, setForm] = useState({ name: '', email: '', password: '', otp: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handle = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (loading) return;
    setError('');

    if (!form.email.toLowerCase().endsWith('@cuchd.in')) {
      setError('Error: Please use your institutional (@cuchd.in) email address.');
      return;
    }

    if (mode === 'login') {
      await handleLogin();
    } else {
      if (signupStep === 'email') {
        await handleInitiateSignup();
      } else if (signupStep === 'otp') {
        await handleVerifyOtpStep();
      } else {
        await handleFinalizeSignup();
      }
    }
  };

  const handleLogin = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email, password: form.password }),
      });
      
      let data;
      try {
        data = await res.json();
      } catch (parseError) {
        throw new Error('Server returned an invalid response. Please ensure backend is running.');
      }

      if (!res.ok) {
        if (data.message === 'User not found') {
          throw new Error('User not found! Please sign up.');
        }
        throw new Error(data.message || 'Login failed');
      }

      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(data.user));
      onLogin(data.user);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  // Step 1: Just Email -> Sends OTP
  const handleInitiateSignup = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/auth/signup/initiate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email }),
      });
      
      let data;
      try {
        data = await res.json();
      } catch (parseError) {
        throw new Error('Server returned an invalid response. Please ensure backend is running.');
      }

      if (!res.ok) {
        if (data.message && data.message.includes('already exists')) {
          throw new Error('User already exists! Please login.');
        }
        throw new Error(data.message || 'Failed to send OTP');
      }
      
      setSignupStep('otp');
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  // Step 2: OTP Verification Only
  const handleVerifyOtpStep = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/auth/signup/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email, otpCode: form.otp }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Invalid OTP');

      setSignupStep('details');
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Finalize with Name and Password
  const handleFinalizeSignup = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/auth/signup/verify`, {
        method: 'POST', 
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: form.email,
          otpCode: form.otp,
          name: form.name,
          password: form.password
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Registration failed');

      setMode('login');
      setSignupStep('email');
      setForm({ ...form, otp: '', password: '', name: '' });
      setError('Registration successful! Please login.');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const resetAuth = () => {
    setSignupStep('email');
    setError('');
    // We don't necessarily want to wipe the email if they just switched tabs
    // but for safety, let's keep it consistent.
  };

  return (
    <div className="auth-bg">
      <div className="auth-glow" />
      <div className="auth-box">
        {/* Logo */}
        <div className="auth-logo">
          <span className="logo-icon">⚡</span>
          <span className="logo-text">EExchange</span>
        </div>

        <p className="auth-tagline">
          {mode === 'login'
            ? 'Welcome back 👋 pick up where you left off'
            : signupStep === 'email' 
              ? 'Join the campus marketplace 🎓'
              : signupStep === 'otp'
                ? 'Check your email for the code ✉️'
                : 'Final step: set your profile ✨'}
        </p>

        {/* Tabs - Only show in initial state or login mode */}
        {signupStep === 'email' && (
          <div className="auth-tabs">
            <button
              className={`auth-tab ${mode === 'login' ? 'active' : ''}`}
              onClick={() => { setMode('login'); resetAuth(); }}
            >
              Login
            </button>
            <button
              className={`auth-tab ${mode === 'signup' ? 'active' : ''}`}
              onClick={() => { setMode('signup'); resetAuth(); }}
            >
              Sign Up
            </button>
          </div>
        )}

        <form onSubmit={submit} className="auth-form">
          {/* LOGIN MODE */}
          {mode === 'login' && (
            <>
              <div className="form-group">
                <label>Email</label>
                <input
                  name="email"
                  type="email"
                  placeholder="yourname@cuchd.in"
                  value={form.email}
                  onChange={handle}
                  required
                />
              </div>
              <div className="form-group">
                <label>Password</label>
                <input
                  name="password"
                  type="password"
                  placeholder="••••••••"
                  value={form.password}
                  onChange={handle}
                  required
                />
              </div>
            </>
          )}

          {/* SIGNUP STEP: EMAIL */}
          {mode === 'signup' && signupStep === 'email' && (
            <div className="form-group">
              <label>Institutional Email</label>
              <input
                name="email"
                type="email"
                placeholder="yourid@cuchd.in"
                value={form.email}
                onChange={handle}
                required
                autoFocus
              />
              <p className="otp-hint">We'll send a verification code to this address.</p>
            </div>
          )}

          {/* SIGNUP STEP: OTP */}
          {mode === 'signup' && signupStep === 'otp' && (
            <div className="form-group">
              <label>Enter 6-digit OTP</label>
              <input
                name="otp"
                placeholder="000000"
                value={form.otp}
                onChange={handle}
                maxLength={6}
                required
                autoFocus
              />
              <p className="otp-hint">Sent to <strong>{form.email}</strong></p>
            </div>
          )}

          {/* SIGNUP STEP: DETAILS */}
          {mode === 'signup' && signupStep === 'details' && (
            <>
              <div className="form-group">
                <label>Full Name</label>
                <input
                  name="name"
                  placeholder="Your display name..."
                  value={form.name}
                  onChange={handle}
                  required
                  autoFocus
                />
              </div>
              <div className="form-group">
                <label>Create Password</label>
                <input
                  name="password"
                  type="password"
                  placeholder="At least 8 characters"
                  value={form.password}
                  onChange={handle}
                  required
                />
              </div>
            </>
          )}

          {error && <p className={`error-msg ${error.includes('successful') ? 'success' : ''}`}>{error}</p>}

          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Processing...' : 
              mode === 'login' ? 'Let me in 🚀' : 
              signupStep === 'email' ? 'Get OTP 📩' : 
              signupStep === 'otp' ? 'Verify Code ✨' : 'Finish Signup 🎉'}
          </button>

          {/* BACK BUTTONS */}
          {mode === 'signup' && signupStep !== 'email' && (
            <button 
              type="button" 
              className="btn-link" 
              onClick={() => setSignupStep(signupStep === 'otp' ? 'email' : 'otp')}
              style={{ marginTop: '10px', fontSize: '0.9rem', opacity: 0.7 }}
            >
              {signupStep === 'otp' ? '← Change email' : '← Back to OTP'}
            </button>
          )}
        </form>

        {signupStep === 'email' && (
          <p className="auth-switch">
            {mode === 'login' ? "New here? " : 'Already have an account? '}
            <span onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); resetAuth(); }}>
              {mode === 'login' ? 'Sign up' : 'Login'}
            </span>
          </p>
        )}
      </div>
    </div>
  );
}
