import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ClipboardList,
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
import { AdminEmpty, ReportStatusBadge, REPORT_REASON_LABELS } from '../../components/AdminBits';
import '../../components/Admin.css';

const EMPTY_STATS = {
  users: 0,
  listings: 0,
  openReports: 0,
  needsReview: 0,
};

export default function AdminDashboardPage() {
  const { admin, isSuperAdmin } = useAdmin();
  const [stats, setStats] = useState(EMPTY_STATS);
  const [recentUsers, setRecentUsers] = useState([]);
  const [recentPosts, setRecentPosts] = useState([]);
  const [openReports, setOpenReports] = useState([]);
  const [reviewQueue, setReviewQueue] = useState([]);
  const [recentAudit, setRecentAudit] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError('');

    const requests = [
      adminApi.users.list({ page: 0, size: 4 }),
      adminApi.users.list({ page: 0, size: 1 }),
      adminApi.posts.list({ page: 0, size: 4 }),
      adminApi.posts.list({ page: 0, size: 1 }),
      adminApi.reports.list({ status: 'OPEN', page: 0, size: 4 }),
      adminApi.posts.list({ status: 'PENDING_REVIEW', page: 0, size: 1 }),
      adminApi.posts.list({ status: 'MANUAL_REVIEW', page: 0, size: 1 }),
      adminApi.moderation.queue({ page: 0, size: 4 }),
    ];
    if (isSuperAdmin) requests.push(adminApi.audit.list({ page: 0, size: 5 }));

    const results = await Promise.allSettled(requests);
    const valueAt = (index) =>
      results[index]?.status === 'fulfilled' ? results[index].value.data : null;

    const latestUsers = valueAt(0);
    const userTotal = valueAt(1);
    const latestPosts = valueAt(2);
    const postTotal = valueAt(3);
    const reports = valueAt(4);
    const pending = valueAt(5);
    const manual = valueAt(6);
    const queue = valueAt(7);

    const failures = results.filter((result) => result.status === 'rejected');
    if (failures.length === results.length) {
      const first = failures[0]?.reason;
      setError(getErrorMessage(first, 'Could not load the admin overview.'));
    }

    setRecentUsers(latestUsers?.content || []);
    setRecentPosts(latestPosts?.content || []);
    setOpenReports(reports?.content || []);
    setReviewQueue(queue?.content || []);
    setStats({
      users: userTotal?.totalElements ?? 0,
      listings: postTotal?.totalElements ?? 0,
      openReports: reports?.totalElements ?? 0,
      needsReview:
        (pending?.totalElements ?? 0) + (manual?.totalElements ?? 0),
    });
    if (isSuperAdmin) {
      setRecentAudit(valueAt(8)?.content || []);
    }
    setLoading(false);
  }, [isSuperAdmin]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const firstName = admin?.username || 'there';

  return (
    <div className="admin-page">
      <div className="admin-hero-strip admin-hero-moderator">
        <div className="admin-hero-copy">
          <span className="admin-eyebrow"><ShieldCheck size={12} /> Moderator workspace</span>
          <h2>Hey {firstName}, campus looks lively today.</h2>
          <p>
            A quick pulse on students, listings and reports. Little actions here keep
            the whole marketplace friendly and safe.
          </p>
        </div>
        <div className="admin-hero-badge">🛡️</div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-stat-grid">
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
            label: 'Needs review',
            value: stats.needsReview,
            icon: ShieldCheck,
            bg: 'var(--sun-soft)',
            fg: '#997000',
            note: 'Pending + manual review',
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
                <p>Listings the AI escalated for a human decision.</p>
              </div>
              <Link to="/admin/moderation" className="admin-panel-link">
                Open queue <ArrowRight size={13} />
              </Link>
            </div>
            {reviewQueue.length === 0 ? (
              <AdminEmpty
                title={loading ? 'Checking the queue…' : 'Queue is clear'}
                description={loading ? 'Hang tight.' : 'No listings need a human review right now.'}
                icon={Sparkles}
              />
            ) : (
              <div className="admin-audit-list">
                {reviewQueue.map((item) => (
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

          <section className="admin-panel">
            <div className="admin-panel-head">
              <div>
                <h3><PackageSearch size={17} /> Freshest listings</h3>
                <p>The newest things students have posted.</p>
              </div>
              <Link to="/admin/posts" className="admin-panel-link">
                View all <ArrowRight size={13} />
              </Link>
            </div>
            {recentPosts.length === 0 ? (
              <AdminEmpty
                title={loading ? 'Loading listings…' : 'No listings yet'}
                description={loading ? 'Hang tight.' : 'New listings will show up here.'}
                icon={PackageSearch}
              />
            ) : (
              <div className="admin-audit-list">
                {recentPosts.map((post) => (
                  <div className="admin-audit-row" key={post.id}>
                    <span className="admin-audit-icon green"><PackageSearch size={16} /></span>
                    <div className="admin-audit-main">
                      <div className="admin-audit-line">
                        <strong>{post.title}</strong>
                      </div>
                      <div className="admin-audit-meta">
                        <StatusBadge status={post.status} />
                        <span>by {post.sellerName}</span>
                        <span>· {timeAgo(post.createdAt)}</span>
                      </div>
                    </div>
                    <Link to={`/admin/posts/${post.id}`} className="admin-row-link">
                      Open <ArrowRight size={12} />
                    </Link>
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
                <h3>Quick actions</h3>
                <p>Jump straight to the work.</p>
              </div>
            </div>
            <div className="admin-panel-body">
              <div className="admin-quick-grid">
                <Link to="/admin/reports" className="admin-quick-link">
                  <span className="admin-quick-icon" style={{ background: 'var(--red-soft)', color: '#bd2b3a' }}>
                    <Flag size={16} />
                  </span>
                  <span className="admin-quick-copy">
                    <strong>Resolve reports</strong>
                    <small>{stats.openReports} waiting in the queue</small>
                  </span>
                  <ArrowRight size={14} />
                </Link>
                <Link to="/admin/users" className="admin-quick-link">
                  <span className="admin-quick-icon" style={{ background: 'var(--sky-soft)', color: '#2b6fd4' }}>
                    <Users size={16} />
                  </span>
                  <span className="admin-quick-copy">
                    <strong>Find a student</strong>
                    <small>Accounts, status &amp; block history</small>
                  </span>
                  <ArrowRight size={14} />
                </Link>
                <Link to="/admin/posts" className="admin-quick-link">
                  <span className="admin-quick-icon" style={{ background: 'var(--mint-soft)', color: '#0d7f75' }}>
                    <PackageSearch size={16} />
                  </span>
                  <span className="admin-quick-copy">
                    <strong>Browse listings</strong>
                    <small>Search by title, seller or status</small>
                  </span>
                  <ArrowRight size={14} />
                </Link>
                <Link to="/admin/moderation" className="admin-quick-link">
                  <span className="admin-quick-icon" style={{ background: 'var(--sun-soft)', color: '#997000' }}>
                    <Sparkles size={16} />
                  </span>
                  <span className="admin-quick-copy">
                    <strong>AI review queue</strong>
                    <small>Approve, reject or requeue flagged listings</small>
                  </span>
                  <ArrowRight size={14} />
                </Link>
                {isSuperAdmin && (
                  <Link to="/admin/audit-log" className="admin-quick-link">
                    <span className="admin-quick-icon" style={{ background: 'var(--lavender-soft)', color: '#6653e0' }}>
                      <ClipboardList size={16} />
                    </span>
                    <span className="admin-quick-copy">
                      <strong>Audit trail</strong>
                      <small>Every moderator action, logged</small>
                    </span>
                    <ArrowRight size={14} />
                  </Link>
                )}
              </div>
            </div>
          </section>

          {isSuperAdmin && (
            <section className="admin-panel">
              <div className="admin-panel-head">
                <div>
                  <h3><ClipboardList size={17} /> Recent activity</h3>
                  <p>Latest moderator actions.</p>
                </div>
              </div>
              <div className="admin-panel-body">
                {recentAudit.length === 0 ? (
                  <p className="admin-text-muted" style={{ fontSize: '0.8rem' }}>
                    {loading ? 'Loading activity…' : 'No admin activity recorded yet.'}
                  </p>
                ) : (
                  <div className="admin-timeline">
                    {recentAudit.map((item) => (
                      <div className="admin-timeline-item" key={item.id}>
                        <span className="admin-timeline-dot" />
                        <div>
                          <strong>{item.adminUsername}</strong>
                          <small>
                            {item.action?.replaceAll('_', ' ').toLowerCase()} · {timeAgo(item.createdAt)}
                          </small>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </section>
          )}

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
        </div>
      </div>

      <div className="admin-flex admin-justify-between admin-mt-18">
        <span className="admin-text-muted" style={{ fontSize: '0.72rem' }}>
          Overview refreshes when you open it. Use the button for a manual refresh.
        </span>
        <button type="button" className="btn btn-light btn-sm" onClick={loadDashboard} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'admin-spin' : ''} /> Refresh
        </button>
      </div>
    </div>
  );
}
