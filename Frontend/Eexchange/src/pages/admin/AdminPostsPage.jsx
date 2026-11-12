import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronRight,
  Flag,
  GraduationCap,
  PackageSearch,
  RotateCcw,
  Search,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { adminApi, catalogApi, getErrorMessage } from '../../lib/api';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { formatDate, formatPrice, timeAgo } from '../../lib/format';
import { POST_STATUS_META } from '../../lib/constants';
import StatusBadge from '../../components/StatusBadge';
import {
  AdminEmpty,
  AdminPager,
  AdminTableSkeleton,
} from '../../components/AdminBits';
import AdminPostDeleteModal from '../../components/AdminPostDeleteModal';
import '../../components/Admin.css';

const PAGE_SIZE = 20;

export default function AdminPostsPage() {
  const [q, setQ] = useState('');
  const debouncedQ = useDebouncedValue(q, 350);
  const [status, setStatus] = useState('');
  const [collegeId, setCollegeId] = useState('');
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [posts, setPosts] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);

  useEffect(() => {
    catalogApi
      .colleges()
      .then((response) => setColleges(response.data || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, status, collegeId]);

  const loadPosts = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.posts.list({
        q: debouncedQ.trim() || undefined,
        status: status || undefined,
        collegeId: collegeId || undefined,
        page,
        size: PAGE_SIZE,
      });
      const data = response.data || {};
      setPosts(data.content || []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load listings.'));
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, status, collegeId, page]);

  useEffect(() => {
    loadPosts();
  }, [loadPosts, refreshKey]);

  const resetFilters = () => {
    setQ('');
    setStatus('');
    setCollegeId('');
    setPage(0);
  };

  const hasFilters = Boolean(q || status || collegeId);

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <span className="admin-eyebrow"><PackageSearch size={12} /> Marketplace</span>
          <h1>Listings</h1>
          <p>Review what students are posting, spot problem items, and act with context.</p>
        </div>
        <div className="admin-head-actions">
          <span className="chip sky"><PackageSearch size={13} /> {totalElements} listings</span>
        </div>
      </div>

      <div className="admin-filters">
        <div className="admin-filter grow">
          <label htmlFor="post-search"><Search size={11} /> Search</label>
          <input
            id="post-search"
            type="search"
            placeholder="Title or seller name…"
            value={q}
            onChange={(event) => setQ(event.target.value)}
          />
        </div>
        <div className="admin-filter">
          <label htmlFor="post-status">Status</label>
          <select
            id="post-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">All statuses</option>
            {Object.entries(POST_STATUS_META).map(([value, meta]) => (
              <option key={value} value={value}>{meta.label}</option>
            ))}
          </select>
        </div>
        <div className="admin-filter">
          <label htmlFor="post-college"><GraduationCap size={11} /> College</label>
          <select
            id="post-college"
            value={collegeId}
            onChange={(event) => setCollegeId(event.target.value)}
          >
            <option value="">All colleges</option>
            {colleges.map((college) => (
              <option key={college.id} value={college.id}>{college.name}</option>
            ))}
          </select>
        </div>
        <div className="admin-filter-actions">
          {hasFilters && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={resetFilters}>
              <RotateCcw size={14} /> Reset
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="admin-table-card">
        {loading ? (
          <AdminTableSkeleton />
        ) : posts.length === 0 ? (
          <AdminEmpty
            title="No listings match"
            description={
              hasFilters
                ? 'Try a different search, status or college.'
                : 'Student listings will appear here.'
            }
            icon={PackageSearch}
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
                    <th>Status</th>
                    <th>Signals</th>
                    <th>Created</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {posts.map((post) => (
                    <tr key={post.id}>
                      <td data-label="Listing">
                        <div className="admin-cell-main">
                          <div className="admin-cell-stack">
                            <Link
                              to={`/admin/posts/${post.id}`}
                              className="admin-row-link"
                              style={{ fontSize: '0.84rem', color: 'var(--ink)' }}
                            >
                              {post.title}
                            </Link>
                            <small>{post.collegeName || 'Unknown college'}</small>
                          </div>
                        </div>
                      </td>
                      <td data-label="Seller">
                        <span className="admin-cell-muted">{post.sellerName}</span>
                      </td>
                      <td data-label="Price">
                        <strong className="admin-nowrap">{formatPrice(post.price)}</strong>
                      </td>
                      <td data-label="Status">
                        <StatusBadge status={post.status} />
                      </td>
                      <td data-label="Signals">
                        <div className="admin-flex">
                          {post.reportCount > 0 && (
                            <span className="admin-report-count"><Flag size={11} /> {post.reportCount}</span>
                          )}
                          {post.aiVerdict && (
                            <span className="admin-ai-verdict"><Sparkles size={10} /> {post.aiVerdict}</span>
                          )}
                          {!post.reportCount && !post.aiVerdict && (
                            <span className="admin-cell-muted">—</span>
                          )}
                        </div>
                      </td>
                      <td data-label="Created">
                        <div className="admin-cell-stack">
                          <span className="admin-cell-muted">{formatDate(post.createdAt)}</span>
                          <small>{timeAgo(post.createdAt)}</small>
                        </div>
                      </td>
                      <td data-label="Actions" style={{ textAlign: 'right' }}>
                        <div className="admin-cell-actions">
                          {post.status !== 'DELETED' && (
                            <button
                              type="button"
                              className="admin-icon-action danger"
                              onClick={() => setDeleteTarget(post)}
                              aria-label={`Delete ${post.title}`}
                              title="Delete listing"
                            >
                              <Trash2 size={15} />
                            </button>
                          )}
                          <Link
                            to={`/admin/posts/${post.id}`}
                            className="admin-icon-action"
                            aria-label={`View ${post.title}`}
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

      <AdminPostDeleteModal
        open={Boolean(deleteTarget)}
        post={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onSuccess={() => setRefreshKey((key) => key + 1)}
      />
    </div>
  );
}
