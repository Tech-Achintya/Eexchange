import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  AlertTriangle,
  BadgeCheck,
  Bookmark,
  CalendarDays,
  Eye,
  EyeOff,
  Inbox,
  KeyRound,
  Loader2,
  LogOut,
  Mail,
  Package,
  Save,
  ShieldCheck,
  Trash2,
  UserRound,
} from 'lucide-react';
import {
  getErrorMessage,
  interestsApi,
  postsApi,
  savedApi,
  usersApi,
} from '../lib/api';
import { formatDate, initials } from '../lib/format';
import { useAuth, normalizeAuthUser } from '../context/AuthContext';
import Modal from '../components/Modal';
import { PageLoader } from '../components/Loaders';
import './Dashboard.css';

const TABS = [
  { id: 'personal', label: 'Personal info', icon: UserRound },
  { id: 'security', label: 'Password & security', icon: ShieldCheck },
  { id: 'account', label: 'Account', icon: AlertTriangle },
];

export default function ProfilePage() {
  const { user, signOut, updateFromProfile, refreshToken } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = TABS.some((tab) => tab.id === searchParams.get('tab'))
    ? searchParams.get('tab')
    : 'personal';

  const [tab, setTab] = useState(initialTab);
  const [profile, setProfile] = useState(user);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [name, setName] = useState(user?.name || '');
  const [nameSaving, setNameSaving] = useState(false);

  const [passwords, setPasswords] = useState({ oldPassword: '', newPassword: '', confirm: '' });
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [showPasswords, setShowPasswords] = useState({
    old: false,
    new: false,
    confirm: false,
    delete: false,
  });

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);

  const [stats, setStats] = useState({ listings: 0, saved: 0, unread: 0 });

  useEffect(() => {
    let active = true;
    setLoading(true);
    usersApi
      .me()
      .then((response) => {
        if (!active) return;
        setProfile(response.data);
        setName(response.data?.name || '');
        updateFromProfile(response.data);
      })
      .catch((requestError) => {
        if (active) setError(getErrorMessage(requestError, 'Could not load your profile.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    Promise.allSettled([postsApi.mine({ page: 0, size: 1 }), savedApi.list(), interestsApi.unreadCount()])
      .then(([postsResult, savedResult, unreadResult]) => {
        if (!active) return;
        setStats({
          listings: postsResult.status === 'fulfilled' ? postsResult.value.data?.totalElements ?? 0 : 0,
          saved: savedResult.status === 'fulfilled' ? (savedResult.value.data || []).length : 0,
          unread: unreadResult.status === 'fulfilled' ? unreadResult.value.data?.count ?? 0 : 0,
        });
      })
      .catch(() => {});

    return () => { active = false; };
  }, [updateFromProfile]);

  useEffect(() => {
    const requested = searchParams.get('tab');
    if (requested && TABS.some((item) => item.id === requested)) setTab(requested);
  }, [searchParams]);

  const changeTab = (next) => {
    setTab(next);
    setSearchParams(next === 'personal' ? {} : { tab: next }, { replace: true });
  };

  const collegeName = profile?.college?.name || profile?.collegeName || user?.collegeName || 'Your college';
  const memberSince = profile?.createdAt || user?.createdAt;
  const verified = profile?.isCollegeVerified ?? user?.isCollegeVerified;

  const saveName = async (event) => {
    event.preventDefault();
    if (name.trim().length < 2) {
      toast.error('Your name needs at least 2 characters.');
      return;
    }
    setNameSaving(true);
    try {
      const response = await usersApi.updateMe({ name: name.trim() });
      setProfile((previous) => ({ ...previous, ...response.data }));
      updateFromProfile({ ...normalizeAuthUser(profile), ...normalizeAuthUser(response.data) });
      setName(response.data.name);
      toast.success('Name updated ✨');
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not update your name.'));
    } finally {
      setNameSaving(false);
    }
  };

  const changePassword = async (event) => {
    event.preventDefault();
    setPasswordError('');
    if (!passwords.oldPassword) {
      setPasswordError('Enter your current password.');
      return;
    }
    if (passwords.newPassword.length < 8) {
      setPasswordError('New password needs at least 8 characters.');
      return;
    }
    if (passwords.newPassword !== passwords.confirm) {
      setPasswordError('The new passwords do not match.');
      return;
    }
    setPasswordSaving(true);
    try {
      const response = await usersApi.changePassword({
        oldPassword: passwords.oldPassword,
        newPassword: passwords.newPassword,
      });
      if (response.data?.token) refreshToken(response.data.token);
      setPasswords({ oldPassword: '', newPassword: '', confirm: '' });
      toast.success('Password changed. Other sessions were signed out.');
    } catch (requestError) {
      setPasswordError(getErrorMessage(requestError, 'Could not change your password.'));
    } finally {
      setPasswordSaving(false);
    }
  };

  const deleteAccount = async () => {
    setDeleteError('');
    if (!deletePassword) {
      setDeleteError('Enter your password to confirm.');
      return;
    }
    setDeleting(true);
    try {
      await usersApi.deleteMe({ password: deletePassword });
      toast.success('Your account has been deleted.');
      signOut();
      navigate('/', { replace: true });
    } catch (requestError) {
      setDeleteError(getErrorMessage(requestError, 'Could not delete your account.'));
      setDeleting(false);
    }
  };

  const handleLogout = () => {
    signOut();
    navigate('/');
  };

  const statusLabel = useMemo(() => {
    if (profile?.status === 'TEMP_BLOCKED') return 'Temporarily blocked';
    if (profile?.status === 'PERM_BLOCKED') return 'Suspended';
    return 'Active';
  }, [profile?.status]);

  if (loading) {
    return (
      <div className="page">
        <div className="container"><PageLoader label="Fetching your profile…" /></div>
      </div>
    );
  }

  return (
    <div className="page profile-page">
      <div className="container">
        <div className="page-head">
          <div>
            <span className="eyebrow">Your account</span>
            <h1>Profile & settings</h1>
            <p>Keep your details fresh and your account safe.</p>
          </div>
        </div>

        {error && <div className="alert error">{error}</div>}

        <div className="profile-layout">
          <aside className="profile-card">
            <div className="profile-avatar-lg">{initials(profile?.name)}</div>
            <h2>{profile?.name}</h2>
            <p className="profile-email"><Mail size={13} /> {profile?.email}</p>

            <span className={`chip ${verified ? 'green' : 'sun'} profile-verified`}>
              <BadgeCheck size={14} />
              {verified ? 'College verified' : 'Verification pending'}
            </span>

            <div className="profile-college">
              <span className="profile-college-icon">🎓</span>
              <div>
                <small>College</small>
                <strong>{collegeName}</strong>
              </div>
            </div>

            <div className="profile-mini-stats">
              <div>
                <strong>{stats.listings}</strong>
                <span>listings</span>
              </div>
              <div>
                <strong>{stats.saved}</strong>
                <span>saved</span>
              </div>
              <div>
                <strong>{stats.unread}</strong>
                <span>unread</span>
              </div>
            </div>

            <div className="profile-status-row">
              <span className="status-dot" style={{ color: profile?.status === 'ACTIVE' ? 'var(--green)' : 'var(--red)' }} />
              <span>{statusLabel}</span>
              {memberSince && (
                <span className="profile-since">
                  <CalendarDays size={13} /> joined {formatDate(memberSince, 'MMM yyyy')}
                </span>
              )}
            </div>

            <div className="profile-quick-links">
              <button type="button" onClick={() => changeTab('security')}>
                <KeyRound size={15} /> Change password
              </button>
              <button type="button" onClick={() => navigate('/my-posts')}>
                <Package size={15} /> My listings
              </button>
              <button type="button" onClick={() => navigate('/saved')}>
                <Bookmark size={15} /> Wishlist
              </button>
              <button type="button" onClick={() => navigate('/interests')}>
                <Inbox size={15} /> Inbox
              </button>
            </div>

            <button type="button" className="btn btn-light btn-block" onClick={handleLogout}>
              <LogOut size={15} /> Log out
            </button>
          </aside>

          <div className="profile-content">
            <div className="profile-tabs">
              {TABS.map(({ id, label, icon: Icon }) => (
                <button
                  key={id}
                  type="button"
                  className={`profile-tab ${tab === id ? 'active' : ''} ${id === 'account' ? 'danger' : ''}`}
                  onClick={() => changeTab(id)}
                >
                  <Icon size={16} /> {label}
                </button>
              ))}
            </div>

            {tab === 'personal' && (
              <section className="panel">
                <h2 className="panel-title">Personal information</h2>
                <p className="panel-sub">
                  This is how you show up to other students on campus.
                </p>

                <form onSubmit={saveName}>
                  <div className="form-group">
                    <label htmlFor="profile-name">Display name</label>
                    <input
                      id="profile-name"
                      value={name}
                      maxLength={100}
                      onChange={(event) => setName(event.target.value)}
                      required
                    />
                    <span className="field-hint">Between 2 and 100 characters.</span>
                  </div>

                  <div className="form-group">
                    <label htmlFor="profile-email">College email</label>
                    <div className="input-icon-wrap">
                      <Mail size={17} />
                      <input id="profile-email" value={profile?.email || ''} disabled />
                    </div>
                    <span className="field-hint">
                      Your email is tied to your verified college and can’t be changed here.
                    </span>
                  </div>

                  <div className="profile-info-row">
                    <div>
                      <small>College</small>
                      <strong>{collegeName}</strong>
                    </div>
                    <div>
                      <small>Verification</small>
                      <strong>{verified ? 'Verified student ✓' : 'Pending'}</strong>
                    </div>
                  </div>

                  <button type="submit" className="btn btn-primary" disabled={nameSaving || name.trim() === profile?.name}>
                    {nameSaving ? <Loader2 size={16} className="spin" /> : <Save size={16} />}
                    Save changes
                  </button>
                </form>
              </section>
            )}

            {tab === 'security' && (
              <section className="panel">
                <h2 className="panel-title">Password & security</h2>
                <p className="panel-sub">
                  Changing your password signs you out of all other devices.
                </p>

                {passwordError && <div className="alert error">{passwordError}</div>}

                <form onSubmit={changePassword}>
                  <div className="form-group">
                    <label htmlFor="old-password">Current password</label>
                    <div className="password-wrap">
                      <input
                        id="old-password"
                        type={showPasswords.old ? 'text' : 'password'}
                        autoComplete="current-password"
                        value={passwords.oldPassword}
                        onChange={(event) => setPasswords({ ...passwords, oldPassword: event.target.value })}
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle"
                        onClick={() => setShowPasswords((prev) => ({ ...prev, old: !prev.old }))}
                        aria-label={showPasswords.old ? 'Hide password' : 'Show password'}
                      >
                        {showPasswords.old ? <EyeOff size={18} /> : <Eye size={18} />}
                      </button>
                    </div>
                  </div>

                  <div className="form-row">
                    <div className="form-group">
                      <label htmlFor="new-password">New password</label>
                      <div className="password-wrap">
                        <input
                          id="new-password"
                          type={showPasswords.new ? 'text' : 'password'}
                          autoComplete="new-password"
                          placeholder="8+ characters"
                          value={passwords.newPassword}
                          onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })}
                          required
                        />
                        <button
                          type="button"
                          className="password-toggle"
                          onClick={() => setShowPasswords((prev) => ({ ...prev, new: !prev.new }))}
                          aria-label={showPasswords.new ? 'Hide password' : 'Show password'}
                        >
                          {showPasswords.new ? <EyeOff size={18} /> : <Eye size={18} />}
                        </button>
                      </div>
                    </div>
                    <div className="form-group">
                      <label htmlFor="confirm-new-password">Confirm new password</label>
                      <div className="password-wrap">
                        <input
                          id="confirm-new-password"
                          type={showPasswords.confirm ? 'text' : 'password'}
                          autoComplete="new-password"
                          placeholder="Same again"
                          value={passwords.confirm}
                          onChange={(event) => setPasswords({ ...passwords, confirm: event.target.value })}
                          required
                        />
                        <button
                          type="button"
                          className="password-toggle"
                          onClick={() => setShowPasswords((prev) => ({ ...prev, confirm: !prev.confirm }))}
                          aria-label={showPasswords.confirm ? 'Hide password' : 'Show password'}
                        >
                          {showPasswords.confirm ? <EyeOff size={18} /> : <Eye size={18} />}
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="security-tip">
                    <ShieldCheck size={17} />
                    <span>Use a password you don’t use anywhere else. At least 8 characters.</span>
                  </div>

                  <button type="submit" className="btn btn-dark" disabled={passwordSaving}>
                    {passwordSaving ? <Loader2 size={16} className="spin" /> : <KeyRound size={16} />}
                    Update password
                  </button>
                </form>
              </section>
            )}

            {tab === 'account' && (
              <section className="panel danger-panel">
                <h2 className="panel-title"><AlertTriangle size={18} /> Account & danger zone</h2>
                <p className="panel-sub">
                  You can log out of this device any time. Deleting your account is permanent.
                </p>

                <div className="account-actions">
                  <div className="account-action">
                    <div>
                      <strong>Log out of this device</strong>
                      <p>You can log back in whenever you like.</p>
                    </div>
                    <button type="button" className="btn btn-light" onClick={handleLogout}>
                      <LogOut size={15} /> Log out
                    </button>
                  </div>

                  <div className="account-action danger">
                    <div>
                      <strong>Delete my account</strong>
                      <p>Your profile and listings will be removed. This cannot be undone.</p>
                    </div>
                    <button type="button" className="btn btn-danger" onClick={() => setDeleteOpen(true)}>
                      <Trash2 size={15} /> Delete account
                    </button>
                  </div>
                </div>
              </section>
            )}
          </div>
        </div>
      </div>

      <Modal
        open={deleteOpen}
        onClose={() => { setDeleteOpen(false); setDeletePassword(''); setDeleteError(''); }}
        title="Delete your account?"
        subtitle="This is permanent. Your profile, listings and wishlist will go away."
        width={450}
      >
        {deleteError && <div className="alert error">{deleteError}</div>}
        <div className="delete-warning">
          <AlertTriangle size={18} />
          <span>Enter your password to confirm you really want to do this.</span>
        </div>
        <div className="form-group">
          <label htmlFor="delete-password">Password</label>
          <div className="password-wrap">
            <input
              id="delete-password"
              type={showPasswords.delete ? 'text' : 'password'}
              value={deletePassword}
              onChange={(event) => setDeletePassword(event.target.value)}
              placeholder="Your current password"
              autoFocus
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPasswords((prev) => ({ ...prev, delete: !prev.delete }))}
              aria-label={showPasswords.delete ? 'Hide password' : 'Show password'}
            >
              {showPasswords.delete ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-light" onClick={() => setDeleteOpen(false)}>
            Keep my account
          </button>
          <button type="button" className="btn btn-danger" onClick={deleteAccount} disabled={deleting}>
            {deleting ? <Loader2 size={16} className="spin" /> : <Trash2 size={16} />} Delete forever
          </button>
        </div>
      </Modal>
    </div>
  );
}
