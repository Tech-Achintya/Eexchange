import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AdminProvider, useAdmin } from './context/AdminContext';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import AdminProtectedRoute from './components/AdminProtectedRoute';
import AdminLayout from './components/AdminLayout';
import ScrollToTop from './components/ScrollToTop';
import LandingPage from './pages/LandingPage';
import DashboardHomePage from './pages/DashboardHomePage';
import FeedPage from './pages/FeedPage';
import PostDetailPage from './pages/PostDetailPage';
import PostFormPage from './pages/PostFormPage';
import MyPostsPage from './pages/MyPostsPage';
import SavedPage from './pages/SavedPage';
import InterestsPage from './pages/InterestsPage';
import ProfilePage from './pages/ProfilePage';
import AuthPage from './pages/AuthPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import NotFoundPage from './pages/NotFoundPage';
import AdminLoginPage from './pages/admin/AdminLoginPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminSuperDashboardPage from './pages/admin/AdminSuperDashboardPage';
import AdminModerationPage from './pages/admin/AdminModerationPage';
import AdminUsersPage from './pages/admin/AdminUsersPage';
import AdminUserDetailPage from './pages/admin/AdminUserDetailPage';
import AdminPostsPage from './pages/admin/AdminPostsPage';
import AdminPostDetailPage from './pages/admin/AdminPostDetailPage';
import AdminReportsPage from './pages/admin/AdminReportsPage';
import AdminAuditPage from './pages/admin/AdminAuditPage';

function HomeRoute() {
  const { user } = useAuth();
  return user ? <Navigate to="/dashboard" replace /> : <LandingPage />;
}

function AdminHomeRoute() {
  const { isSuperAdmin } = useAdmin();
  return isSuperAdmin ? <AdminSuperDashboardPage /> : <AdminDashboardPage />;
}

export default function App() {
  return (
    <AdminProvider>
      <AuthProvider>
        <BrowserRouter>
          <ScrollToTop />
          <Toaster
            position="top-center"
            toastOptions={{
              duration: 3400,
              style: {
                background: '#ffffff',
                color: '#241f3d',
                borderRadius: '16px',
                border: '1px solid #f0e5d8',
                boxShadow: '0 18px 40px rgba(84, 60, 32, 0.14)',
                fontFamily: "'DM Sans', sans-serif",
                fontWeight: 600,
                fontSize: '0.9rem',
                padding: '12px 16px',
              },
              success: { iconTheme: { primary: '#1fb96b', secondary: '#def7e9' } },
              error: { iconTheme: { primary: '#ea4c5c', secondary: '#ffe5e7' } },
            }}
          />

          <Routes>
            <Route path="/admin/login" element={<AdminLoginPage />} />
            <Route element={<AdminProtectedRoute />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<AdminHomeRoute />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="users/:id" element={<AdminUserDetailPage />} />
                <Route path="posts" element={<AdminPostsPage />} />
                <Route path="posts/:id" element={<AdminPostDetailPage />} />
                <Route path="moderation" element={<AdminModerationPage />} />
                <Route path="reports" element={<AdminReportsPage />} />
                <Route path="audit-log" element={<AdminAuditPage />} />
              </Route>
            </Route>

            <Route element={<Layout />}>
              <Route index element={<HomeRoute />} />
              <Route path="/auth" element={<AuthPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />

              <Route element={<ProtectedRoute />}>
                <Route path="/dashboard" element={<DashboardHomePage />} />
                <Route path="/feed" element={<FeedPage />} />
                <Route path="/posts/:id" element={<PostDetailPage />} />
                <Route path="/posts/:id/edit" element={<PostFormPage mode="edit" />} />
                <Route path="/sell" element={<PostFormPage mode="create" />} />
                <Route path="/my-posts" element={<MyPostsPage />} />
                <Route path="/saved" element={<SavedPage />} />
                <Route path="/interests" element={<InterestsPage />} />
                <Route path="/profile" element={<ProfilePage />} />
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </AdminProvider>
  );
}
