import { Repeat2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function Logo({ compact = false, to = '/' }) {
  return (
    <Link to={to} className="brand" aria-label="EExchange home">
      <span className="brand-mark">
        <Repeat2 size={20} strokeWidth={2.6} />
        <span className="brand-spark" />
      </span>
      {!compact && (
        <span className="brand-word">
          E<span>Exchange</span>
        </span>
      )}
    </Link>
  );
}
