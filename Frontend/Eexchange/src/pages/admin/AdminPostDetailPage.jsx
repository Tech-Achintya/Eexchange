import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  CalendarDays,
  Flag,
  ImageOff,
  PackageSearch,
  Sparkles,
  Tag,
  Trash2,
  UserRound,
} from 'lucide-react';
import { adminApi, getErrorMessage } from '../../lib/api';
import { formatDate, formatPrice, timeAgo } from '../../lib/format';
import { CONDITION_LABELS } from '../../lib/constants';
import StatusBadge from '../../components/StatusBadge';
import {
  AdminDetailSkeleton,
  AdminEmpty,
  REPORT_REASON_LABELS,
  ReportStatusBadge,
} from '../../components/AdminBits';
import AdminPostDeleteModal from '../../components/AdminPostDeleteModal';
import '../../components/Admin.css';

export default function AdminPostDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const loadPost = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.posts.detail(id);
      setPost(response.data);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load this listing.'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadPost();
  }, [loadPost, refreshKey]);

  if (loading && !post) {
    return (
      <div className="admin-page"><AdminDetailSkeleton /></div>
    );
  }

  if (error && !post) {
    return (
      <div className="admin-page">
        <Link to="/admin/posts" className="admin-row-link" style={{ marginBottom: 16 }}>
          <ArrowLeft size={14} /> Back to listings
        </Link>
        <div className="alert error">{error}</div>
        <AdminEmpty title="Listing not available" description="It may have been deleted or the link is wrong." icon={PackageSearch} />
      </div>
    );
  }

  if (!post) return null;

  const openReports = (post.reports || []).filter((report) => report.status === 'OPEN');
  const aiResult = post.aiReviewResult && Object.keys(post.aiReviewResult).length
    ? JSON.stringify(post.aiReviewResult, null, 2)
    : null;

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <button type="button" className="admin-row-link" onClick={() => navigate('/admin/posts')} style={{ marginBottom: 10 }}>
            <ArrowLeft size={14} /> All listings
          </button>
          <h1>Listing detail</h1>
          <p>Full context before you moderate. Reports are shown at the bottom.</p>
        </div>
        <div className="admin-head-actions">
          <button type="button" className="btn btn-light btn-sm" onClick={() => loadPost()} disabled={loading}>
            Refresh
          </button>
          {post.status !== 'DELETED' && (
            <button type="button" className="btn btn-danger btn-sm" onClick={() => setDeleteOpen(true)}>
              <Trash2 size={15} /> Delete listing
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-detail-grid">
        <div>
          <section className="admin-detail-card">
            <div className="admin-detail-body">
              {post.imageUrls?.length ? (
                <div className="admin-post-gallery">
                  {post.imageUrls.map((url, index) => (
                    <a key={url} href={url} target="_blank" rel="noreferrer" aria-label={`Open image ${index + 1}`}>
                      <img src={url} alt={`${post.title} photo ${index + 1}`} loading="lazy" />
                    </a>
                  ))}
                </div>
              ) : (
                <div className="admin-post-gallery">
                  <div className="admin-thumb-placeholder" style={{ aspectRatio: '1 / 1', borderRadius: 12, background: 'var(--cream)' }}>
                    <ImageOff size={26} />
                  </div>
                </div>
              )}

              <h2 className="admin-post-title">{post.title}</h2>
              <div className="admin-post-price">{formatPrice(post.price)}</div>

              <div className="admin-tags">
                <StatusBadge status={post.status} />
                <span className="admin-status muted">{CONDITION_LABELS[post.condition] || post.condition}</span>
                <span className="admin-status sky"><Tag size={11} /> {post.categoryName}</span>
                <span className="admin-status lav">#{post.id}</span>
                {openReports.length > 0 && (
                  <span className="admin-report-count"><Flag size={11} /> {openReports.length} open report{openReports.length === 1 ? '' : 's'}</span>
                )}
              </div>

              <p className="admin-description">{post.description}</p>

              {post.rejectionReason && (
                <div className="admin-callout danger">
                  <AlertTriangle size={15} />
                  <div><strong>Rejection reason</strong><div>{post.rejectionReason}</div></div>
                </div>
              )}
              {post.hiddenReason && (
                <div className="admin-callout sun">
                  <AlertTriangle size={15} />
                  <div><strong>Hidden reason</strong><div>{post.hiddenReason}</div></div>
                </div>
              )}

              <div className="admin-ai-box">
                <strong><Sparkles size={13} /> AI review snapshot</strong>
                {aiResult ? (
                  <pre>{aiResult}</pre>
                ) : (
                  <p className="admin-text-muted" style={{ fontSize: '0.75rem' }}>
                    No AI review data was stored for this listing.
                  </p>
                )}
              </div>
            </div>
          </section>

          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><Flag size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Reports on this listing</h2>
                <p>Every report ever submitted, newest review context first.</p>
              </div>
              <Link to="/admin/reports" className="admin-row-link">Open report queue</Link>
            </div>
            <div className="admin-detail-body">
              {post.reports?.length ? (
                <div className="admin-timeline">
                  {post.reports.map((report) => (
                    <div className="admin-timeline-item" key={report.id}>
                      <span
                        className="admin-timeline-dot"
                        style={{ background: report.status === 'OPEN' ? 'var(--red)' : 'var(--green)' }}
                      />
                      <div>
                        <div className="admin-flex">
                          <strong>{REPORT_REASON_LABELS[report.reason] || report.reason}</strong>
                          <ReportStatusBadge status={report.status} />
                        </div>
                        <small>
                          Reported by {report.reporterName || 'a student'} · {formatDate(report.createdAt, 'dd MMM yyyy, HH:mm')} · {timeAgo(report.createdAt)}
                        </small>
                        {report.comment && <span className="admin-timeline-reason">“{report.comment}”</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="admin-text-muted" style={{ fontSize: '0.8rem' }}>
                  Nobody has reported this listing. Nice.
                </p>
              )}
            </div>
          </section>
        </div>

        <div>
          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><PackageSearch size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Listing info</h2>
                <p>Where it lives and when it was last touched.</p>
              </div>
            </div>
            <div className="admin-detail-body">
              <div className="admin-facts">
                <div className="admin-fact">
                  <small>College</small>
                  <strong>{post.collegeName || 'Unknown'}</strong>
                </div>
                <div className="admin-fact">
                  <small>Category</small>
                  <strong>{post.categoryName}</strong>
                </div>
                <div className="admin-fact">
                  <small>Created</small>
                  <strong>{formatDate(post.createdAt, 'dd MMM yyyy, HH:mm')}</strong>
                </div>
                <div className="admin-fact">
                  <small>Updated</small>
                  <strong>{formatDate(post.updatedAt, 'dd MMM yyyy, HH:mm')}</strong>
                </div>
                <div className="admin-fact full">
                  <small><CalendarDays size={10} /> Expires</small>
                  <strong>{post.expiresAt ? formatDate(post.expiresAt, 'dd MMM yyyy, HH:mm') : 'No expiry'}</strong>
                </div>
              </div>
            </div>
          </section>

          <section className="admin-detail-card">
            <div className="admin-detail-head">
              <div>
                <h2><UserRound size={18} style={{ verticalAlign: -2, marginRight: 6 }} />Seller</h2>
                <p>The student behind this listing.</p>
              </div>
            </div>
            <div className="admin-detail-body">
              <div className="admin-flex admin-justify-between">
                <div>
                  <strong style={{ fontSize: '0.95rem' }}>{post.sellerName}</strong>
                  <p className="admin-text-muted" style={{ fontSize: '0.72rem', marginTop: 2 }}>
                    Student #{post.sellerId}
                  </p>
                </div>
                <Link to={`/admin/users/${post.sellerId}`} className="btn btn-light btn-sm">
                  View profile
                </Link>
              </div>
              <div className="admin-callout info" style={{ marginTop: 16, marginBottom: 0 }}>
                <AlertTriangle size={15} />
                <span>
                  Check the seller’s open report count and recent moderation history before
                  deleting an item. Context matters.
                </span>
              </div>
            </div>
          </section>
        </div>
      </div>

      <AdminPostDeleteModal
        open={deleteOpen}
        post={post}
        onClose={() => setDeleteOpen(false)}
        onSuccess={() => setRefreshKey((key) => key + 1)}
      />
    </div>
  );
}
