import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Check, Loader2, RefreshCw, ShieldAlert, X } from 'lucide-react';
import { adminApi, getErrorMessage } from '../lib/api';
import Modal from './Modal';

const COPY = {
  approve: {
    title: 'Approve this listing?',
    subtitle: 'It will go live in the campus marketplace right away.',
    label: 'Approval note (optional)',
    placeholder: 'e.g. Checked the photos and description — looks genuine',
    hint: 'Kept in the audit trail for the team.',
    submit: 'Approve listing',
    pending: 'Approving…',
    icon: Check,
    tone: 'btn btn-mint',
    callout: {
      background: 'var(--green-soft)',
      color: '#0c5f39',
    },
  },
  reject: {
    title: 'Reject this listing?',
    subtitle: 'The seller will be told why so they can fix and repost it.',
    label: 'Reason for the seller',
    placeholder: 'e.g. Prohibited item — vapes are not allowed on campus',
    hint: 'Between 5 and 300 characters. Shown to the seller.',
    submit: 'Reject listing',
    pending: 'Rejecting…',
    icon: X,
    tone: 'btn btn-danger',
    callout: {
      background: 'var(--red-soft)',
      color: '#a82735',
    },
  },
  requeue: {
    title: 'Send this listing back to AI review?',
    subtitle: 'The listing leaves the human queue and the AI moderation pass runs again.',
    label: 'Requeue note (optional)',
    placeholder: 'e.g. Photos were replaced — please re-check them',
    hint: 'Kept in the audit trail for the team.',
    submit: 'Requeue for AI review',
    pending: 'Requeuing…',
    icon: RefreshCw,
    tone: 'btn btn-light',
    callout: {
      background: 'var(--sky-soft)',
      color: '#23589f',
    },
  },
};

export default function AdminModerationModal({ open, action, item, onClose, onSuccess }) {
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setNote('');
    setError('');
    setSubmitting(false);
  }, [open, item?.id, action]);

  const copy = COPY[action] || COPY.approve;
  const Icon = copy.icon;
  const subtitle = item?.queueReason === 'RETRY_LATER'
    ? 'The AI asked to retry this listing later. Your decision overrides the automated review.'
    : copy.subtitle;

  const handleSubmit = async (event) => {
    event.preventDefault();
    const trimmed = note.trim();

    if (action === 'reject' && (trimmed.length < 5 || trimmed.length > 300)) {
      setError('Give the seller a reason between 5 and 300 characters.');
      return;
    }
    if (trimmed.length > 300) {
      setError('Keep the note under 300 characters.');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      if (action === 'reject') {
        await adminApi.moderation.reject(item.id, { reason: trimmed });
      } else if (action === 'requeue') {
        await adminApi.moderation.requeue(item.id, trimmed ? { note: trimmed } : {});
      } else {
        await adminApi.moderation.approve(item.id, trimmed ? { note: trimmed } : {});
      }

      toast.success(
        action === 'reject'
          ? 'Listing rejected and the seller notified.'
          : action === 'requeue'
            ? 'Listing sent back to AI review.'
            : 'Listing approved and live.',
      );
      onSuccess?.();
      onClose?.();
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not update this listing.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={copy.title} subtitle={subtitle} width={540}>
      {error && <div className="alert error">{error}</div>}

      <div className="admin-modal-note" style={copy.callout}>
        <ShieldAlert size={15} />
        <span>
          <strong>“{item?.title}”</strong> by {item?.sellerName || 'a student'}
          {item?.collegeName ? ` · ${item.collegeName}` : ''}
        </span>
      </div>

      <form onSubmit={handleSubmit} style={{ marginTop: 16 }}>
        <div className="form-group">
          <label htmlFor="moderation-note">{copy.label}</label>
          <textarea
            id="moderation-note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={300}
            rows={4}
            placeholder={copy.placeholder}
            autoFocus
          />
          <div className="flex justify-between">
            <span className="field-hint">{copy.hint}</span>
            <span className="char-count">{note.length}/300</span>
          </div>
        </div>

        <div className="admin-modal-actions">
          <button type="button" className="btn btn-light" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className={copy.tone} disabled={submitting || (action === 'reject' && !note.trim())}>
            {submitting ? <Loader2 size={16} className="admin-spin" /> : <Icon size={16} />}
            {submitting ? copy.pending : copy.submit}
          </button>
        </div>
      </form>
    </Modal>
  );
}
