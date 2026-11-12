import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { AlertTriangle, Ban, CalendarClock, Loader2, Unlock } from 'lucide-react';
import { adminApi, getErrorMessage } from '../lib/api';
import Modal from './Modal';

function defaultUntil() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export default function AdminUserActionModal({
  open,
  mode = 'block',
  user,
  isSuperAdmin,
  onClose,
  onSuccess,
}) {
  const [type, setType] = useState('TEMP');
  const [until, setUntil] = useState(defaultUntil);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setType('TEMP');
    setUntil(defaultUntil());
    setReason('');
    setError('');
    setSubmitting(false);
  }, [open, user?.id, mode]);

  const isBlock = mode === 'block';

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');

    if (reason.trim().length < 5) {
      setError('Please add a reason of at least 5 characters.');
      return;
    }

    if (isBlock && type === 'TEMP') {
      const untilDate = new Date(until);
      if (!until || Number.isNaN(untilDate.getTime()) || untilDate <= new Date()) {
        setError('Choose a future date and time for a temporary block.');
        return;
      }
    }

    setSubmitting(true);
    try {
      if (isBlock) {
        await adminApi.users.block(user.id, {
          type,
          until: type === 'TEMP' ? new Date(until).toISOString() : null,
          reason: reason.trim(),
        });
        toast.success(
          type === 'TEMP'
            ? `${user.name} has been temporarily blocked.`
            : `${user.name} has been permanently blocked.`,
        );
      } else {
        await adminApi.users.unblock(user.id, { reason: reason.trim() });
        toast.success(`${user.name} can use EExchange again.`);
      }
      onSuccess?.();
      onClose?.();
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not update this account.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isBlock ? `Block ${user?.name || 'student'}?` : `Unblock ${user?.name || 'student'}?`}
      subtitle={
        isBlock
          ? 'Choose how long the block lasts and leave a clear, factual reason.'
          : 'The student will be able to log in and restore their listings.'
      }
      width={540}
    >
      {error && <div className="alert error">{error}</div>}

      <form onSubmit={handleSubmit}>
        {isBlock ? (
          <div className="admin-form-grid">
            <div className="form-group">
              <label>Block length</label>
              <div className="admin-block-toggle">
                <button
                  type="button"
                  className={type === 'TEMP' ? 'active' : ''}
                  onClick={() => setType('TEMP')}
                >
                  <CalendarClock size={15} /> Temporary
                </button>
                <button
                  type="button"
                  className={type === 'PERM' ? 'active' : ''}
                  onClick={() => setType('PERM')}
                  disabled={!isSuperAdmin}
                  title={!isSuperAdmin ? 'Only super admins can permanently block' : undefined}
                >
                  <Ban size={15} /> Permanent
                </button>
              </div>
              {!isSuperAdmin && (
                <span className="field-hint">Permanent blocks are reserved for super admins.</span>
              )}
            </div>

            {type === 'TEMP' && (
              <div className="form-group">
                <label htmlFor="block-until">Blocked until</label>
                <input
                  id="block-until"
                  type="datetime-local"
                  value={until}
                  onChange={(event) => setUntil(event.target.value)}
                />
                <span className="field-hint">
                  Temporary blocks can be up to 90 days. The account is restored automatically
                  when this time passes.
                </span>
              </div>
            )}
          </div>
        ) : (
          <div className="admin-modal-note">
            <AlertTriangle size={15} />
            <span>Unblocking restores the student and any listings that were hidden by the block.</span>
          </div>
        )}

        <div className="form-group" style={{ marginTop: 16 }}>
          <label htmlFor="admin-reason">Reason</label>
          <textarea
            id="admin-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={500}
            rows={4}
            placeholder={
              isBlock
                ? 'e.g. Repeated abusive messages to other students'
                : 'e.g. Appeal accepted after reviewing the conversation'
            }
          />
          <div className="flex justify-between">
            <span className="field-hint">Stored in the audit trail. Keep it factual.</span>
            <span className="char-count">{reason.length}/500</span>
          </div>
        </div>

        <div className="admin-modal-actions">
          <button type="button" className="btn btn-light" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button
            type="submit"
            className={isBlock ? 'btn btn-danger' : 'btn btn-mint'}
            disabled={submitting}
          >
            {submitting ? <Loader2 size={16} className="admin-spin" /> : isBlock ? <Ban size={16} /> : <Unlock size={16} />}
            {submitting
              ? 'Saving…'
              : isBlock
                ? type === 'TEMP'
                  ? 'Temporarily block'
                  : 'Permanently block'
                : 'Unblock student'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
