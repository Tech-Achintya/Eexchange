import { Bookmark, Clock, MapPin } from 'lucide-react';
import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { CONDITION_LABELS } from '../lib/constants';
import { firstImageOf, formatPrice, timeAgo } from '../lib/format';

function styleForCategory(name = '') {
  const value = name.toLowerCase();
  if (value.includes('book') || value.includes('note')) return { emoji: '📚', bg: '#fff0cd', fg: '#a27300' };
  if (value.includes('mobile') || value.includes('electronic')) return { emoji: '💻', bg: '#e3efff', fg: '#2b6fd4' };
  if (value.includes('bicycle') || value.includes('vehicle')) return { emoji: '🚲', bg: '#def7e9', fg: '#12864c' };
  if (value.includes('hostel') || value.includes('furniture')) return { emoji: '🪑', bg: '#ece8ff', fg: '#6653e0' };
  if (value.includes('cloth') || value.includes('fashion')) return { emoji: '👕', bg: '#ffe5ee', fg: '#d63870' };
  if (value.includes('sport') || value.includes('fitness')) return { emoji: '🏸', bg: '#ffe9e6', fg: '#d14852' };
  if (value.includes('station')) return { emoji: '✏️', bg: '#d9f7f3', fg: '#0d7f75' };
  return { emoji: '🎁', bg: '#f5ede2', fg: '#8a6b44' };
}

export default function PostCard({ post, saved = false, onToggleSave, index = 0 }) {
  const image = firstImageOf(post);
  const palette = styleForCategory(post?.categoryName);
  const sold = post?.status === 'SOLD';

  return (
    <motion.article
      className="post-card"
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: Math.min(index * 0.04, 0.35), ease: [0.22, 1, 0.36, 1] }}
    >
      <Link to={`/posts/${post.id}`} className="post-card-media" aria-label={post.title}>
        {image ? (
          <img src={image} alt={post.title} loading="lazy" />
        ) : (
          <div className="post-placeholder" style={{ background: palette.bg, color: palette.fg }}>
            <span>{palette.emoji}</span>
          </div>
        )}
        <span className="post-condition">
          {CONDITION_LABELS[post.condition] || post.condition}
        </span>
        {sold && <span className="post-sold">SOLD</span>}
      </Link>

      <div className="post-card-body">
        <div className="post-card-topline">
          <span className="post-category">{post.categoryName || 'Campus find'}</span>
          <span className="post-age">{timeAgo(post.createdAt)}</span>
        </div>

        <Link to={`/posts/${post.id}`} className="post-card-title">
          {post.title}
        </Link>

        <div className="post-card-bottom">
          <div>
            <p className="post-price">{formatPrice(post.price)}</p>
            <p className="post-seller">
              <span className="seller-dot" />
              {post.sellerName || 'Campus seller'}
              <MapPin size={12} />
            </p>
          </div>

          {onToggleSave && (
            <button
              type="button"
              className={`save-btn ${saved ? 'is-saved' : ''}`}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                onToggleSave(post);
              }}
              aria-label={saved ? 'Remove from wishlist' : 'Save to wishlist'}
              aria-pressed={saved}
            >
              <Bookmark size={18} fill={saved ? 'currentColor' : 'none'} />
            </button>
          )}
        </div>

        {post.status && post.status !== 'APPROVED' && post.status !== 'SOLD' && (
          <span className="post-status-note">
            <Clock size={12} /> {post.status === 'PENDING_REVIEW' ? 'Waiting for review' : String(post.status).replaceAll('_', ' ').toLowerCase()}
          </span>
        )}
      </div>
    </motion.article>
  );
}
