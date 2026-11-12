import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  ArrowRight,
  Bookmark,
  CheckCircle2,
  Compass,
  Inbox,
  MessageCircle,
  Package,
  Plus,
  Send,
  TrendingUp,
  UserRound,
} from 'lucide-react';
import {
  getErrorMessage,
  interestsApi,
  postsApi,
  savedApi,
} from '../lib/api';
import { useAuth } from '../context/AuthContext';
import {
  firstImageOf,
  formatPrice,
  timeAgo,
} from '../lib/format';
import StatusBadge from '../components/StatusBadge';
import PostCard from '../components/PostCard';
import { ListSkeleton } from '../components/Loaders';
import CampusExchangeDashboardArt from '../components/CampusExchangeDashboardArt';
import './Dashboard.css';

export default function DashboardHomePage() {
  const { user } = useAuth();
  const [overview, setOverview] = useState({ listings: 0, saved: 0, unread: 0, sent: 0 });
  const [myPosts, setMyPosts] = useState([]);
  const [feed, setFeed] = useState([]);
  const [savedPreview, setSavedPreview] = useState([]);
  const [savedIds, setSavedIds] = useState([]);
  const [savingId, setSavingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError('');

    const results = await Promise.allSettled([
      postsApi.mine({ page: 0, size: 5 }),
      savedApi.list(),
      interestsApi.unreadCount(),
      interestsApi.sent({ page: 0, size: 1 }),
      postsApi.feed({ page: 0, size: 4, sort: 'newest' }),
    ]);

    const valueAt = (index) =>
      results[index]?.status === 'fulfilled' ? results[index].value.data : null;

    const failures = results.filter((result) => result.status === 'rejected');
    if (failures.length === results.length) {
      setError(getErrorMessage(failures[0]?.reason, 'Could not load your dashboard.'));
    }

    const mine = valueAt(0);
    const saved = valueAt(1) || [];
    const unread = valueAt(2);
    const sent = valueAt(3);
    const feedData = valueAt(4);

    setMyPosts(mine?.content || []);
    setSavedIds(saved);
    setFeed(feedData?.content || []);
    setOverview({
      listings: mine?.totalElements ?? 0,
      saved: saved.length,
      unread: Number(unread?.count ?? 0),
      sent: sent?.totalElements ?? 0,
    });

    if (saved.length) {
      const details = await Promise.allSettled(
        saved.slice(0, 3).map((id) => postsApi.detail(id)),
      );
      setSavedPreview(
        details
          .filter((result) => result.status === 'fulfilled')
          .map((result) => result.value.data)
          .filter(Boolean),
      );
    } else {
      setSavedPreview([]);
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  const handleToggleSave = async (post) => {
    if (savingId === post.id) return;
    setSavingId(post.id);
    try {
      const response = await savedApi.toggle(post.id);
      const isSaved = Boolean(response.data?.saved);
      setSavedIds((previous) => {
        if (isSaved) return previous.includes(post.id) ? previous : [...previous, post.id];
        return previous.filter((id) => id !== post.id);
      });
      setOverview((previous) => ({
        ...previous,
        saved: Math.max(0, previous.saved + (isSaved ? 1 : -1)),
      }));
      if (!isSaved) {
        setSavedPreview((previous) => previous.filter((item) => item.id !== post.id));
      }
      toast.success(isSaved ? 'Saved to your wishlist 💛' : 'Removed from wishlist');
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not update your wishlist.'));
    } finally {
      setSavingId(null);
    }
  };

  const firstName = user?.name?.split(' ')[0] || 'there';
  const collegeName = user?.collegeName || 'Your campus';
  const verified = user?.isCollegeVerified;

  const nextStep = useMemo(() => {
    if (!overview.listings) {
      return {
        icon: Package,
        tone: 'coral',
        title: 'Post your first listing',
        text: 'Turn the stuff you no longer need into cash or a trade.',
        to: '/sell',
        label: 'Sell something',
      };
    }
    if (overview.unread > 0) {
      return {
        icon: MessageCircle,
        tone: 'lav',
        title: `${overview.unread} unread ${overview.unread === 1 ? 'message' : 'messages'}`,
        text: 'Someone is interested in what you posted. Reply before they move on.',
        to: '/interests',
        label: 'Open inbox',
      };
    }
    if (overview.saved > 0) {
      return {
        icon: Bookmark,
        tone: 'sun',
        title: `${overview.saved} saved ${overview.saved === 1 ? 'find' : 'finds'}`,
        text: 'Your wishlist is waiting. Check prices before they disappear.',
        to: '/saved',
        label: 'View wishlist',
      };
    }
    return {
      icon: Compass,
      tone: 'mint',
      title: 'Find your next campus gem',
      text: 'Fresh listings from verified students are waiting in the feed.',
      to: '/feed',
      label: 'Browse the feed',
    };
  }, [overview]);

  const NextIcon = nextStep.icon;

  const statCards = [
    {
      label: 'My listings',
      value: overview.listings,
      icon: Package,
      bg: 'var(--mint-soft)',
      fg: '#0d7f75',
      note: 'Things you are selling',
      to: '/my-posts',
    },
    {
      label: 'Wishlist',
      value: overview.saved,
      icon: Bookmark,
      bg: 'var(--sun-soft)',
      fg: '#997000',
      note: 'Finds you saved',
      to: '/saved',
    },
    {
      label: 'Unread messages',
      value: overview.unread,
      icon: Inbox,
      bg: 'var(--coral-soft, #ffe9e6)',
      fg: '#d14852',
      note: 'People waiting to hear back',
      to: '/interests',
    },
    {
      label: 'Interests sent',
      value: overview.sent,
      icon: Send,
      bg: 'var(--lavender-soft)',
      fg: '#6653e0',
      note: 'Listings you reached out about',
      to: '/interests',
    },
  ];

  return (
    <div className="page dashboard-page home-page">
      <div className="container">
        <motion.section
          className="home-hero"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className="home-hero-copy">
            <span className="eyebrow">{collegeName} · your dashboard</span>
            <h1>Hi {firstName} 👋<br />Ready to swap something?</h1>
            <p>
              This is your little corner of campus. Track your listings, keep an eye on
              your wishlist, and catch every message in one place.
            </p>
            <div className="home-hero-actions">
              <Link to="/feed" className="btn btn-primary">
                <Compass size={17} /> Browse the feed
              </Link>
              <Link to="/sell" className="btn btn-light">
                <Plus size={17} /> Sell something
              </Link>
            </div>
          </div>

          <CampusExchangeDashboardArt collegeName={collegeName} verified={verified} />
        </motion.section>

        {error && <div className="alert error">{error}</div>}

        <div className="home-stat-grid">
          {statCards.map(({ label, value, icon: Icon, bg, fg, note, to }) => (
            <Link to={to} className="home-stat-card" key={label}>
              <span className="home-stat-icon" style={{ background: bg, color: fg }}>
                <Icon size={19} />
              </span>
              <div className="home-stat-copy">
                <strong>{loading ? '—' : value}</strong>
                <span>{label}</span>
              </div>
              <small><TrendingUp size={11} /> {note}</small>
            </Link>
          ))}
        </div>

        <div className="home-layout">
          <div className="home-main">
            <section className="panel home-panel">
              <div className="home-panel-head">
                <div>
                  <span className="home-panel-eyebrow"><Package size={12} /> Your shelf</span>
                  <h2 className="panel-title">Your listings</h2>
                  <p className="panel-sub">The latest things you put up for campus.</p>
                </div>
                <Link to="/my-posts" className="home-panel-link">
                  Manage all <ArrowRight size={13} />
                </Link>
              </div>

              {loading ? (
                <ListSkeleton count={3} />
              ) : myPosts.length === 0 ? (
                <div className="home-empty">
                  <span>🛍️</span>
                  <div>
                    <strong>Your shelf is empty</strong>
                    <p>Post something you no longer need — someone on campus probably does.</p>
                  </div>
                  <Link to="/sell" className="btn btn-primary btn-sm">
                    <Plus size={14} /> New listing
                  </Link>
                </div>
              ) : (
                <div className="listing-rows">
                  {myPosts.map((post) => {
                    const image = firstImageOf(post);
                    return (
                      <div className="listing-row" key={post.id}>
                        <div className="listing-thumb">
                          {image ? <img src={image} alt={post.title} /> : '🎁'}
                        </div>
                        <div className="listing-main">
                          <div className="listing-title-line">
                            <Link to={`/posts/${post.id}`} className="listing-title">
                              {post.title}
                            </Link>
                            <StatusBadge status={post.status} />
                          </div>
                          <div className="listing-meta">
                            <strong>{formatPrice(post.price)}</strong>
                            <span>·</span>
                            <span>posted {timeAgo(post.createdAt)}</span>
                          </div>
                        </div>
                        <Link to={`/posts/${post.id}`} className="btn btn-light btn-sm">
                          View <ArrowRight size={13} />
                        </Link>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            <section className="panel home-panel">
              <div className="home-panel-head">
                <div>
                  <span className="home-panel-eyebrow">Fresh finds</span>
                  <h2 className="panel-title">New on campus</h2>
                  <p className="panel-sub">The newest listings from verified students.</p>
                </div>
                <Link to="/feed" className="home-panel-link">
                  See all <ArrowRight size={13} />
                </Link>
              </div>

              {loading ? (
                <ListSkeleton count={2} />
              ) : feed.length === 0 ? (
                <div className="home-empty">
                  <span>🌱</span>
                  <div>
                    <strong>Nothing new just yet</strong>
                    <p>Be the first to post something and get the feed moving.</p>
                  </div>
                  <Link to="/sell" className="btn btn-light btn-sm">
                    Post a listing
                  </Link>
                </div>
              ) : (
                <div className="post-grid home-post-grid">
                  {feed.map((post, index) => (
                    <PostCard
                      key={post.id}
                      post={post}
                      index={index}
                      saved={savedIds.map(String).includes(String(post.id))}
                      onToggleSave={handleToggleSave}
                    />
                  ))}
                </div>
              )}
            </section>
          </div>

          <aside className="home-side">
            <section className={`panel home-next home-next-${nextStep.tone}`}>
              <span className="home-next-icon"><NextIcon size={22} /></span>
              <span className="home-panel-eyebrow">Next best step</span>
              <h2 className="panel-title">{nextStep.title}</h2>
              <p className="panel-sub">{nextStep.text}</p>
              <Link to={nextStep.to} className="btn btn-dark btn-block">
                {nextStep.label} <ArrowRight size={15} />
              </Link>
            </section>

            <section className="panel home-panel">
              <div className="home-panel-head">
                <div>
                  <span className="home-panel-eyebrow"><Bookmark size={12} /> Saved</span>
                  <h2 className="panel-title">Wishlist peek</h2>
                </div>
                <Link to="/saved" className="home-panel-link">
                  All <ArrowRight size={13} />
                </Link>
              </div>

              {loading ? (
                <ListSkeleton count={2} />
              ) : savedPreview.length === 0 ? (
                <p className="home-muted">
                  Nothing saved yet. Tap the bookmark on any listing to keep it here.
                </p>
              ) : (
                <div className="home-mini-list">
                  {savedPreview.map((post) => {
                    const image = firstImageOf(post);
                    return (
                      <Link to={`/posts/${post.id}`} className="home-mini-item" key={post.id}>
                        <span className="home-mini-thumb">
                          {image ? <img src={image} alt={post.title} /> : '🎁'}
                        </span>
                        <span className="home-mini-copy">
                          <strong>{post.title}</strong>
                          <small>{formatPrice(post.price)}</small>
                        </span>
                        <ArrowRight size={14} />
                      </Link>
                    );
                  })}
                </div>
              )}
            </section>

            <section className="panel home-panel">
              <div className="home-panel-head">
                <div>
                  <span className="home-panel-eyebrow"><CheckCircle2 size={12} /> Shortcuts</span>
                  <h2 className="panel-title">Quick links</h2>
                </div>
              </div>
              <div className="home-shortcuts">
                <Link to="/my-posts"><Package size={15} /> My listings</Link>
                <Link to="/interests"><Inbox size={15} /> Inbox {overview.unread > 0 && <span className="tab-badge">{overview.unread}</span>}</Link>
                <Link to="/saved"><Bookmark size={15} /> Wishlist</Link>
                <Link to="/profile"><UserRound size={15} /> Profile &amp; settings</Link>
              </div>
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}
