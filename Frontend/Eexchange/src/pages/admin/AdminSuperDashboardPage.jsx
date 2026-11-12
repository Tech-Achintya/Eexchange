import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  ClipboardList,
  Crown,
  Flag,
  PackageSearch,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  Users,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { useAdmin } from '../../context/AdminContext';
import { timeAgo } from '../../lib/format';
import StatusBadge from '../../components/StatusBadge';
import {
  AdminEmpty,
  AUDIT_ACTION_LABELS,
  REPORT_REASON_LABELS,
  ReportStatusBadge,
  formatAuditTarget,
} from '../../components/AdminBits';
import '../../components/Admin.css';

const EMPTY_STATS = {
  users: 0,
  listings: 0,
  openReports: 0,
  moderation: 0,
  audit: 0,
};

const QUICK_LINKS = [
  {
    to: '/admin/audit-log',
    title: 'Audit trail',
    note: 'Every moderator decision, forever',
    icon: ClipboardList,
    bg: 'var(--lavender-soft)',
    fg: '#6653e0',
  },
  {
    to: '/admin/moderation',
    title: 'AI review queue',
    note: 'Human decisions for flagged listings',
    icon: Sparkles,
    bg: 'var(--sun-soft)',
    fg: '#997000',
  },
  {
    to: '/admin/reports',
    title: 'Reports',
    note: 'Resolve or dismiss student reports',
    icon: Flag,
    bg: 'var(--red-soft)',
    fg: '#bd2b3a',
  },
  {
    to: '/admin/users',
    title: 'Students',
    note: 'Accounts, blocks and history',
    icon: Users,
    bg: 'var(--sky-soft)',
    fg: '#2b6fd4',
  },
  {
    to: '/admin/posts',
    title: 'Listings',
    note: 'Search every listing status',
    icon: PackageSearch,
    bg: 'var(--mint-soft)',
    fg: '#0d7f75',
  },
];

export default function AdminSuperDashboardPage() {
  const { admin } = useAdmin();
  const [stats, setStats] = useState(EMPTY_STATS);
  const [recentUsers, setRecentUsers] = useState([]);
  const [recentPosts, setRecentPosts] = useState([]);
  const [openReports, setOpenReports] = useState([]);
  const [queue, setQueue] = useState([]);
  const [recentAudit, setRecentAudit] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError('');

    const results = await Promise.allSettled([
      adminApi.users.list({ page: 0, size: 4 }),
      adminApi.users.list({ page: 0, size: 1 }),
      adminApi.posts.list({ page: 0, size: 4 }),
      adminApi.posts.list({ page: 0, size: 1 }),
      adminApi.reports.list({ status: 'OPEN', page: 0, size: 4 }),
      adminApi.moderation.queue({ page: 0, size: 4 }),
      adminApi.audit.list({ page: 0, size: 8 }),
    ]);

    const valueAt = (index) =>
      results[index]?.status === 'fulfilled' ? results[index].value.data : null;

    const failures = results.filter((result) => result.status === 'rejected');
    if (failures.length === results.length) {
      setError(getErrorMessage(failures[0]?.reason, 'Could not load the super admin overview.'));
    }

    const latestUsers = valueAt(0);
    const userTotal = valueAt(1);
    const latestPosts = valueAt(2);
    const postTotal = valueAt(3);
    const reports = valueAt(4);
    const queueData = valueAt(5);
    const auditData = valueAt(6);

    setRecentUsers(latestUsers?.content || []);
    setRecentPosts(latestPosts?.content || []);
    setOpenReports(reports?.content || []);
    setQueue(queueData?.content || []);
    setRecentAudit(auditData?.content || []);
    setStats({
      users: userTotal?.totalElements ?? 0,
      listings: postTotal?.totalElements ?? 0,
      openReports: reports?.totalElements ?? 0,
      moderation: queueData?.totalElements ?? 0,
      audit: auditData?.totalElements ?? 0,
    });
    setLoading(false);
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const firstName = admin?.username || 'there';

  return (
    <div className="admin-page">
      <div className="admin-hero-strip admin-hero-super">
        <div className="admin-hero-copy">
          <span className="admin-eyebrow"><Crown size={12} /> Super admin command center</span>
          <h2>Full visibility, {firstName}.</h2>
          <p>
            Students, listings, AI escalations and every moderator decision in one place.
            You have the widest view of campus — and the final word.
          </p>
          <div className="admin-super-tags">
            <span className="chip lav"><Crown size={12} /> Super admin</span>
            <span className="chip green"><CheckCircle2 size={12} /> Audit access</span>
            <span className="chip red"><ShieldCheck size={12} /> Permanent blocks</span>
          </div>
        </div>
        <div className="admin-hero-badge admin-hero-crown" aria-hidden="true">👑</div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-stat-grid admin-stat-grid-5">
        {[
          {
            label: 'Students',
            value: stats.users,
            icon: Users,
            bg: 'var(--sky-soft)',
            fg: '#2b6fd4',
            note: 'Campus accounts',
          },
          {
            label: 'Listings',
            value: stats.listings,
            icon: PackageSearch,
            bg: 'var(--mint-soft)',
            fg: '#0d7f75',
            note: 'Across all statuses',
          },
          {
            label: 'Open reports',
            value: stats.openReports,
            icon: Flag,
            bg: 'var(--red-soft)',
            fg: '#bd2b3a',
            note: 'Waiting for a human',
          },
          {
            label: 'AI review queue',
            value: stats.moderation,
            icon: Sparkles,
            bg: 'var(--sun-soft)',
            fg: '#997000',
            note: 'Flagged for a decision',
          },
          {
            label: 'Audit events',
            value: stats.audit,
            icon: ClipboardList,
            bg: 'var(--lavender-soft)',
            fg: '#6653e0',
            note: 'Every moderator action',
          },
        ].map(({ label, value, icon: Icon, bg, fg, note }) => (
          <div className="admin-stat-card" key={label}>
            <div className="admin-stat-top">
              <span className="admin-stat-icon" style={{ background: bg, color: fg }}>
                <Icon size={19} />
              </span>
            </div>
            <strong>{loading ? '—' : value}</strong>
            <small>{label}</small>
            <span className="admin-stat-note"><TrendingUp size={12} /> {note}</span>
            <span className="admin-stat-accent" style={{ background: fg }} />
          </div>
        ))}
      </div>

      <div className="admin-grid-2">
        <div style={{ display: 'grid', gap: 18 }}>
          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><ClipboardList size={17} /> Latest moderator activity</h3>
                <p>The newest decisions across the whole team.</p>
              </div>
              <Link to="/admin/audit-log" className="admin-panel-link">
                Full audit trail <ArrowRight size={13} />
              </Link>
            </div>
            {recentAudit.length === 0 ? (
              <AdminEmpty
                title={loading ? 'Loading activity…' : 'No activity yet'}
                description={loading ? 'Hang tight.' : 'Moderator decisions will appear here.'}
                icon={ClipboardList}
              />
            ) : (
              <div className="admin-audit-list">
                {recentAudit.map((entry) => (
                  <div className="admin-audit-row" key={entry.id}>
                    <span className="admin-audit-icon lav">
                      <ClipboardList size={16} />
                    </span>
                    <div className="admin-audit-main">
                      <div className="admin-audit-line">
                        <strong>{entry.adminUsername}</strong>
                        <span>{AUDIT_ACTION_LABELS[entry.action] || entry.action?.replaceAll('_', ' ').toLowerCase()}</span>
                      </div>
                      <div className="admin-audit-meta">
                        <span className="admin-status muted">
                          {formatAuditTarget(entry.targetType, entry.targetId)}
                        </span>
                        <span>· {timeAgo(entry.createdAt)}</span>
                      </div>
                      {entry.reason && <div className="admin-audit-reason">“{entry.reason}”</div>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><Flag size={17} /> Reports needing attention</h3>
                <p>Oldest open reports are shown first.</p>
              </div>
              <Link to="/admin/reports" className="admin-panel-link">
                View all <ArrowRight size={13} />
              </Link>
            </div>
            {openReports.length === 0 ? (
              <AdminEmpty
                title={loading ? 'Checking reports…' : 'No open reports'}
                description={loading ? 'Hang tight.' : 'Lovely — the report queue is clear.'}
                icon={Flag}
              />
            ) : (
              <div className="admin-audit-list">
                {openReports.map((report) => (
                  <div className="admin-audit-row" key={report.id}>
                    <span className="admin-audit-icon red"><Flag size={16} /></span>
                    <div className="admin-audit-main">
                      <div className="admin-audit-line">
                        <strong>{REPORT_REASON_LABELS[report.reason] || report.reason}</strong>
                        <span>on “{report.postTitle}”</span>
                      </div>
                      <div className="admin-audit-meta">
                        <ReportStatusBadge status="OPEN" />
                        <span>by {report.reporterName || 'a student'}</span>
                        <span>· {timeAgo(report.createdAt)}</span>
                      </div>
                    </div>
                    <Link to="/admin/reports" className="admin-row-link">Review <ArrowRight size={12} /></Link>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><Sparkles size={17} /> AI review queue</h3>
                <p>Listings waiting for a human decision.</p>
              </div>
              <Link to="/admin/moderation" className="admin-panel-link">
                Open queue <ArrowRight size={13} />
              </Link>
            </div>
            {queue.length === 0 ? (
              <AdminEmpty
                title={loading ? 'Checking the queue…' : 'Queue is clear'}
                description={loading ? 'Hang tight.' : 'No listings need a human review right now.'}
                icon={Sparkles}
              />
            ) : (
              <div className="admin-audit-list">
                {queue.map((item) => (
                  <div className="admin-audit-row" key={item.id}>
                    <span className="admin-audit-icon sun"><Sparkles size={16} /></span>
                    <div className="admin-audit-main">
                      <div className="admin-audit-line">
                        <strong>{item.title}</strong>
                      </div>
                      <div className="admin-audit-meta">
                        <span>by {item.sellerName}</span>
                        <span>· {item.attempts ?? 0} {(item.attempts ?? 0) === 1 ? 'pass' : 'passes'}</span>
                        <span>· queued {timeAgo(item.queuedAt || item.createdAt)}</span>
                      </div>
                      {item.lastFailure && (
                        <div className="admin-audit-reason">“{item.lastFailure}”</div>
                      )}
                    </div>
                    <Link to="/admin/moderation" className="admin-row-link">Decide <ArrowRight size={12} /></Link>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        <div style={{ display: 'grid', gap: 18 }}>
          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><Crown size={17} /> Super admin tools</h3>
                <p>Everything the team can do, plus the audit trail.</p>
              </div>
            </div>
            <div className="admin-panel-body">
              <div className="admin-quick-grid">
                {QUICK_LINKS.map(({ to, title, note, icon: Icon, bg, fg }) => (
                  <Link to={to} className="admin-quick-link" key={to}>
                    <span className="admin-quick-icon" style={{ background: bg, color: fg }}>
                      <Icon size={16} />
                    </span>
                    <span className="admin-quick-copy">
                      <strong>{title}</strong>
                      <small>{note}</small>
                    </span>
                    <ArrowRight size={14} />
                  </Link>
                ))}
              </div>
            </div>
          </section>

          <section className="admin-panel admin-super-access">
            <div className="admin-panel-head">
              <div>
                <h3><ShieldCheck size={17} /> Your access</h3>
                <p>Reserved for the super admin role.</p>
              </div>
            </div>
            <div className="admin-panel-body">
              <ul className="admin-super-list">
                <li><CheckCircle2 size={14} /> Permanent student blocks</li>
                <li><CheckCircle2 size={14} /> Full audit trail with filters</li>
                <li><CheckCircle2 size={14} /> All moderation &amp; report tools</li>
                <li><CheckCircle2 size={14} /> AI review queue decisions</li>
              </ul>
              <p className="admin-text-muted" style={{ fontSize: '0.76rem', marginTop: 10 }}>
                Moderators can handle users, listings and reports, but only super admins
                can see the audit trail or permanently block an account.
              </p>
            </div>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><Users size={17} /> Newest students</h3>
                <p>Latest campus sign-ups.</p>
              </div>
            </div>
            <div className="admin-panel-body">
              {recentUsers.length === 0 ? (
                <p className="admin-text-muted" style={{ fontSize: '0.8rem' }}>
                  {loading ? 'Loading students…' : 'No students yet.'}
                </p>
              ) : (
                <div className="admin-timeline">
                  {recentUsers.map((user) => (
                    <div className="admin-timeline-item" key={user.id}>
                      <span className="admin-timeline-dot" style={{ background: 'var(--mint)' }} />
                      <div>
                        <strong>{user.name}</strong>
                        <small>{user.college?.name || user.email} · {timeAgo(user.createdAt)}</small>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><PackageSearch size={17} /> Freshest listings</h3>
                <p>Newest things students have posted.</p>
              </div>
              <Link to="/admin/posts" className="admin-panel-link">
                View all <ArrowRight size={13} />
              </Link>
            </div>
            <div className="admin-panel-body">
              {recentPosts.length === 0 ? (
                <p className="admin-text-muted" style={{ fontSize: '0.8rem' }}>
                  {loading ? 'Loading listings…' : 'No listings yet.'}
                </p>
              ) : (
                <div className="admin-mini-list">
                  {recentPosts.map((post) => (
                    <div className="admin-audit-row" key={post.id} style={{ padding: '9px 0' }}>
                      <span className="admin-audit-icon green"><PackageSearch size={15} /></span>
                      <div className="admin-audit-main">
                        <div className="admin-audit-line">
                          <strong>{post.title}</strong>
                        </div>
                        <div className="admin-audit-meta">
                          <StatusBadge status={post.status} />
                          <span>· {timeAgo(post.createdAt)}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        </div>
      </div>

      <div className="admin-flex admin-justify-between admin-mt-18">
        <span className="admin-text-muted" style={{ fontSize: '0.72rem' }}>
          Super admin overview · gives you the whole campus picture at a glance.
        </span>
        <button type="button" className="btn btn-light btn-sm" onClick={loadDashboard} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'admin-spin' : ''} /> Refresh
        </button>
      </div>
    </div>
  );
}
