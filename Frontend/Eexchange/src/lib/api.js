import axios from 'axios';

export const API_BASE_URL =
  (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 45000,
  headers: { Accept: 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    // Never bounce the user out while they are actively signing in.
    if (status === 401 && localStorage.getItem('token')) {
      window.dispatchEvent(new CustomEvent('eexchange:unauthorized'));
    }
    return Promise.reject(error);
  },
);

/** Every backend endpoint returns ApiResponse<T> = { success, message, data, timestamp }. */
const unwrap = (promise) => promise.then((response) => response.data);

// ---------------------------------------------------------------- auth
export const authApi = {
  signup: (payload) => unwrap(api.post('/api/auth/signup', payload)),
  verifyOtp: (payload) => unwrap(api.post('/api/auth/verify-otp', payload)),
  resendOtp: (payload) => unwrap(api.post('/api/auth/resend-otp', payload)),
  login: (payload) => unwrap(api.post('/api/auth/login', payload)),
  forgotPassword: (payload) => unwrap(api.post('/api/auth/forgot-password', payload)),
  validateResetToken: (token) =>
    unwrap(api.get('/api/auth/validate-reset-token', { params: { token } })),
  resetPassword: (payload) => unwrap(api.post('/api/auth/reset-password', payload)),
};

// ---------------------------------------------------------------- users
export const usersApi = {
  me: () => unwrap(api.get('/api/users/me')),
  updateMe: (payload) => unwrap(api.patch('/api/users/me', payload)),
  changePassword: (payload) => unwrap(api.post('/api/users/me/change-password', payload)),
  deleteMe: (payload) => unwrap(api.delete('/api/users/me', { data: payload })),
};

// ---------------------------------------------------------------- catalog
export const catalogApi = {
  categories: () => unwrap(api.get('/api/categories')),
  colleges: () => unwrap(api.get('/api/colleges')),
};

// ---------------------------------------------------------------- posts
export const postsApi = {
  feed: (params) => unwrap(api.get('/api/posts', { params })),
  detail: (id) => unwrap(api.get(`/api/posts/${id}`)),
  mine: (params) => unwrap(api.get('/api/posts/mine', { params })),
  create: (formData) =>
    unwrap(api.post('/api/posts', formData, { timeout: 120000 })),
  update: (id, formData) =>
    unwrap(api.patch(`/api/posts/${id}`, formData, { timeout: 120000 })),
  markSold: (id) => unwrap(api.post(`/api/posts/${id}/sold`)),
  remove: (id) => unwrap(api.delete(`/api/posts/${id}`)),
  sendInterest: (id, payload) => unwrap(api.post(`/api/posts/${id}/interest`, payload)),
  report: (id, payload) => unwrap(api.post(`/api/posts/${id}/report`, payload)),
};

// ---------------------------------------------------------------- saved posts
export const savedApi = {
  list: () => unwrap(api.get('/api/saved-posts')),
  toggle: (postId) => unwrap(api.post(`/api/saved-posts/toggle/${postId}`)),
};

// ---------------------------------------------------------------- interests
export const interestsApi = {
  received: (params) => unwrap(api.get('/api/interests/received', { params })),
  sent: (params) => unwrap(api.get('/api/interests/sent', { params })),
  markRead: (id) => unwrap(api.patch(`/api/interests/${id}/read`)),
  unreadCount: () => unwrap(api.get('/api/interests/unread-count')),
};

// ---------------------------------------------------------------- admin
// Admin sessions are completely separate from student sessions: different
// localStorage keys, different interceptor and different revoke event.
const adminHttp = axios.create({
  baseURL: API_BASE_URL,
  timeout: 45000,
  headers: { Accept: 'application/json' },
});

adminHttp.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

adminHttp.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401 && localStorage.getItem('admin_token')) {
      window.dispatchEvent(new CustomEvent('eexchange:admin-unauthorized'));
    }
    return Promise.reject(error);
  },
);

const unwrapAdmin = (promise) => promise.then((response) => response.data);

export const adminApi = {
  login: (payload) => unwrapAdmin(adminHttp.post('/api/admin/auth/login', payload)),

  users: {
    list: (params) => unwrapAdmin(adminHttp.get('/api/admin/users', { params })),
    detail: (id) => unwrapAdmin(adminHttp.get(`/api/admin/users/${id}`)),
    block: (id, payload) => unwrapAdmin(adminHttp.post(`/api/admin/users/${id}/block`, payload)),
    unblock: (id, payload) =>
      unwrapAdmin(adminHttp.post(`/api/admin/users/${id}/unblock`, payload)),
  },

  posts: {
    list: (params) => unwrapAdmin(adminHttp.get('/api/admin/posts', { params })),
    detail: (id) => unwrapAdmin(adminHttp.get(`/api/admin/posts/${id}`)),
    remove: (id, payload) =>
      unwrapAdmin(adminHttp.delete(`/api/admin/posts/${id}`, { data: payload })),
  },

  moderation: {
    queue: (params) => unwrapAdmin(adminHttp.get('/api/admin/moderation/queue', { params })),
    approve: (postId, payload) =>
      unwrapAdmin(adminHttp.post(`/api/admin/moderation/${postId}/approve`, payload)),
    reject: (postId, payload) =>
      unwrapAdmin(adminHttp.post(`/api/admin/moderation/${postId}/reject`, payload)),
    requeue: (postId, payload) =>
      unwrapAdmin(adminHttp.post(`/api/admin/moderation/${postId}/requeue`, payload)),
  },

  reports: {
    list: (params) => unwrapAdmin(adminHttp.get('/api/admin/reports', { params })),
    act: (id, payload) => unwrapAdmin(adminHttp.patch(`/api/admin/reports/${id}`, payload)),
  },

  audit: {
    list: (params) => unwrapAdmin(adminHttp.get('/api/admin/audit-log', { params })),
  },
};

// ---------------------------------------------------------------- errors
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const response = error?.response;
  if (!response) {
    if (error?.code === 'ECONNABORTED') return 'The request took too long. Please try again.';
    return 'Cannot reach the server. Is the backend running?';
  }

  const payload = response.data;
  const validation = payload?.data && typeof payload.data === 'object' ? payload.data : null;

  if (validation && !Array.isArray(validation)) {
    const first = Object.values(validation)[0];
    if (typeof first === 'string' && first.trim()) return first;
  }

  if (payload?.message) return payload.message;

  if (response.status === 401) return 'Please log in to continue.';
  if (response.status === 403) return 'You do not have permission to do that.';
  if (response.status === 404) return 'We could not find what you were looking for.';
  if (response.status === 413) return 'That upload is too large. Max 5 MB per image.';
  if (response.status === 429) return 'Slow down a little — too many requests. Try again shortly.';
  if (response.status >= 500) return 'The server had a hiccup. Please try again in a moment.';

  return fallback;
}

export default api;
