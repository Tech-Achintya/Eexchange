import { POST_STATUS_META } from '../lib/constants';

export default function StatusBadge({ status, className = '' }) {
  const meta = POST_STATUS_META[status] || { label: status || 'Unknown', tone: 'lav' };
  return (
    <span className={`status-badge status-${meta.tone} ${className}`.trim()}>
      <span className="status-dot" />
      {meta.label}
    </span>
  );
}
