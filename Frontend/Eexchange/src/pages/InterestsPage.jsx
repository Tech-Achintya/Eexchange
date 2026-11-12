import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  ArrowUpRight,
  CheckCheck,
  Compass,
  Copy,
  Inbox,
  Loader2,
  MailOpen,
  MessageCircle,
  Send,
} from 'lucide-react';
import { getErrorMessage, interestsApi, savedApi } from '../lib/api';
import { formatDate, initials, timeAgo } from '../lib/format';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import { ListSkeleton } from '../components/Loaders';
import './Dashboard.css';

const PAGE_SIZE = 20;

export default function InterestsPage() {
  const [tab, setTab] = useState('received');
  const [received, setReceived] = useState([]);
  const [sent, setSent] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const [markingId, setMarkingId] = useState(null);
  const [markingAll, setMarkingAll] = useState(false);
  const [savedPosts, setSavedPosts] = useState([]);

  useEffect(() => {
    savedApi.list().then((response) => setSavedPosts(response.data || [])).catch(() => {});
    interestsApi
      .unreadCount()
      .then((response) => setUnreadCount(Number(response.data?.count ?? 0)))
      .catch(() => {});
  }, []);

  const fetchInterests = useCallback(
    async (pageNumber = 0, append = false) => {
      if (append) setLoadingMore(true);
      else setLoading(true);
      setError('');
      try {
        if (tab === 'received') {
          const response = await interestsApi.received({
            page: pageNumber,
            size: PAGE_SIZE,
            unreadOnly,
          });
          const data = response.data || {};
          const content = data.content || [];
          setReceived((previous) => (append ? [...previous, ...content] : content));
          setPage(data.page ?? pageNumber);
          setTotalPages(data.totalPages ?? 0);
          setTotalElements(data.totalElements ?? content.length);
          setUnreadCount(content.filter((item) => !item.isRead).length);
        } else {
          const response = await interestsApi.sent({ page: pageNumber, size: PAGE_SIZE });
          const data = response.data || {};
          const content = data.content || [];
          setSent((previous) => (append ? [...previous, ...content] : content));
          setPage(data.page ?? pageNumber);
          setTotalPages(data.totalPages ?? 0);
          setTotalElements(data.totalElements ?? content.length);
        }
      } catch (requestError) {
        setError(getErrorMessage(requestError, 'Could not load your inbox.'));
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [tab, unreadOnly],
  );

  useEffect(() => {
    fetchInterests(0, false);
  }, [fetchInterests]);

  const markRead = async (interest) => {
    if (interest.isRead) return;
    setMarkingId(interest.id);
    try {
      await interestsApi.markRead(interest.id);
      setReceived((previous) =>
        previous.map((item) => (item.id === interest.id ? { ...item, isRead: true } : item)),
      );
      setUnreadCount((count) => Math.max(0, count - 1));
      window.dispatchEvent(new CustomEvent('eexchange:interests-changed'));
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not mark it as read.'));
    } finally {
      setMarkingId(null);
    }
  };

  const markAllRead = async () => {
    const unread = received.filter((item) => !item.isRead);
    if (!unread.length) return;
    setMarkingAll(true);
    try {
      await Promise.allSettled(unread.map((item) => interestsApi.markRead(item.id)));
      setReceived((previous) => previous.map((item) => ({ ...item, isRead: true })));
      setUnreadCount(0);
      window.dispatchEvent(new CustomEvent('eexchange:interests-changed'));
      toast.success('Inbox cleared — all caught up ✨');
    } catch {
      toast.error('Some messages could not be marked as read.');
    } finally {
      setMarkingAll(false);
    }
  };

  const copyContact = async (contact) => {
    try {
      await navigator.clipboard.writeText(contact);
      toast.success('Contact info copied!');
    } catch {
      toast.error('Could not copy — you can select it manually.');
    }
  };

  const currentItems = tab === 'received' ? received : sent;
  const isSaved = (postId) => savedPosts.map(String).includes(String(postId));

  return (
    <div className="page dashboard-page">
      <div className="container">
        <div className="page-head">
          <div>
            <span className="eyebrow">Your campus conversations</span>
            <h1>Inbox</h1>
            <p>People who want your stuff, and the things you’ve shown interest in.</p>
          </div>
          <Link to="/feed" className="btn btn-light">
            <Compass size={16} /> Find more
          </Link>
        </div>

        <div className="inbox-tabs">
          <button
            type="button"
            className={`inbox-tab ${tab === 'received' ? 'active' : ''}`}
            onClick={() => { setTab('received'); setUnreadOnly(false); }}
          >
            <Inbox size={17} /> Received
            {unreadCount > 0 && <span className="tab-badge">{unreadCount}</span>}
          </button>
          <button
            type="button"
            className={`inbox-tab ${tab === 'sent' ? 'active' : ''}`}
            onClick={() => setTab('sent')}
          >
            <Send size={17} /> Sent
          </button>
        </div>

        <div className="inbox-toolbar">
          <span className="dashboard-meta-inline">
            {loading ? 'Loading…' : `${totalElements} ${tab === 'received' ? 'message' : 'interest'}${totalElements === 1 ? '' : 's'}${tab === 'sent' ? ' sent' : ''}`}
          </span>

          {tab === 'received' && (
            <div className="inbox-actions">
              <label className="toggle-label">
                <input
                  type="checkbox"
                  checked={unreadOnly}
                  onChange={(event) => setUnreadOnly(event.target.checked)}
                />
                <span>Unread only</span>
              </label>
              {received.some((item) => !item.isRead) && (
                <button type="button" className="btn btn-light btn-sm" onClick={markAllRead} disabled={markingAll}>
                  {markingAll ? <Loader2 size={14} className="spin" /> : <CheckCheck size={14} />}
                  Mark all read
                </button>
              )}
            </div>
          )}
        </div>

        {error && <div className="alert error">{error}</div>}

        {loading ? (
          <ListSkeleton count={3} />
        ) : currentItems.length === 0 ? (
          <EmptyState
            icon={tab === 'received' ? MailOpen : MessageCircle}
            title={tab === 'received' ? 'No messages yet' : 'No interests sent yet'}
            description={
              tab === 'received'
                ? 'When someone wants one of your listings, their message will land here.'
                : 'When you find something you like, tap “I’m interested” and it’ll show up here.'
            }
            action={<Link to={tab === 'received' ? '/sell' : '/feed'} className="btn btn-primary">
              {tab === 'received' ? 'Post a listing' : 'Browse the feed'}
            </Link>}
          />
        ) : tab === 'received' ? (
          <div className="interest-list">
            {received.map((interest) => (
              <article className={`interest-card ${interest.isRead ? '' : 'unread'}`} key={interest.id}>
                <div className="interest-top">
                  <span className="interest-avatar">{initials(interest.senderName)}</span>
                  <div className="interest-who">
                    <div className="interest-name-line">
                      <strong>{interest.senderName}</strong>
                      {!interest.isRead && <span className="unread-pill">New</span>}
                    </div>
                    <span className="interest-time">{timeAgo(interest.createdAt)}</span>
                  </div>
                  {!interest.isRead && (
                    <button
                      type="button"
                      className="btn btn-light btn-sm"
                      onClick={() => markRead(interest)}
                      disabled={markingId === interest.id}
                    >
                      {markingId === interest.id ? <Loader2 size={13} className="spin" /> : <CheckCheck size={13} />}
                      Mark read
                    </button>
                  )}
                </div>

                <Link to={`/posts/${interest.postId}`} className="interest-post">
                  {interest.firstImageUrl ? (
                    <img src={interest.firstImageUrl} alt={interest.postTitle} />
                  ) : (
                    <span className="interest-post-placeholder">🎁</span>
                  )}
                  <div>
                    <small>About your listing</small>
                    <strong>{interest.postTitle}</strong>
                  </div>
                  <ArrowUpRight size={16} />
                </Link>

                <blockquote className="interest-message">“{interest.message}”</blockquote>

                <div className="contact-box">
                  <div>
                    <small>Reach them at</small>
                    <strong>{interest.contactInfo}</strong>
                  </div>
                  <button type="button" className="btn btn-light btn-sm" onClick={() => copyContact(interest.contactInfo)}>
                    <Copy size={14} /> Copy
                  </button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="interest-list">
            {sent.map((interest) => (
              <article className="interest-card sent" key={interest.id}>
                <div className="interest-top">
                  <span className="interest-avatar sent-avatar"><Send size={16} /></span>
                  <div className="interest-who">
                    <div className="interest-name-line">
                      <strong>{interest.postTitle}</strong>
                      <StatusBadge status={interest.postStatus} />
                    </div>
                    <span className="interest-time">Sent {timeAgo(interest.createdAt)}</span>
                  </div>
                  <Link to={`/posts/${interest.postId}`} className="btn btn-light btn-sm">
                    View listing <ArrowUpRight size={14} />
                  </Link>
                </div>
                <blockquote className="interest-message">“{interest.message}”</blockquote>
                <div className="interest-footer">
                  <span>{formatDate(interest.createdAt, 'dd MMM yyyy · h:mm a')}</span>
                  {isSaved(interest.postId) && <span className="chip sun">Saved to wishlist</span>}
                </div>
              </article>
            ))}
          </div>
        )}

        {totalPages > 1 && !loading && currentItems.length > 0 && (
          <div className="pager">
            <button
              type="button"
              className="btn btn-light btn-sm"
              disabled={page === 0}
              onClick={() => fetchInterests(page - 1)}
            >
              Previous
            </button>
            <span>Page {page + 1} of {totalPages}</span>
            <button
              type="button"
              className="btn btn-light btn-sm"
              disabled={page + 1 >= totalPages || loadingMore}
              onClick={() => fetchInterests(page + 1)}
            >
              {loadingMore ? <Loader2 size={14} className="spin" /> : 'Next'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
