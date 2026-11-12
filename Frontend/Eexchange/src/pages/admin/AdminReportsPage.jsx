import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowUpRight,
  Check,
  Flag,
  Inbox,
  MessageSquareQuote,
  RefreshCw,
  ShieldCheck,
  X,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { formatDate, timeAgo } from '../../lib/format';
import {
  AdminEmpty,
  AdminPager,
  AdminTableSkeleton,
  REPORT_REASON_LABELS,
  ReportStatusBadge,
} from '../../components/AdminBits';
import AdminReportActionModal from '../../components/AdminReportActionModal';
import '../../components/Admin.css';

const PAGE_SIZE = 20;

const TABS = [
  { value: 'OPEN', label: 'Open', icon: Inbox },
  { value: 'RESOLVED', label: 'Resolved', icon: Check },
  { value: 'DISMISSED', label: 'Dismissed', icon: X },
];

export default function AdminReportsPage() {
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(0);
  const [reports, setReports] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [action, setAction] = useState({ open: false, action: 'RESOLVE', report: null });

  const loadReports = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.reports.list({ status, page, size: PAGE_SIZE });
      const data = response.data || {};
      setReports(data.content || []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load reports.'));
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    loadReports();
  }, [loadReports, refreshKey]);

  const changeStatus = (nextStatus) => {
    setStatus(nextStatus);
    setPage(0);
  };

  const openAction = (nextAction, report) =>
    setAction({ open: true, action: nextAction, report });
  const closeAction = () => setAction((previous) => ({ ...previous, open: false }));

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <span className="admin-eyebrow"><Flag size={12} /> Trust &amp; safety</span>
          <h1>Reports</h1>
          <p>Every report is a real person asking for help. Review the context, then resolve or dismiss.</p>
        </div>
        <div className="admin-head-actions">
          <button type="button" className="btn btn-light btn-sm" onClick={() => setRefreshKey((key) => key + 1)} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'admin-spin' : ''} /> Refresh
          </button>
        </div>
      </div>

      <div className="inbox-tabs">
        {TABS.map(({ value, label, icon: Icon }) => (
          <button
            key={value}
            type="button"
            className={`inbox-tab ${status === value ? 'active' : ''}`}
            onClick={() => changeStatus(value)}
          >
            <Icon size={16} /> {label}
            {value === status && <span className="tab-badge">{totalElements}</span>}
          </button>
        ))}
      </div>

      {error && <div className="alert error">{error}</div>}

      {loading ? (
        <div className="admin-table-card"><AdminTableSkeleton rows={4} /></div>
      ) : reports.length === 0 ? (
        <div className="admin-table-card">
          <AdminEmpty
            title={
              status === 'OPEN'
                ? 'The report queue is clear'
                : status === 'RESOLVED'
                  ? 'No resolved reports yet'
                  : 'No dismissed reports yet'
            }
            description={
              status === 'OPEN'
                ? 'Nothing needs your attention right now. Go enjoy a chai.'
                : 'Reports you action will show up here for future context.'
            }
            icon={status === 'OPEN' ? ShieldCheck : Flag}
          />
        </div>
      ) : (
        <>
          <div className="admin-report-grid">
            {reports.map((report) => (
              <article className="admin-report-card" key={report.id}>
                <div className="admin-report-top">
                  <span className="admin-report-reason">
                    <Flag size={12} /> {REPORT_REASON_LABELS[report.reason] || report.reason}
                  </span>
                  <ReportStatusBadge status={status} />
                </div>

                <Link to={`/admin/posts/${report.postId}`} className="admin-report-post">
                  <div>
                    <small>Reported listing</small>
                    <strong>{report.postTitle}</strong>
                  </div>
                  <ArrowUpRight size={15} style={{ color: 'var(--muted)', flexShrink: 0 }} />
                </Link>

                {report.comment && (
                  <p className="admin-report-comment">
                    <MessageSquareQuote size={12} style={{ verticalAlign: -1, marginRight: 4 }} />
                    {report.comment}
                  </p>
                )}

                <div className="admin-report-meta">
                  <span>By {report.reporterName || 'a student'} · {timeAgo(report.createdAt)}</span>
                  <span>on {formatDate(report.createdAt, 'dd MMM yyyy')}</span>
                </div>

                <div className="admin-report-meta">
                  <span className="admin-report-count"><Flag size={10} /> {report.openReportCount} open on this listing</span>
                  {report.postStatus && (
                    <span className="admin-status muted">{report.postStatus.replaceAll('_', ' ')}</span>
                  )}
                </div>

                {status === 'OPEN' && (
                  <div className="admin-report-actions">
                    <button
                      type="button"
                      className="btn btn-light btn-sm"
                      onClick={() => openAction('DISMISS', report)}
                    >
                      <X size={14} /> Dismiss
                    </button>
                    <button
                      type="button"
                      className="btn btn-mint btn-sm"
                      onClick={() => openAction('RESOLVE', report)}
                    >
                      <Check size={14} /> Resolve
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>

          <div className="admin-table-card" style={{ marginTop: 16 }}>
            <AdminPager
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
              unit="report"
            />
          </div>
        </>
      )}

      <AdminReportActionModal
        open={action.open}
        action={action.action}
        report={action.report}
        onClose={closeAction}
        onSuccess={() => setRefreshKey((key) => key + 1)}
      />
    </div>
  );
}
