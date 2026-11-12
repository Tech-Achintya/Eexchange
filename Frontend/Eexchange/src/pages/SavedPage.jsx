import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Bookmark, Compass, Loader2 } from 'lucide-react';
import { getErrorMessage, postsApi, savedApi } from '../lib/api';
import PostCard from '../components/PostCard';
import EmptyState from '../components/EmptyState';
import { PostGridSkeleton } from '../components/Loaders';
import './Dashboard.css';

export default function SavedPage() {
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingId, setSavingId] = useState(null);

  const loadSaved = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const idsResponse = await savedApi.list();
      const ids = idsResponse.data || [];
      if (!ids.length) {
        setPosts([]);
        return;
      }

      const results = await Promise.allSettled(ids.map((postId) => postsApi.detail(postId)));
      const loaded = results
        .filter((result) => result.status === 'fulfilled' && result.value?.data)
        .map((result) => result.value.data);
      setPosts(loaded);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load your wishlist.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSaved();
  }, [loadSaved]);

  const handleToggleSave = async (post) => {
    if (savingId === post.id) return;
    setSavingId(post.id);
    try {
      const response = await savedApi.toggle(post.id);
      if (!response.data?.saved) {
        setPosts((previous) => previous.filter((item) => item.id !== post.id));
        toast.success('Removed from your wishlist');
      }
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not update your wishlist.'));
    } finally {
      setSavingId(null);
    }
  };

  return (
    <div className="page dashboard-page">
      <div className="container">
        <div className="page-head">
          <div>
            <span className="eyebrow">Saved for later</span>
            <h1>Your wishlist</h1>
            <p>All the campus finds you’ve bookmarked, in one safe little pile.</p>
          </div>
          <Link to="/feed" className="btn btn-light">
            <Compass size={16} /> Keep browsing
          </Link>
        </div>

        {error && <div className="alert error">{error}</div>}

        {loading ? (
          <PostGridSkeleton count={4} />
        ) : posts.length === 0 ? (
          <EmptyState
            icon={Bookmark}
            title="Nothing saved yet"
            description="Tap the little bookmark on anything you like and it’ll wait for you here."
            action={<Link to="/feed" className="btn btn-primary"><Compass size={16} /> Find something good</Link>}
          />
        ) : (
          <>
            <div className="dashboard-meta">
              <span>{posts.length} saved {posts.length === 1 ? 'find' : 'finds'}</span>
              {savingId && <span className="inline-saving"><Loader2 size={13} className="spin" /> Saving…</span>}
            </div>
            <div className="post-grid">
              {posts.map((post, index) => (
                <PostCard
                  key={post.id}
                  post={post}
                  index={index}
                  saved
                  onToggleSave={handleToggleSave}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
