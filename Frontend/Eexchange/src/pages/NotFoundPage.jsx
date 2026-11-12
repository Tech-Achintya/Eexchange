import { Link } from 'react-router-dom';
import { Compass, Home } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="page">
      <div className="container">
        <div className="empty-state" style={{ marginTop: 40 }}>
          <span className="empty-icon" style={{ background: 'var(--lavender-soft)', color: 'var(--lavender)' }}>
            <Compass size={36} />
          </span>
          <h3 style={{ fontSize: '2.4rem' }}>404 — lost on campus?</h3>
          <p>
            This page wandered off somewhere between the library and the canteen.
          </p>
          <div className="flex gap-12 wrap" style={{ marginTop: 14 }}>
            <Link to="/" className="btn btn-primary"><Home size={16} /> Back home</Link>
            <Link to="/feed" className="btn btn-light">Browse the feed</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
