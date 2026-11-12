import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  Eye,
  Handshake,
  Loader2,
  Package,
  Pencil,
  Plus,
  Trash2,
} from 'lucide-react';
import { getErrorMessage, postsApi } from '../lib/api';
import { firstImageOf, formatDate, formatPrice, pluralize } from '../lib/format';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import { ListSkeleton } from '../components/Loaders';
import './Dashboard.css';

const TABS = [
  { value: '', label: 'All listings' },
  { value: 'APPROVED', label: 'Live' },
  { value: 'PENDING_REVIEW', label: 'In review' },
  { value: 'SOLD', label: 'Sold' },
  { value: 'REJECTED', label: 'Rejected' },
];

export default function MyPostsPage() {
  const [status, setStatus] = useState('');
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [soldLoading, setSoldLoading] = useState(null);

  const fetchPosts = useCallback(
    async (pageNumber = 0) => {
      setLoading(true);
      setError('');
      try {
        const params = { page: pageNumber, size: 10 };
        if (status) params.status = status;
        const response = await postsApi.mine(params);
        const data = response.data || {};
        setPosts(data.content || []);
        setPage(data.page ?? pageNumber);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      } catch (requestError) {
        setError(getErrorMessage(requestError, 'Could not load your listings.'));
        setPosts([]);
      } finally {
        setLoading(false);
      }
    },
    [status],
  );

  useEffect(() => {
    fetchPosts(0);
  }, [fetchPosts]);

  const markSold = async (post) => {
    setSoldLoading(post.id);
    try {
      await postsApi.markSold(post.id);
      toast.success('Congrats on the sale! 🎉');
      fetchPosts(page);
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not mark it as sold.'));
    } finally {
      setSoldLoading(null);
    }
  };

  const deletePost = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await postsApi.remove(deleteTarget.id);
      toast.success('Listing deleted.');
      setDeleteTarget(null);
      fetchPosts(page);
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not delete the listing.'));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="page dashboard-page">
      <div className="container">
        <div className="page-head">
          <div>
            <span className="eyebrow">Your corner of campus</span>
            <h1>My listings</h1>
            <p>Track what’s live, what’s pending and what’s already found a new home.</p>
          </div>
          <Link to="/sell" className="btn btn-primary">
            <Plus size={17} /> New listing
          </Link>
        </div>

        <div className="status-tabs">
          {TABS.map((tab) => (
            <button
              key={tab.value || 'all'}
              type="button"
              className={`status-tab ${status === tab.value ? 'active' : ''}`}
              onClick={() => setStatus(tab.value)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="dashboard-meta">
          <span>{pluralize(totalElements, 'listing')} in this view</span>
          {totalPages > 1 && <span>Page {page + 1} of {totalPages}</span>}
        </div>

        {error && <div className="alert error">{error}</div>}

        {loading ? (
          <ListSkeleton count={4} />
        ) : posts.length === 0 ? (
          <EmptyState
            icon={Package}
            title={status ? 'Nothing in this pile yet' : 'No listings yet'}
            description={
              status
                ? 'Try another filter — or post something new.'
                : 'Your old textbooks, extra lamp or cycle could be someone’s best find.'
            }
            action={<Link to="/sell" className="btn btn-primary"><Plus size={16} /> Post your first listing</Link>}
          />
        ) : (
          <div className="listing-rows">
            {posts.map((post) => {
              const image = firstImageOf(post);
              const editable = !['SOLD', 'DELETED', 'HIDDEN'].includes(post.status);
              return (
                <article className="listing-row" key={post.id}>
                  <Link to={`/posts/${post.id}`} className="listing-thumb">
                    {image ? <img src={image} alt={post.title} /> : <span>🎁</span>}
                  </Link>

                  <div className="listing-main">
                    <div className="listing-title-line">
                      <Link to={`/posts/${post.id}`} className="listing-title">{post.title}</Link>
                      <StatusBadge status={post.status} />
                    </div>
                    <div className="listing-meta">
                      <span>{post.categoryName}</span>
                      <span>·</span>
                      <span>{formatPrice(post.price)}</span>
                      <span>·</span>
                      <span>Posted {formatDate(post.createdAt)}</span>
                    </div>
                    {post.rejectionReason && (
                      <p className="listing-reason">Rejected: {post.rejectionReason}</p>
                    )}
                  </div>

                  <div className="listing-actions">
                    <Link to={`/posts/${post.id}`} className="icon-btn" aria-label="View listing">
                      <Eye size={16} />
                    </Link>
                    {editable && (
                      <Link to={`/posts/${post.id}/edit`} className="icon-btn" aria-label="Edit listing">
                        <Pencil size={16} />
                      </Link>
                    )}
                    {post.status === 'APPROVED' && (
                      <button
                        type="button"
                        className="btn btn-mint btn-sm"
                        onClick={() => markSold(post)}
                        disabled={soldLoading === post.id}
                      >
                        {soldLoading === post.id ? <Loader2 size={14} className="spin" /> : <Handshake size={14} />}
                        Sold
                      </button>
                    )}
                    <button
                      type="button"
                      className="icon-btn danger-icon"
                      onClick={() => setDeleteTarget(post)}
                      aria-label="Delete listing"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {totalPages > 1 && !loading && posts.length > 0 && (
          <div className="pager">
            <button
              type="button"
              className="btn btn-light btn-sm"
              disabled={page === 0}
              onClick={() => fetchPosts(page - 1)}
            >
              Previous
            </button>
            <span>Page {page + 1} of {totalPages}</span>
            <button
              type="button"
              className="btn btn-light btn-sm"
              disabled={page + 1 >= totalPages}
              onClick={() => fetchPosts(page + 1)}
            >
              Next
            </button>
          </div>
        )}
      </div>

      <Modal
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        title="Delete this listing?"
        subtitle={deleteTarget ? `“${deleteTarget.title}” will be removed from your listings.` : ''}
        width={430}
      >
        <div className="modal-actions">
          <button type="button" className="btn btn-light" onClick={() => setDeleteTarget(null)}>Keep it</button>
          <button type="button" className="btn btn-danger" onClick={deletePost} disabled={deleting}>
            {deleting ? <Loader2 size={16} className="spin" /> : <Trash2 size={16} />} Delete
          </button>
        </div>
      </Modal>
    </div>
  );
}
