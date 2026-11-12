import { ChevronLeft, ChevronRight, SearchX } from 'lucide-react';

export const USER_STATUS_META = {
  ACTIVE: { label: 'Active', tone: 'green' },
  TEMP_BLOCKED: { label: 'Temp blocked', tone: 'sun' },
  PERM_BLOCKED: { label: 'Permanently blocked', tone: 'red' },
};

export const REPORT_STATUS_META = {
  OPEN: { label: 'Open', tone: 'sun' },
  RESOLVED: { label: 'Resolved', tone: 'green' },
  DISMISSED: { label: 'Dismissed', tone: 'muted' },
};

export const REPORT_REASON_LABELS = {
  SPAM: 'Spam or duplicate',
  SCAM: 'Possible scam',
  INAPPROPRIATE: 'Inappropriate content',
  PROHIBITED_ITEM: 'Prohibited item',
  OTHER: 'Other concern',
};

export const AUDIT_ACTION_LABELS = {
  BLOCK_TEMP: 'Temporarily blocked a student',
  BLOCK_PERM: 'Permanently blocked a student',
  UNBLOCK: 'Unblocked a student',
  DELETE_POST: 'Deleted a listing',
  APPROVE_POST: 'Approved a listing',
  REJECT_POST: 'Rejected a listing',
  RESOLVE_REPORT: 'Resolved a report',
  DISMISS_REPORT: 'Dismissed a report',
  DEACTIVATE_COLLEGE: 'Deactivated a college',
  ACTIVATE_COLLEGE: 'Activated a college',
};

export const AUDIT_TARGET_LABELS = {
  USER: 'Student',
  POST: 'Listing',
  REPORT: 'Report',
  COLLEGE: 'College',
};

export function UserStatusBadge({ status }) {
  const meta = USER_STATUS_META[status] || { label: status || 'Unknown', tone: 'muted' };
  return (
    <span className={`admin-status ${meta.tone}`}>
      <span className="dot" />
      {meta.label}
    </span>
  );
}

export function ReportStatusBadge({ status }) {
  const meta = REPORT_STATUS_META[status] || { label: status || 'Unknown', tone: 'muted' };
  return <span className={`admin-status ${meta.tone}`}>{meta.label}</span>;
}

export function AdminPager({ page, totalPages, totalElements, onPageChange, unit = 'result' }) {
  const safeTotalPages = Math.max(totalPages || 0, 1);
  return (
    <div className="admin-pager">
      <span className="admin-pager-info">
        {totalElements ?? 0} {unit}{(totalElements ?? 0) === 1 ? '' : 's'}
        {totalPages > 1 ? ` · page ${page + 1} of ${safeTotalPages}` : ''}
      </span>
      <div className="admin-pager-btns">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page <= 0}
        >
          <ChevronLeft size={14} /> Prev
        </button>
        <span>{page + 1}</span>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
        >
          Next <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}

export function AdminEmpty({ title = 'Nothing here yet', description, icon: Icon = SearchX }) {
  return (
    <div className="admin-empty">
      <span className="admin-empty-icon"><Icon size={28} /></span>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  );
}

export function AdminTableSkeleton({ rows = 5 }) {
  return (
    <div style={{ display: 'grid', gap: 10, padding: 18 }}>
      {Array.from({ length: rows }).map((_, index) => (
        <div
          key={index}
          className="admin-skeleton"
          style={{ height: 62, opacity: 1 - index * 0.1 }}
        />
      ))}
    </div>
  );
}

export function AdminDetailSkeleton() {
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div className="admin-skeleton" style={{ height: 150 }} />
      <div className="admin-skeleton" style={{ height: 280 }} />
    </div>
  );
}

export function formatAuditTarget(targetType, targetId) {
  const label = AUDIT_TARGET_LABELS[targetType] || targetType || 'Item';
  return targetId ? `${label} #${targetId}` : label;
}
