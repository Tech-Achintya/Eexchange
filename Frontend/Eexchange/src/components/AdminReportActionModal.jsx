import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { AlertTriangle, Check, Loader2, Trash2, X } from 'lucide-react';
import { adminApi, getErrorMessage } from '../lib/api';
import { REPORT_REASON_LABELS } from './AdminBits';
import Modal from './Modal';

export default function AdminReportActionModal({ open, action, report, onClose, onSuccess }) {
  const [note, setNote] = useState('');
  const [deletePost, setDeletePost] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setNote('');
    setDeletePost(false);
    setError('');
    setSubmitting(false);
  }, [open, report?.id, action]);

  const isResolve = action === 'RESOLVE';
  const postDeleted = report?.postStatus === 'DELETED';

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await adminApi.reports.act(report.id, {
        action: isResolve ? 'RESOLVE' : 'DISMISS',
        note: note.trim() || null,
        ...(isResolve ? { deletePost: deletePost && !postDeleted } : {}),
      });
      toast.success(
        isResolve
          ? deletePost && !postDeleted
            ? 'Report resolved and listing deleted.'
            : 'Report resolved.'
          : 'Report dismissed.',
      );
      onSuccess?.();
      onClose?.();
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not update this report.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isResolve ? 'Resolve this report?' : 'Dismiss this report?'}
      subtitle={
        isResolve
          ? 'Use this when the report was valid and you took action.'
          : 'Use this when the report was not a real problem.'
      }
      width={550}
    >
      {error && <div className="alert error">{error}</div>}

      <div className="admin-callout info" style={{ marginBottom: 18 }}>
        <AlertTriangle size={15} />
        <div>
          <strong>{REPORT_REASON_LABELS[report?.reason] || report?.reason}</strong>
          <div style={{ marginTop: 3 }}>
            On “{report?.postTitle}” · reported by {report?.reporterName || 'a student'}
          </div>
          {report?.comment && (
            <div style={{ marginTop: 6, fontStyle: 'italic' }}>“{report.comment}”</div>
          )}
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="report-note">Internal note (optional)</label>
          <textarea
            id="report-note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={1000}
            rows={3}
            placeholder={
              isResolve
                ? 'e.g. Confirmed scam attempt — listing removed'
                : 'e.g. Item is allowed; report was a misunderstanding'
            }
          />
          <div className="flex justify-between">
            <span className="field-hint">Only the admin team sees this note.</span>
            <span className="char-count">{note.length}/1000</span>
          </div>
        </div>

        {isResolve && (
          <label
            className="toggle-label"
            style={{
              padding: '12px 14px',
              border: '1px solid var(--line)',
              borderRadius: 12,
              marginBottom: 4,
              background: postDeleted ? 'var(--cream)' : '#fff',
              opacity: postDeleted ? 0.6 : 1,
            }}
          >
            <input
              type="checkbox"
              checked={deletePost || postDeleted}
              onChange={(event) => setDeletePost(event.target.checked)}
              disabled={postDeleted}
            />
            <Trash2 size={14} /> Also delete the listing
            {postDeleted && <span className="field-hint">(already deleted)</span>}
          </label>
        )}

        <div className="admin-modal-actions">
          <button type="button" className="btn btn-light" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button
            type="submit"
            className={isResolve ? 'btn btn-mint' : 'btn btn-light'}
            disabled={submitting}
          >
            {submitting ? (
              <Loader2 size={16} className="admin-spin" />
            ) : isResolve ? (
              <Check size={16} />
            ) : (
              <X size={16} />
            )}
            {submitting ? 'Saving…' : isResolve ? 'Resolve report' : 'Dismiss report'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
