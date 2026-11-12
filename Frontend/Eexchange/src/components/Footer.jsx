import { Link } from 'react-router-dom';
import { AtSign, Heart, Mail } from 'lucide-react';
import Logo from './Logo';
import './Footer.css';

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div className="footer-brand">
          <Logo />
          <p>
            The student-only marketplace for campus finds. Buy, sell and pass things
            on — without the awkward group chat haggling.
          </p>
          <span className="footer-made">
            Built for campus <Heart size={13} fill="currentColor" /> by students
          </span>
        </div>

        <div className="footer-col">
          <h4>Explore</h4>
          <Link to="/feed">Browse finds</Link>
          <Link to="/sell">Sell something</Link>
          <Link to="/saved">Wishlist</Link>
          <Link to="/interests">Your inbox</Link>
        </div>

        <div className="footer-col">
          <h4>Your account</h4>
          <Link to="/profile">Profile</Link>
          <Link to="/my-posts">My listings</Link>
          <Link to="/profile?tab=security">Security</Link>
          <Link to="/auth?mode=signup">Join free</Link>
        </div>

        <div className="footer-col footer-fun">
          <h4>Stay campus-cool</h4>
          <p>New drops every day from your own college.</p>
          <div className="footer-socials">
            <a href="mailto:hello@eexchange.app" aria-label="Email us"><Mail size={16} /></a>
            <a href="https://instagram.com" target="_blank" rel="noreferrer" aria-label="Instagram"><AtSign size={16} /></a>
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <span>© {new Date().getFullYear()} EExchange. Made with sunshine, not dark mode.</span>
        <span className="footer-pill">Campus verified only 🌱</span>
        <Link to="/admin/login" className="footer-admin-link">Team login</Link>
      </div>
    </footer>
  );
}
