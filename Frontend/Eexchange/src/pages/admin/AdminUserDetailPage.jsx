import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Ban,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  GraduationCap,
  Mail,
  PackageSearch,
  ShieldCheck,
  Unlock,
  UserRound,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { useAdmin } from '../../context/AdminContext';
import { formatDate, initials, timeAgo } from '../../lib/format';
import { POST_STATUS_META } from '../../lib/constants';
import StatusBadge from '../../components/StatusBadge';
import {
  AdminDetailSkeleton,
  AdminEmpty,
  AUDIT_ACTION_LABELS,
  UserStatusBadge,
  formatAuditTarget,
} from '../../components/AdminBits';
import AdminUserActionModal from '../../components/AdminUserActionModal';
import '../../components/Admin.css';

export default function AdminUserDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isSuperAdmin } = useAdmin();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionMode, setActionMode] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const loadUser = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.users.detail(id);
      setUser(response.data);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load this student.'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadUser();
  }, [loadUser, refreshKey]);

  if (loading && !user) {
    return (
      <div className="admin-page">
        <AdminDetailSkeleton />
      </div>
    );
  }

  if (error && !user) {
    return (
      <div className="admin-page">
        <Link to="/admin/users" className="admin-row-link" style={{ marginBottom: 16 }}>
          <ArrowLeft size={14} /> Back to students
        </Link>
        <div className="alert error">{error}</div>
        <AdminEmpty title="Student not available" description="Their account may have been deleted or the link is wrong." icon={UserRound} />
      </div>
    );
  }

  if (!user) return null;

  const isBlocked = user.status !== 'ACTIVE';
  const canManagePerm = isSuperAdmin || user.status !== 'PERM_BLOCKED';
  const postCounts = user.postCountsByStatus || {};

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <button type="button" className="admin-row-link" onClick={() => navigate('/admin/users')} style={{ marginBottom: 10 }}>
            <ArrowLeft size={14} /> All students
          </button>
          <h1>Student profile</h1>
          <p>Account details, marketplace activity and moderation history.</p>
        </div>
        <div className="admin-head-actions">
          <button
            type="button"
            className="btn btn-light btn-sm"
            onClick={() => loadUser()}
            disabled={loading}
          >
            Refresh
          </button>
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <section className="admin-detail-card">
        <div className="admin-detail-body">
          <div className="admin-profile-hero">
            <span className="admin-avatar">{initials(user.name)}</span>
            <div>
              <h2>{user.name}</h2>
              <p>{user.email}</p>
              <div className="admin-badges">
                <UserStatusBadge status={user.status} />
                {user.isCollegeVerified && (
                  <span className="admin-status green"><CheckCircle2 size={12} /> College verified</span>
                )}
                <span className="admin-status muted"><CalendarDays size={12} /> Joined {formatDate(user.createdAt)}</span>
              </div>
            </div>
            <div className="admin-profile-actions">
              {isBlocked ? (
                <button
                  type="button"
                  className="btn btn-mint btn-sm"
                  onClick={() => setActionMode('unblock')}
                  disabled={!canManagePerm}
                  title={!canManagePerm ? 'Only super admins can unblock permanent blocks' : undefined}
                >
                  <Unlock size={15} /> Unblock
                </button>
              ) : (
                <button type="button" className="btn btn-danger btn-sm" onClick={() => setActionMode('block')}>
                  <Ban size={15} /> Block student
                </button>
              )}
            </div>
          </div>
        </div>
      </section>

      <div className="admin-detail-grid">
        <div>
          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><UserRound size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Account details</h2>
                <p>Basic identity and campus information.</p>
              </div>
            </div>
            <div className="admin-detail-body">
              <div className="admin-facts">
                <div className="admin-fact">
                  <small><Mail size={10} /> Email</small>
                  <strong>{user.email}</strong>
                </div>
                <div className="admin-fact">
                  <small><GraduationCap size={10} /> College</small>
                  <strong>{user.college?.name || 'Not linked'}</strong>
                </div>
                <div className="admin-fact">
                  <small><CalendarDays size={10} /> Member since</small>
                  <strong>{formatDate(user.createdAt, 'dd MMM yyyy')}</strong>
                </div>
                <div className="admin-fact">
                  <small><ShieldCheck size={10} /> Last login</small>
                  <strong>{user.lastLoginAt ? formatDate(user.lastLoginAt, 'dd MMM yyyy, HH:mm') : 'Never'}</strong>
                </div>
                <div className="admin-fact full">
                  <small>College verification</small>
                  <strong>{user.isCollegeVerified ? 'Verified with campus email' : 'Not verified'}</strong>
                </div>
              </div>
            </div>
          </section>

          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><PackageSearch size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Marketplace activity</h2>
                <p>{user.postCount} listing{user.postCount === 1 ? '' : 's'} in total · {user.openReportCount} open report{user.openReportCount === 1 ? '' : 's'} against their listings.</p>
              </div>
              <Link to={`/admin/posts?q=${encodeURIComponent(user.name)}`} className="admin-row-link">
                Find listings
              </Link>
            </div>
            <div className="admin-detail-body">
              <div className="admin-facts">
                {Object.entries(POST_STATUS_META).map(([status, meta]) => (
                  <div className="admin-fact" key={status}>
                    <small>{meta.label}</small>
                    <strong style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <span>{postCounts[status] ?? 0}</span>
                      <StatusBadge status={status} />
                    </strong>
                  </div>
                ))}
              </div>
            </div>
          </section>
        </div>

        <div>
          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><ShieldCheck size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Moderation</h2>
                <p>Block status and current signals.</p>
              </div>
            </div>
            <div className="admin-detail-body">
              {isBlocked ? (
                <>
                  <div className="admin-callout danger">
                    <AlertTriangle size={16} />
                    <div>
                      <strong>This account is {user.status === 'PERM_BLOCKED' ? 'permanently' : 'temporarily'} blocked.</strong>
                      <div style={{ marginTop: 4 }}>
                        {user.blockReason || 'No reason recorded.'}
                      </div>
                    </div>
                  </div>
                  <div className="admin-facts">
                    <div className="admin-fact">
                      <small>Blocked at</small>
                      <strong>{formatDate(user.blockedAt, 'dd MMM yyyy, HH:mm')}</strong>
                    </div>
                    <div className="admin-fact">
                      <small>Blocked until</small>
                      <strong>{user.blockedUntil ? formatDate(user.blockedUntil, 'dd MMM yyyy, HH:mm') : 'No expiry'}</strong>
                    </div>
                  </div>
                </>
              ) : (
                <div className="admin-callout mint">
                  <CheckCircle2 size={16} />
                  <div>
                    <strong>Account is active</strong>
                    <div style={{ marginTop: 2 }}>No blocks on record. Keep an eye on the open report count.</div>
                  </div>
                </div>
              )}

              {user.openReportCount > 0 && (
                <div className="admin-callout sun" style={{ marginTop: 14, marginBottom: 0 }}>
                  <AlertTriangle size={16} />
                  <div>
                    <strong>{user.openReportCount} open report{user.openReportCount === 1 ? '' : 's'}</strong>
                    <div style={{ marginTop: 2 }}>
                      Review the reports queue before taking permanent action.
                    </div>
                  </div>
                </div>
              )}
            </div>
          </section>

          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><ClipboardList size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Recent moderation</h2>
                <p>Last 10 actions on this account.</p>
              </div>
            </div>
            <div className="admin-detail-body">
              {user.recentAudit?.length ? (
                <div className="admin-timeline">
                  {user.recentAudit.map((entry) => (
                    <div className="admin-timeline-item" key={entry.id}>
                      <span className="admin-timeline-dot" />
                      <div>
                        <strong>{AUDIT_ACTION_LABELS[entry.action] || entry.action}</strong>
                        <small>
                          {entry.adminUsername} · {formatAuditTarget(entry.targetType, entry.targetId)} · {timeAgo(entry.createdAt)}
                        </small>
                        {entry.reason && <span className="admin-timeline-reason">“{entry.reason}”</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="admin-text-muted" style={{ fontSize: '0.8rem' }}>
                  No moderation actions against this student yet.
                </p>
              )}
            </div>
          </section>
        </div>
      </div>

      <AdminUserActionModal
        open={Boolean(actionMode)}
        mode={actionMode || 'block'}
        user={user}
        isSuperAdmin={isSuperAdmin}
        onClose={() => setActionMode(null)}
        onSuccess={() => setRefreshKey((key) => key + 1)}
      />
    </div>
  );
}
