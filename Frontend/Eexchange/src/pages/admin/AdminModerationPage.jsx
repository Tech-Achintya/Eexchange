import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  Check,
  ChevronRight,
  RefreshCw,
  RotateCcw,
  Sparkles,
  X,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { formatPrice, timeAgo } from '../../lib/format';
import { AdminEmpty, AdminPager, AdminTableSkeleton } from '../../components/AdminBits';
import AdminModerationModal from '../../components/AdminModerationModal';
import '../../components/Admin.css';

const PAGE_SIZE = 20;

export default function AdminModerationPage() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [action, setAction] = useState(null);

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.moderation.queue({ page, size: PAGE_SIZE });
      const data = response.data || {};
      setItems(data.content || []);
      setTotalPages(data.totalPages ?? 0);
      setTotalElements(data.totalElements ?? 0);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load the AI review queue.'));
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadQueue();
  }, [loadQueue, refreshKey]);

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <span className="admin-eyebrow"><Sparkles size={12} /> AI moderation</span>
          <h1>Review queue</h1>
          <p>
            Listings the AI pass could not confidently approve, or asked to retry later.
            Approve them, reject them with a reason, or send them back through AI review.
          </p>
        </div>
        <div className="admin-head-actions">
          <span className="chip sun"><Sparkles size={13} /> {totalElements} waiting</span>
          <button
            type="button"
            className="btn btn-light btn-sm"
            onClick={() => setRefreshKey((key) => key + 1)}
            disabled={loading}
          >
            <RefreshCw size={14} className={loading ? 'admin-spin' : ''} /> Refresh
          </button>
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-callout info" style={{ marginBottom: 18 }}>
        <Sparkles size={15} />
        <div>
          <strong>Why listings land here</strong>
          <div style={{ marginTop: 3 }}>
            The AI flagged something it could not settle on its own — a low-confidence
            verdict, a possible prohibited item, or a moderation service failure.
            A human decision is needed.
          </div>
        </div>
      </div>

      <div className="admin-table-card">
        {loading ? (
          <AdminTableSkeleton />
        ) : items.length === 0 ? (
          <AdminEmpty
            title="The queue is clear"
            description="Nothing needs a human review right now. New AI escalations will appear here automatically."
            icon={Check}
          />
        ) : (
          <>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Listing</th>
                    <th>Seller</th>
                    <th>Price</th>
                    <th>Attempts</th>
                    <th>Queued</th>
                    <th style={{ textAlign: 'right' }}>Decision</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td data-label="Listing">
                        <div className="admin-cell-main">
                          {item.imageUrls?.[0] ? (
                            <img
                              src={item.imageUrls[0]}
                              alt=""
                              style={{
                                width: 44,
                                height: 44,
                                borderRadius: 10,
                                objectFit: 'cover',
                                border: '1px solid var(--line)',
                                flexShrink: 0,
                              }}
                            />
                          ) : (
                            <span
                              className="admin-row-avatar"
                              style={{ background: 'var(--sun-soft)', color: '#997000' }}
                            >
                              <Sparkles size={16} />
                            </span>
                          )}
                          <div className="admin-cell-stack">
                            <Link
                              to={`/admin/posts/${item.id}`}
                              className="admin-row-link"
                              style={{ fontSize: '0.84rem', color: 'var(--ink)' }}
                            >
                              {item.title}
                            </Link>
                            <small>{item.collegeName || 'Unknown college'}</small>
                            {item.queueReason === 'RETRY_LATER' ? (
                              <small style={{ color: '#23589f', display: 'flex', gap: 4, alignItems: 'center' }}>
                                <RefreshCw size={11} /> AI retry pending
                              </small>
                            ) : (
                              <small style={{ color: '#997000', display: 'flex', gap: 4, alignItems: 'center' }}>
                                <Sparkles size={11} /> Manual review
                              </small>
                            )}
                            {item.lastFailure && (
                              <small style={{ color: '#a82735', display: 'flex', gap: 4, alignItems: 'center' }}>
                                <AlertTriangle size={11} /> {item.lastFailure}
                              </small>
                            )}
                          </div>
                        </div>
                      </td>
                      <td data-label="Seller">
                        <span className="admin-cell-muted">{item.sellerName}</span>
                      </td>
                      <td data-label="Price">
                        <strong className="admin-nowrap">{formatPrice(item.price)}</strong>
                      </td>
                      <td data-label="Attempts">
                        <span className={`admin-status ${item.attempts > 2 ? 'red' : 'muted'}`}>
                          {item.attempts ?? 0} {item.attempts === 1 ? 'pass' : 'passes'}
                        </span>
                      </td>
                      <td data-label="Queued">
                        <div className="admin-cell-stack">
                          <span className="admin-cell-muted">{timeAgo(item.queuedAt || item.createdAt)}</span>
                          <small>Posted {timeAgo(item.createdAt)}</small>
                        </div>
                      </td>
                      <td data-label="Decision" style={{ textAlign: 'right' }}>
                        <div className="admin-cell-actions">
                          <button
                            type="button"
                            className="btn btn-mint btn-sm"
                            onClick={() => setAction({ type: 'approve', item })}
                            title="Approve listing"
                          >
                            <Check size={14} /> Approve
                          </button>
                          <button
                            type="button"
                            className="btn btn-danger btn-sm"
                            onClick={() => setAction({ type: 'reject', item })}
                            title="Reject listing"
                          >
                            <X size={14} /> Reject
                          </button>
                          <button
                            type="button"
                            className="admin-icon-action"
                            onClick={() => setAction({ type: 'requeue', item })}
                            title={item.queueReason === 'RETRY_LATER' ? 'Trigger fresh AI retry' : 'Requeue for AI review'}
                            aria-label={`Requeue ${item.title} for AI review`}
                          >
                            <RotateCcw size={15} />
                          </button>
                          <Link
                            to={`/admin/posts/${item.id}`}
                            className="admin-icon-action"
                            aria-label={`View ${item.title}`}
                            title="View listing"
                          >
                            <ChevronRight size={16} />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <AdminPager
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
              unit="listing"
            />
          </>
        )}
      </div>

      <AdminModerationModal
        open={Boolean(action)}
        action={action?.type}
        item={action?.item}
        onClose={() => setAction(null)}
        onSuccess={() => setRefreshKey((key) => key + 1)}
      />
    </div>
  );
}
