import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { AlertTriangle, Loader2, Trash2 } from 'lucide-react';
import { adminApi, getErrorMessage } from '../lib/api';
import Modal from './Modal';

export default function AdminPostDeleteModal({ open, post, onClose, onSuccess }) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setReason('');
    setError('');
    setSubmitting(false);
  }, [open, post?.id]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!reason.trim()) {
      setError('A short reason is required so the audit trail makes sense.');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      await adminApi.posts.remove(post.id, { reason: reason.trim() });
      toast.success('Listing deleted and open reports resolved.');
      onSuccess?.();
      onClose?.();
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not delete this listing.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Delete this listing?"
      subtitle="This removes the listing from the marketplace and closes its open reports."
      width={520}
    >
      {error && <div className="alert error">{error}</div>}

      <div className="admin-modal-note" style={{ background: 'var(--red-soft)', color: '#a82735' }}>
        <AlertTriangle size={15} />
        <span>
          You are about to delete <strong>“{post?.title}”</strong> by {post?.sellerName || 'this student'}.
          The action is logged in the audit trail.
        </span>
      </div>

      <form onSubmit={handleSubmit} style={{ marginTop: 16 }}>
        <div className="form-group">
          <label htmlFor="post-delete-reason">Reason</label>
          <textarea
            id="post-delete-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={1000}
            rows={4}
            placeholder="e.g. Prohibited item — selling prescription medication"
          />
          <div className="flex justify-between">
            <span className="field-hint">Visible to the team in the audit log.</span>
            <span className="char-count">{reason.length}/1000</span>
          </div>
        </div>

        <div className="admin-modal-actions">
          <button type="button" className="btn btn-light" onClick={onClose} disabled={submitting}>
            Keep listing
          </button>
          <button type="submit" className="btn btn-danger" disabled={submitting}>
            {submitting ? <Loader2 size={16} className="admin-spin" /> : <Trash2 size={16} />}
            {submitting ? 'Deleting…' : 'Delete listing'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
