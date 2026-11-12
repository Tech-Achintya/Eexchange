import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  ArrowRight,
  Check,
  PackageSearch,
  Plus,
  Search,
  SlidersHorizontal,
  X,
} from 'lucide-react';
import { catalogApi, getErrorMessage, postsApi, savedApi } from '../lib/api';
import { SORT_OPTIONS } from '../lib/constants';
import { pluralize } from '../lib/format';
import { useAuth } from '../context/AuthContext';
import useDebouncedValue from '../hooks/useDebouncedValue';
import CategoryPills from '../components/CategoryPills';
import PostCard from '../components/PostCard';
import EmptyState from '../components/EmptyState';
import { PostGridSkeleton } from '../components/Loaders';
import './FeedPage.css';

const PAGE_SIZE = 12;

export default function FeedPage() {
  const { user } = useAuth();

  const [categories, setCategories] = useState([]);
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search, 400);
  const [category, setCategory] = useState(null);
  const [sort, setSort] = useState('newest');
  const [priceOpen, setPriceOpen] = useState(false);
  const [priceInput, setPriceInput] = useState({ min: '', max: '' });
  const [price, setPrice] = useState({ min: '', max: '' });

  const [savedIds, setSavedIds] = useState(() => new Set());
  const [savingId, setSavingId] = useState(null);

  useEffect(() => {
    catalogApi
      .categories()
      .then((response) => setCategories(response.data || []))
      .catch(() => {});
    savedApi
      .list()
      .then((response) => setSavedIds(new Set(response.data || [])))
      .catch(() => {});
  }, []);

  const fetchFeed = useCallback(
    async (pageNumber = 0, append = false) => {
      if (append) setLoadingMore(true);
      else setLoading(true);
      setError('');
      try {
        const params = { page: pageNumber, size: PAGE_SIZE, sort };
        if (category) params.category = category;
        if (debouncedSearch.trim()) params.q = debouncedSearch.trim();
        if (price.min !== '') params.minPrice = price.min;
        if (price.max !== '') params.maxPrice = price.max;

        const response = await postsApi.feed(params);
        const data = response.data || {};
        const content = data.content || [];
        setPosts((previous) => (append ? [...previous, ...content] : content));
        setPage(data.page ?? pageNumber);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? content.length);
      } catch (requestError) {
        setError(getErrorMessage(requestError, 'Could not load the campus feed.'));
        if (!append) setPosts([]);
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [category, debouncedSearch, price, sort],
  );

  useEffect(() => {
    fetchFeed(0, false);
  }, [fetchFeed]);

  const handleToggleSave = async (post) => {
    if (savingId === post.id) return;
    setSavingId(post.id);
    try {
      const response = await savedApi.toggle(post.id);
      const isSaved = Boolean(response.data?.saved);
      setSavedIds((previous) => {
        const next = new Set(previous);
        if (isSaved) next.add(post.id);
        else next.delete(post.id);
        return next;
      });
      toast.success(isSaved ? 'Saved to your wishlist 💛' : 'Removed from wishlist');
    } catch (requestError) {
      toast.error(getErrorMessage(requestError, 'Could not update your wishlist.'));
    } finally {
      setSavingId(null);
    }
  };

  const applyPrice = (event) => {
    event?.preventDefault();
    if (priceInput.min !== '' && priceInput.max !== '' && Number(priceInput.min) > Number(priceInput.max)) {
      toast.error('Minimum price cannot be higher than maximum.');
      return;
    }
    setPrice({ ...priceInput });
    setPriceOpen(false);
  };

  const hasFilters = Boolean(category || debouncedSearch || price.min || price.max || sort !== 'newest');
  const clearFilters = () => {
    setSearch('');
    setCategory(null);
    setSort('newest');
    setPrice({ min: '', max: '' });
    setPriceInput({ min: '', max: '' });
  };

  const firstName = useMemo(() => user?.name?.split(' ')[0] || 'there', [user]);

  return (
    <div className="page feed-page">
      <div className="container">
        <motion.section
          className="feed-hero"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45 }}
        >
          <div className="feed-hero-copy">
            <span className="eyebrow">{user?.collegeName || 'Your campus'} · live feed</span>
            <h1>Hey {firstName} 👋<br />See what’s moving on campus.</h1>
            <p>
              Fresh finds from verified students at your college. Save the good stuff
              before someone else grabs it.
            </p>
            <div className="feed-hero-actions">
              <Link to="/sell" className="btn btn-primary">
                <Plus size={17} /> Sell something
              </Link>
              <Link to="/saved" className="btn btn-light">
                Your wishlist <ArrowRight size={16} />
              </Link>
            </div>
          </div>
          <div className="feed-hero-art" aria-hidden="true">
            <div className="feed-bubble bubble-one">🎒</div>
            <div className="feed-bubble bubble-two">📚</div>
            <div className="feed-bubble bubble-three">🎧</div>
            <div className="feed-bubble bubble-four">🚲</div>
            <div className="feed-hero-sticker">
              <strong>{totalElements}</strong>
              <span>{totalElements === 1 ? 'find' : 'finds'} right now</span>
            </div>
          </div>
        </motion.section>

        <section className="feed-toolbar">
          <div className="feed-search">
            <Search size={18} />
            <input
              type="search"
              placeholder="Search campus finds — ‘cycle’, ‘notes’, ‘lamp’…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              aria-label="Search listings"
            />
            {search && (
              <button type="button" className="search-clear" onClick={() => setSearch('')} aria-label="Clear search">
                <X size={15} />
              </button>
            )}
          </div>

          <div className="feed-toolbar-actions">
            <button
              type="button"
              className={`btn btn-light filter-btn ${priceOpen || price.min || price.max ? 'active' : ''}`}
              onClick={() => setPriceOpen((open) => !open)}
            >
              <SlidersHorizontal size={16} />
              Price
              {(price.min || price.max) && <span className="filter-dot" />}
            </button>

            <div className="sort-wrap">
              <select value={sort} onChange={(event) => setSort(event.target.value)} aria-label="Sort listings">
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </div>
          </div>

          <AnimatePresence>
            {priceOpen && (
              <motion.form
                className="price-panel"
                onSubmit={applyPrice}
                initial={{ opacity: 0, y: -8, height: 0 }}
                animate={{ opacity: 1, y: 0, height: 'auto' }}
                exit={{ opacity: 0, y: -6, height: 0 }}
                transition={{ duration: 0.22 }}
              >
                <div className="price-panel-inner">
                  <div className="price-fields">
                    <div className="form-group">
                      <label htmlFor="min-price">Min price (₹)</label>
                      <input
                        id="min-price"
                        type="number"
                        min="0"
                        step="1"
                        placeholder="0"
                        value={priceInput.min}
                        onChange={(event) => setPriceInput({ ...priceInput, min: event.target.value })}
                      />
                    </div>
                    <span className="price-dash">to</span>
                    <div className="form-group">
                      <label htmlFor="max-price">Max price (₹)</label>
                      <input
                        id="max-price"
                        type="number"
                        min="0"
                        step="1"
                        placeholder="Any"
                        value={priceInput.max}
                        onChange={(event) => setPriceInput({ ...priceInput, max: event.target.value })}
                      />
                    </div>
                  </div>
                  <div className="price-panel-actions">
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setPriceInput({ min: '', max: '' }); setPrice({ min: '', max: '' }); }}>
                      Clear
                    </button>
                    <button type="submit" className="btn btn-dark btn-sm"><Check size={15} /> Apply price</button>
                  </div>
                </div>
              </motion.form>
            )}
          </AnimatePresence>
        </section>

        <CategoryPills categories={categories} active={category} onChange={setCategory} />

        <div className="feed-meta">
          <p>
            {loading ? 'Finding the good stuff…' : pluralize(totalElements, 'find')}
            {debouncedSearch && <> for “{debouncedSearch}”</>}
          </p>
          {hasFilters && (
            <button type="button" className="clear-link" onClick={clearFilters}>
              <X size={14} /> Clear filters
            </button>
          )}
        </div>

        {error && <div className="alert error">{error}</div>}

        {loading ? (
          <PostGridSkeleton count={8} />
        ) : posts.length === 0 ? (
          <EmptyState
            icon={PackageSearch}
            title={hasFilters ? 'No finds match that yet' : 'It’s quiet on campus…'}
            description={
              hasFilters
                ? 'Try a broader search or clear the filters — your next great find might be hiding.'
                : 'Be the first to list something and get the campus feed going.'
            }
            action={
              hasFilters ? (
                <button type="button" className="btn btn-primary" onClick={clearFilters}>Clear filters</button>
              ) : (
                <Link to="/sell" className="btn btn-primary"><Plus size={16} /> Post the first find</Link>
              )
            }
          />
        ) : (
          <>
            <div className="post-grid">
              {posts.map((post, index) => (
                <PostCard
                  key={post.id}
                  post={post}
                  index={index}
                  saved={savedIds.has(post.id)}
                  onToggleSave={handleToggleSave}
                />
              ))}
            </div>

            {page + 1 < totalPages && (
              <div className="load-more">
                <button
                  type="button"
                  className="btn btn-light btn-lg"
                  onClick={() => fetchFeed(page + 1, true)}
                  disabled={loadingMore}
                >
                  {loadingMore ? <span className="spinner" style={{ borderTopColor: 'var(--ink)' }} /> : 'Load more finds'}
                </button>
                <span className="load-progress">Page {page + 1} of {totalPages}</span>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
