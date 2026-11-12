import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAdmin } from '../context/AdminContext';

export default function AdminProtectedRoute() {
  const { isAdminAuthenticated } = useAdmin();
  const location = useLocation();

  if (!isAdminAuthenticated) {
    return (
      <Navigate
        to="/admin/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }

  return <Outlet />;
}
