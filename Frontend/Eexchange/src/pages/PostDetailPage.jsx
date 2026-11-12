import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  AlertTriangle,
  ArrowLeft,
  BadgeCheck,
  Bookmark,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Flag,
  Handshake,
  HeartHandshake,
  Loader2,
  MessageCircle,
  Pencil,
  Send,
  Share2,
  ShieldCheck,
  ShoppingBag,
  Trash2,
  XCircle,
} from 'lucide-react';
import { getErrorMessage, postsApi, savedApi } from '../lib/api';
import { CONDITION_LABELS, REPORT_REASONS } from '../lib/constants';
import { firstImageOf, formatDate, formatPrice, initials, timeAgo } from '../lib/format';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import EmptyState from '../components/EmptyState';
import { PageLoader } from '../components/Loaders';
import './PostDetailPage.css';

export default function PostDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeImage, setActiveImage] = useState(0);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const [interestOpen, setInterestOpen] = useState(false);
  const [interestForm, setInterestForm] = useState({ message: '', contactInfo: '' });
  const [interestError, setInterestError] = useState('');
  const [interestLoading, setInterestLoading] = useState(false);
  const [interestSent, setInterestSent] = useState(false);

  const [reportOpen, setReportOpen] = useState(false);
  const [reportForm, setReportForm] = useState({ reason: 'SCAM', comment: '' });
  const [reportError, setReportError] = useState('');
  const [reportLoading, setReportLoading] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    postsApi
      .detail(id)
      .then((response) => {
        if (!active) return;
        setPost(response.data);
        setInterestSent(Boolean(response.data?.interested));
        setInterestForm((previous) => ({
          ...previous,
          contactInfo: previous.contactInfo || user?.email || '',
        }));
      })
      .catch((requestError) => {
        if (active) setError(getErrorMessage(requestError, 'Could not load this listing.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    savedApi
      .list()
      .then((response) => {
        if (active) setSaved((response.data || []).map(String).includes(String(id)));
      })
      .catch(() => {});

    return () => { active = false; };
  }, [id, user?.email]);

  const images = post?.imageUrls?.length ? post.imageUrls : [firstImageOf(post)].filter(Boolean);
  const isOwner = Boolean(user && post && String(post.sellerId) === String(user.id));
  const canInterest = !isOwner && post?.status !== 'SOLD' && !interestSent && !post?.interested;

  const toggleSave = async () => {
    if (!post || saving) return;
    setSaving(true);
    try {
      const response = await savedApi.toggle(post.id);
      const nextSaved = Boolean(response.data?.saved);
      setSaved(nextSaved);
      toast.success(nextSaved ? 'Saved to your wishlist 💛' : 'Removed from wishlist');
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not update your wishlist.'));
    } finally {
      setSaving(false);
    }
  };

  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast.success('Link copied — send it to a friend!');
    } catch {
      toast.error('Could not copy the link.');
    }
  };

  const submitInterest = async (event) => {
    event.preventDefault();
    setInterestError('');
    if (interestForm.message.trim().length < 5) {
      setInterestError('Add a short message — at least 5 characters.');
      return;
    }
    if (interestForm.contactInfo.trim().length < 5) {
      setInterestError('Share some contact info so the seller can reach you.');
      return;
    }
    setInterestLoading(true);
    try {
      await postsApi.sendInterest(post.id, {
        message: interestForm.message.trim(),
        contactInfo: interestForm.contactInfo.trim(),
      });
      setInterestSent(true);
      setInterestOpen(false);
      toast.success('Interest sent! The seller can now reach you 🎉');
    } catch (requestError) {
      setInterestError(getErrorMessage(requestError, 'Could not send your interest.'));
    } finally {
      setInterestLoading(false);
    }
  };

  const submitReport = async (event) => {
    event.preventDefault();
    setReportError('');
    if (reportForm.comment.length > 500) {
      setReportError('Keep the note under 500 characters.');
      return;
    }
    setReportLoading(true);
    try {
      await postsApi.report(post.id, {
        reason: reportForm.reason,
        comment: reportForm.comment.trim() || null,
      });
      setReportOpen(false);
      setReportForm({ reason: 'SCAM', comment: '' });
      toast.success('Thanks — our team will take a look.');
    } catch (requestError) {
      setReportError(getErrorMessage(requestError, 'Could not submit the report.'));
    } finally {
      setReportLoading(false);
    }
  };

  const markSold = async () => {
    setActionLoading(true);
    try {
      const response = await postsApi.markSold(post.id);
      setPost((previous) => ({ ...previous, ...response.data, status: 'SOLD' }));
      toast.success('Marked as sold. Nice one! 🎉');
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not mark this as sold.'));
    } finally {
      setActionLoading(false);
    }
  };

  const deletePost = async () => {
    setActionLoading(true);
    try {
      await postsApi.remove(post.id);
      toast.success('Listing deleted.');
      navigate('/my-posts', { replace: true });
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not delete this listing.'));
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <div className="container">
          <PageLoader label="Opening this find…" />
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="page">
        <div className="container">
          <EmptyState
            icon={ShoppingBag}
            title="This find isn’t here anymore"
            description={error || 'It may have been sold, hidden or removed.'}
            action={<Link to="/feed" className="btn btn-primary">Back to the feed</Link>}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="page post-detail-page">
      <div className="container">
        <button type="button" className="back-link detail-back" onClick={() => navigate(-1)}>
          <ArrowLeft size={16} /> Back
        </button>

        {isOwner && post.status !== 'APPROVED' && post.status !== 'SOLD' && (
          <div className={`owner-banner banner-${post.status === 'REJECTED' ? 'red' : 'sun'}`}>
            <div>
              <strong>
                {post.status === 'PENDING_REVIEW' && 'This listing is being reviewed.'}
                {post.status === 'REJECTED' && 'This listing was not approved.'}
                {post.status === 'HIDDEN' && 'This listing is currently hidden.'}
                {post.status === 'MANUAL_REVIEW' && 'A human is taking a closer look.'}
              </strong>
              {post.rejectionReason && <p>Reason: {post.rejectionReason}</p>}
              {post.status === 'PENDING_REVIEW' && (
                <p>Most listings clear review quickly. We’ll notify you when it’s live.</p>
              )}
            </div>
            <StatusBadge status={post.status} />
          </div>
        )}

        <div className="detail-grid">
          <motion.div
            className="detail-gallery"
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4 }}
          >
            <div className="gallery-main">
              {images.length ? (
                <img src={images[activeImage]} alt={`${post.title} — photo ${activeImage + 1}`} />
              ) : (
                <div className="gallery-placeholder">🎁</div>
              )}

              {post.status === 'SOLD' && <span className="gallery-sold">Sold</span>}

              {images.length > 1 && (
                <>
                  <button
                    type="button"
                    className="gallery-arrow left"
                    onClick={() => setActiveImage((index) => (index - 1 + images.length) % images.length)}
                    aria-label="Previous photo"
                  >
                    <ChevronLeft size={20} />
                  </button>
                  <button
                    type="button"
                    className="gallery-arrow right"
                    onClick={() => setActiveImage((index) => (index + 1) % images.length)}
                    aria-label="Next photo"
                  >
                    <ChevronRight size={20} />
                  </button>
                  <span className="gallery-count">{activeImage + 1} / {images.length}</span>
                </>
              )}
            </div>

            {images.length > 1 && (
              <div className="gallery-thumbs">
                {images.map((image, index) => (
                  <button
                    key={`${image}-${index}`}
                    type="button"
                    className={`gallery-thumb ${index === activeImage ? 'active' : ''}`}
                    onClick={() => setActiveImage(index)}
                    aria-label={`Show photo ${index + 1}`}
                  >
                    <img src={image} alt="" />
                  </button>
                ))}
              </div>
            )}
          </motion.div>

          <motion.aside
            className="detail-side"
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.08 }}
          >
            <div className="detail-title-row">
              <span className="chip lav">{post.categoryName}</span>
              <StatusBadge status={post.status} />
            </div>

            <h1>{post.title}</h1>

            <div className="detail-price-row">
              <span className="detail-price">{formatPrice(post.price)}</span>
              <span className="chip sun">{CONDITION_LABELS[post.condition] || post.condition}</span>
            </div>

            <div className="detail-meta-line">
              <span><CalendarDays size={14} /> Posted {timeAgo(post.createdAt)}</span>
              {post.expiresAt && <span>· Expires {formatDate(post.expiresAt)}</span>}
            </div>

            <div className="detail-actions">
              {canInterest && (
                <button type="button" className="btn btn-primary btn-block btn-lg" onClick={() => setInterestOpen(true)}>
                  <HeartHandshake size={19} /> I’m interested
                </button>
              )}
              {interestSent && !isOwner && (
                <button type="button" className="btn btn-mint btn-block btn-lg" disabled>
                  <BadgeCheck size={19} /> Interest sent
                </button>
              )}
              {isOwner && (
                <>
                  <div className="owner-actions">
                    <Link to={`/posts/${post.id}/edit`} className="btn btn-dark">
                      <Pencil size={16} /> Edit listing
                    </Link>
                    {post.status === 'APPROVED' && (
                      <button type="button" className="btn btn-mint" onClick={markSold} disabled={actionLoading}>
                        {actionLoading ? <Loader2 size={16} className="spin" /> : <Handshake size={16} />}
                        Mark sold
                      </button>
                    )}
                    <button type="button" className="btn btn-danger" onClick={() => setDeleteOpen(true)}>
                      <Trash2 size={16} /> Delete
                    </button>
                  </div>
                  {post.status === 'SOLD' && (
                    <p className="owner-note"><BadgeCheck size={15} /> This listing is marked as sold.</p>
                  )}
                </>
              )}
            </div>

            <div className="detail-secondary-actions">
              <button type="button" className={`btn btn-light ${saved ? 'saved-action' : ''}`} onClick={toggleSave} disabled={saving}>
                <Bookmark size={16} fill={saved ? 'currentColor' : 'none'} />
                {saved ? 'Saved' : 'Save'}
              </button>
              <button type="button" className="btn btn-light" onClick={share}>
                <Share2 size={16} /> Share
              </button>
              {!isOwner && (
                <button type="button" className="btn btn-ghost" onClick={() => setReportOpen(true)}>
                  <Flag size={15} /> Report
                </button>
              )}
            </div>

            <div className="detail-divider" />

            <section className="detail-section">
              <h2>About this find</h2>
              <p className="detail-description">{post.description}</p>
            </section>

            <div className="detail-divider" />

            <section className="seller-card">
              <span className="seller-avatar">{initials(post.sellerName)}</span>
              <div className="seller-copy">
                <strong>{post.sellerName}</strong>
                <small><ShieldCheck size={13} /> Verified campus student</small>
              </div>
              <span className="chip green"><span className="status-dot" /> Active</span>
            </section>

            <div className="safety-note">
              <ShieldCheck size={18} />
              <div>
                <strong>Trade smart on campus</strong>
                <p>Meet in a busy campus spot, inspect the item first, and keep chats inside your college community.</p>
              </div>
            </div>
          </motion.aside>
        </div>
      </div>

      {/* Interest modal */}
      <Modal
        open={interestOpen}
        onClose={() => setInterestOpen(false)}
        title="Say hello 👋"
        subtitle="Send a short note and your contact info. The seller will see it in their inbox."
      >
        {interestError && <div className="alert error">{interestError}</div>}
        <form onSubmit={submitInterest} noValidate>
          <div className="form-group">
            <label htmlFor="interest-message">Your message</label>
            <textarea
              id="interest-message"
              rows={4}
              maxLength={1000}
              placeholder={`Hi! Is the ${post.title.toLowerCase()} still available? I can pick it up on campus.`}
              value={interestForm.message}
              onChange={(event) => setInterestForm({ ...interestForm, message: event.target.value })}
              required
            />
            <span className="char-count">{interestForm.message.length}/1000</span>
          </div>
          <div className="form-group">
            <label htmlFor="interest-contact">Contact info</label>
            <input
              id="interest-contact"
              maxLength={255}
              placeholder="Email, phone or Instagram — whatever you check"
              value={interestForm.contactInfo}
              onChange={(event) => setInterestForm({ ...interestForm, contactInfo: event.target.value })}
              required
            />
            <span className="field-hint">Only the seller sees this.</span>
          </div>
          <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={interestLoading}>
            {interestLoading ? <span className="spinner" /> : <><Send size={17} /> Send interest</>}
          </button>
        </form>
      </Modal>

      {/* Report modal */}
      <Modal
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        title="Report this listing"
        subtitle="Tell us what feels off. Reports stay private."
        width={480}
      >
        {reportError && <div className="alert error">{reportError}</div>}
        <form onSubmit={submitReport}>
          <div className="report-reasons">
            {REPORT_REASONS.map((reason) => (
              <label key={reason.value} className={`report-reason ${reportForm.reason === reason.value ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="reason"
                  value={reason.value}
                  checked={reportForm.reason === reason.value}
                  onChange={(event) => setReportForm({ ...reportForm, reason: event.target.value })}
                />
                {reason.label}
              </label>
            ))}
          </div>
          <div className="form-group" style={{ marginTop: 16 }}>
            <label htmlFor="report-comment">Anything else? (optional)</label>
            <textarea
              id="report-comment"
              rows={3}
              maxLength={500}
              placeholder="Add a little detail to help our team."
              value={reportForm.comment}
              onChange={(event) => setReportForm({ ...reportForm, comment: event.target.value })}
            />
            <span className="char-count">{reportForm.comment.length}/500</span>
          </div>
          <button type="submit" className="btn btn-danger btn-block" disabled={reportLoading}>
            {reportLoading ? <span className="spinner" /> : <><AlertTriangle size={16} /> Submit report</>}
          </button>
        </form>
      </Modal>

      {/* Delete modal */}
      <Modal
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        title="Delete this listing?"
        subtitle="This can’t be undone. The listing and its photos will be removed."
        width={440}
      >
        <div className="delete-warning">
          <XCircle size={20} />
          <span>Any interested students will no longer be able to see it.</span>
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-light" onClick={() => setDeleteOpen(false)}>
            Keep it
          </button>
          <button type="button" className="btn btn-danger" onClick={deletePost} disabled={actionLoading}>
            {actionLoading ? <Loader2 size={16} className="spin" /> : <Trash2 size={16} />}
            Yes, delete
          </button>
        </div>
      </Modal>
    </div>
  );
}
