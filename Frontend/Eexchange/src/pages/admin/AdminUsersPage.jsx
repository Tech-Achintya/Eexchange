import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Ban,
  ChevronRight,
  Filter,
  GraduationCap,
  RotateCcw,
  Search,
  Unlock,
  Users,
} from 'lucide-react';
import { adminApi, catalogApi, getErrorMessage } from '../../lib/api';
import useDebouncedValue from '../../hooks/useDebouncedValue';
import { formatDate, initials, timeAgo } from '../../lib/format';
import { useAdmin } from '../../context/AdminContext';
import { AdminEmpty, AdminPager, AdminTableSkeleton, UserStatusBadge } from '../../components/AdminBits';
import AdminUserActionModal from '../../components/AdminUserActionModal';
import '../../components/Admin.css';

const PAGE_SIZE = 20;

export default function AdminUsersPage() {
  const { isSuperAdmin } = useAdmin();
  const [q, setQ] = useState('');
  const debouncedQ = useDebouncedValue(q, 350);
  const [status, setStatus] = useState('');
  const [collegeId, setCollegeId] = useState('');
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [action, setAction] = useState({ open: false, mode: 'block', user: null });

  useEffect(() => {
    catalogApi
      .colleges()
      .then((response) => setColleges(response.data || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, status, collegeId]);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await adminApi.users.list({
        q: debouncedQ.trim() || undefined,
        status: status || undefined,
        collegeId: collegeId || undefined,
        page,
        size: PAGE_SIZE,
      });
      const data = response.data || {};
      setUsers(data.content || []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load students.'));
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, status, collegeId, page]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers, refreshKey]);

  const resetFilters = () => {
    setQ('');
    setStatus('');
    setCollegeId('');
    setPage(0);
  };

  const openAction = (mode, user) => setAction({ open: true, mode, user });
  const closeAction = () => setAction((previous) => ({ ...previous, open: false }));

  const handleSuccess = () => {
    setRefreshKey((key) => key + 1);
  };

  const hasFilters = Boolean(q || status || collegeId);

  return (
    <div className="admin-page">
      <div className="admin-page-head">
        <div>
          <span className="admin-eyebrow"><Users size={12} /> People</span>
          <h1>Students</h1>
          <p>Find a student, review their activity, and step in only when it helps.</p>
        </div>
        <div className="admin-head-actions">
          <span className="chip mint"><Users size={13} /> {totalElements} accounts</span>
        </div>
      </div>

      <div className="admin-filters">
        <div className="admin-filter grow">
          <label htmlFor="user-search"><Search size={11} /> Search</label>
          <input
            id="user-search"
            type="search"
            placeholder="Name or email…"
            value={q}
            onChange={(event) => setQ(event.target.value)}
          />
        </div>
        <div className="admin-filter">
          <label htmlFor="user-status"><Filter size={11} /> Status</label>
          <select
            id="user-status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="TEMP_BLOCKED">Temporarily blocked</option>
            <option value="PERM_BLOCKED">Permanently blocked</option>
          </select>
        </div>
        <div className="admin-filter">
          <label htmlFor="user-college"><GraduationCap size={11} /> College</label>
          <select
            id="user-college"
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
        ) : users.length === 0 ? (
          <AdminEmpty
            title="No students match"
            description={
              hasFilters
                ? 'Try a different name, status or college.'
                : 'New student accounts will appear here.'
            }
            icon={Users}
          />
        ) : (
          <>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>College</th>
                    <th>Status</th>
                    <th>Listings</th>
                    <th>Joined</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr key={user.id}>
                      <td data-label="Student">
                        <div className="admin-cell-main">
                          <span className="admin-row-avatar">{initials(user.name)}</span>
                          <div className="admin-cell-stack">
                            <Link to={`/admin/users/${user.id}`} className="admin-row-link" style={{ fontSize: '0.84rem', color: 'var(--ink)' }}>
                              {user.name}
                            </Link>
                            <small>{user.email}</small>
                          </div>
                        </div>
                      </td>
                      <td data-label="College">
                        <span className="admin-cell-muted">{user.college?.name || '—'}</span>
                      </td>
                      <td data-label="Status">
                        <div className="admin-cell-stack">
                          <UserStatusBadge status={user.status} />
                          {user.status === 'TEMP_BLOCKED' && user.blockedUntil && (
                            <small>Until {formatDate(user.blockedUntil, 'dd MMM, HH:mm')}</small>
                          )}
                        </div>
                      </td>
                      <td data-label="Listings">
                        <span className="chip">{user.postCount ?? 0}</span>
                      </td>
                      <td data-label="Joined">
                        <div className="admin-cell-stack">
                          <span className="admin-cell-muted">{formatDate(user.createdAt)}</span>
                          <small>Last seen {user.lastLoginAt ? timeAgo(user.lastLoginAt) : 'never'}</small>
                        </div>
                      </td>
                      <td data-label="Actions" style={{ textAlign: 'right' }}>
                        <div className="admin-cell-actions">
                          {user.status === 'ACTIVE' ? (
                            <button
                              type="button"
                              className="admin-icon-action danger"
                              onClick={() => openAction('block', user)}
                              aria-label={`Block ${user.name}`}
                              title="Block student"
                            >
                              <Ban size={15} />
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="admin-icon-action success"
                              onClick={() => openAction('unblock', user)}
                              aria-label={`Unblock ${user.name}`}
                              title="Unblock student"
                            >
                              <Unlock size={15} />
                            </button>
                          )}
                          <Link
                            to={`/admin/users/${user.id}`}
                            className="admin-icon-action"
                            aria-label={`View ${user.name}`}
                            title="View student"
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
              unit="student"
            />
          </>
        )}
      </div>

      <AdminUserActionModal
        open={action.open}
        mode={action.mode}
        user={action.user}
        isSuperAdmin={isSuperAdmin}
        onClose={closeAction}
        onSuccess={handleSuccess}
      />
    </div>
  );
}
