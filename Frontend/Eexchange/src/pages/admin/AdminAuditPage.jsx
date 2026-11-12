import { useCallback, useEffect, useState } from 'react';
import {
  Ban,
  CheckCircle2,
  ClipboardList,
  Filter,
  GraduationCap,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Unlock,
  UserRound,
  XCircle,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { useAdmin } from '../../context/AdminContext';
import { formatDate, timeAgo } from '../../lib/format';
import {
  AdminEmpty,
  AdminPager,
  AdminTableSkeleton,
  AUDIT_ACTION_LABELS,
  formatAuditTarget,
} from '../../components/AdminBits';
import '../../components/Admin.css';

const PAGE_SIZE = 20;

const TARGET_OPTIONS = [
  { value: 'USER', label: 'Students' },
  { value: 'POST', label: 'Listings' },
  { value: 'REPORT', label: 'Reports' },
  { value: 'COLLEGE', label: 'Colleges' },
];

function actionVisual(action) {
  if (action?.startsWith('BLOCK')) return { icon: Ban, tone: 'red' };
  if (action === 'UNBLOCK') return { icon: Unlock, tone: 'green' };
  if (action === 'DELETE_POST') return { icon: Trash2, tone: 'red' };
  if (action === 'APPROVE_POST') return { icon: CheckCircle2, tone: 'green' };
  if (action === 'REJECT_POST') return { icon: XCircle, tone: 'sun' };
  if (action === 'RESOLVE_REPORT') return { icon: ShieldCheck, tone: 'green' };
  if (action === 'DISMISS_REPORT') return { icon: ShieldAlert, tone: 'sun' };
  if (action?.includes('COLLEGE')) return { icon: GraduationCap, tone: 'sky' };
  return { icon: ClipboardList, tone: 'sky' };
}

function targetIcon(targetType) {
  if (targetType === 'USER') return <UserRound size={13} />;
  if (targetType === 'POST') return <ClipboardList size={13} />;
  if (targetType === 'REPORT') return <ShieldAlert size={13} />;
  return <GraduationCap size={13} />;
}

export default function AdminAuditPage() {
  const { isSuperAdmin } = useAdmin();
  const [targetType, setTargetType] = useState('');
  const [adminIdInput, setAdminIdInput] = useState('');
  const [adminId, setAdminId] = useState('');
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [logs, setLogs] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadAudit = useCallback(async () => {
    if (!isSuperAdmin) return;
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.audit.list({
        targetType: targetType || undefined,
        adminId: adminId || undefined,
        page,
        size: PAGE_SIZE,
      });
      const data = response.data || {};
      setLogs(data.content || []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load the audit log.'));
    } finally {
      setLoading(false);
    }
  }, [isSuperAdmin, targetType, adminId, page]);

  useEffect(() => {
    loadAudit();
  }, [loadAudit, refreshKey]);

  const applyFilters = (event) => {
    event?.preventDefault();
    setPage(0);
    setAdminId(adminIdInput.trim());
  };

  const resetFilters = () => {
    setTargetType('');
    setAdminIdInput('');
    setAdminId('');
    setPage(0);
  };

  if (!isSuperAdmin) {
    return (
      <div className="admin-page">
        <div className="admin-table-card">
          <AdminEmpty
            title="Super admin only"
            description="Audit history is restricted to super admins. Ask a super admin if you need context from an old decision."
            icon={ShieldAlert}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <span className="admin-eyebrow"><ClipboardList size={12} /> Accountability</span>
          <h1>Audit trail</h1>
          <p>Every moderation decision, permanently logged. Filter by target or admin.</p>
        </div>
        <div className="admin-head-actions">
          <span className="chip lav"><ShieldCheck size={13} /> Super admin</span>
          <button type="button" className="btn btn-light btn-sm" onClick={() => setRefreshKey((key) => key + 1)} disabled={loading}>
            <RefreshCw size={14} className={loading ? 'admin-spin' : ''} /> Refresh
          </button>
        </div>
      </div>

      <form className="admin-filters" onSubmit={applyFilters}>
        <div className="admin-filter">
          <label htmlFor="audit-target"><Filter size={11} /> Target type</label>
          <select
            id="audit-target"
            value={targetType}
            onChange={(event) => {
              setTargetType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Everything</option>
            {TARGET_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
        <div className="admin-filter grow">
          <label htmlFor="audit-admin"><Search size={11} /> Admin ID</label>
          <input
            id="audit-admin"
            type="number"
            min="1"
            placeholder="Filter by a specific admin ID…"
            value={adminIdInput}
            onChange={(event) => setAdminIdInput(event.target.value)}
          />
        </div>
        <div className="admin-filter-actions">
          <button type="submit" className="btn btn-dark btn-sm">
            <Search size={14} /> Apply
          </button>
          {(targetType || adminId) && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={resetFilters}>
              <RotateCcw size={14} /> Reset
            </button>
          )}
        </div>
      </form>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-table-card">
        {loading ? (
          <AdminTableSkeleton />
        ) : logs.length === 0 ? (
          <AdminEmpty
            title="No audit entries"
            description="Moderation actions will appear here automatically."
            icon={ClipboardList}
          />
        ) : (
          <>
            <div className="admin-audit-list">
              {logs.map((entry) => {
                const visual = actionVisual(entry.action);
                const Icon = visual.icon;
                return (
                  <div className="admin-audit-row" key={entry.id}>
                    <span className={`admin-audit-icon ${visual.tone}`}>
                      <Icon size={16} />
                    </span>
                    <div className="admin-audit-main">
                      <div className="admin-audit-line">
                        <strong>{entry.adminUsername}</strong>
                        <span>{AUDIT_ACTION_LABELS[entry.action] || entry.action?.replaceAll('_', ' ').toLowerCase()}</span>
                      </div>
                      <div className="admin-audit-meta">
                        <span className="admin-status muted">
                          {targetIcon(entry.targetType)} {formatAuditTarget(entry.targetType, entry.targetId)}
                        </span>
                        <span>Admin #{entry.adminId}</span>
                        <span>{formatDate(entry.createdAt, 'dd MMM yyyy, HH:mm')}</span>
                        <span>· {timeAgo(entry.createdAt)}</span>
                      </div>
                      {entry.reason && (
                        <div className="admin-audit-reason">“{entry.reason}”</div>
                      )}
                      {entry.metadata && Object.keys(entry.metadata).length > 0 && (
                        <details>
                          <summary
                            style={{
                              cursor: 'pointer',
                              fontSize: '0.68rem',
                              fontWeight: 700,
                              color: 'var(--lavender)',
                              marginTop: 6,
                            }}
                          >
                            Metadata
                          </summary>
                          <div className="admin-audit-meta-data">
                            {Object.entries(entry.metadata).map(([key, value]) => (
                              <div key={key}>
                                <strong>{key}:</strong> {String(value ?? '—')}
                              </div>
                            ))}
                          </div>
                        </details>
                      )}
                    </div>
                    <span className="admin-text-muted" style={{ fontSize: '0.66rem', fontWeight: 700, whiteSpace: 'nowrap' }}>
                      {formatAuditTarget(entry.targetType, entry.targetId)}
                    </span>
                  </div>
                );
              })}
            </div>
            <AdminPager
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
              unit="entry"
            />
          </>
        )}
      </div>
    </div>
  );
}
