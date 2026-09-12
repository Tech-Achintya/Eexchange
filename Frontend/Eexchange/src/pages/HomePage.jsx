import React, { useState, useEffect } from 'react';
import './Home.css';
import CategorySection from '../components/CategorySection';
import { CATEGORIES } from '../constants/categories';

export default function HomePage({ user }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState(null);

  useEffect(() => {
    fetch(`${import.meta.env.VITE_API_URL}/items/all`)
      .then((r) => {
        if (!r.ok) throw new Error('Failed to fetch');
        return r.json();
      })
      .then((data) => { if(Array.isArray(data)) setItems(data); setLoading(false); })
      .catch((e) => { console.error(e); setFetchError(true); setLoading(false); });
  }, []);

  const filtered = items.filter(
    (i) => {
      const matchesSearch = i.title?.toLowerCase().includes(search.toLowerCase()) ||
                           i.description?.toLowerCase().includes(search.toLowerCase());
      const matchesCategory = selectedCategory ? i.category === selectedCategory : true;
      const isNotOwn = user ? i.userEmail !== user.email : true;
      return matchesSearch && matchesCategory && isNotOwn;
    }
  );

  const handleSelectCategory = (catId) => {
    setSelectedCategory(prev => prev === catId ? null : catId);
  };

  const handleDeleteItem = async (itemId) => {
    if (!window.confirm('Admin: Delete this item?')) return;
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/delete/${itemId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      });
      if (res.ok) {
        setItems(prev => prev.filter(i => i.id !== itemId));
      }
    } catch (e) { console.error(e); }
  };

  return (
    <div className="home-page">
      {/* Hero banner */}
      <div className="home-hero">
        <div className="hero-content">
          <h1 className="hero-title">
            Campus stuff,<br />
            <span className="gradient-text">campus prices ⚡</span>
          </h1>
          <p className="hero-sub">
            The student-only marketplace for everything.
          </p>
          <div className="search-bar">
            <span className="search-icon">🔍</span>
            <input
              placeholder="Search for something specific..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
        <div className="hero-decoration" />
      </div>

      {/* Categories Grid */}
      <CategorySection 
        activeCategory={selectedCategory} 
        onSelectCategory={handleSelectCategory} 
      />

      {/* Items grid */}
      <div className="home-content">
        <div className="section-header">
          <h2>
            {search 
              ? `Results for "${search}"` 
              : selectedCategory 
                ? `${CATEGORIES.find(c => c.id === selectedCategory)?.label} Listings` 
                : (user ? 'Top Listings' : 'Recent Items')
            }
          </h2>
        </div>

        {loading ? (
          <div className="loading-grid">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="item-skeleton" />
            ))}
          </div>
        ) : fetchError ? (
          <div className="empty-state">
            <span className="empty-icon">⏳</span>
            <p>Trying to fetch...</p>
            <small>Please make sure the backend server is running.</small>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">📭</span>
            <p>No listings found</p>
            <small>{user ? 'Be the first to post something!' : 'Check back later or login to post!'}</small>
          </div>
        ) : (
          <div className="items-grid">
            {filtered.map((item) => (
              <ItemCard key={item.id} item={item} currentUser={user} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function ItemCard({ item, currentUser }) {
  const isOwn = currentUser && item.userEmail === currentUser.email;

  return (
    <div className={`item-card ${isOwn ? 'own-card' : ''}`}>
      <div className="item-card-img">
        <span className="item-emoji">
          {getEmoji(item.title)}
        </span>
        {isOwn && <span className="own-badge">Yours</span>}
      </div>
      <div className="item-card-body">
        <div className="item-header">
          <h3 className="item-title">{item.title || 'Unnamed Item'}</h3>
          <span className={`tag ${item.status === 'SOLD' ? 'tag-sold' : 'tag-available'}`}>
            {item.status === 'SOLD' ? 'Sold' : 'Available'}
          </span>
        </div>
        <p className="item-desc">{item.description || 'No description provided.'}</p>
        
        {currentUser && (
          <a 
            href={`https://outlook.office.com/mail/deeplink/compose?to=${item.userEmail}&subject=Inquiry about ${item.title} on E-Exchange`}
            target="_blank"
            rel="noopener noreferrer"
            className="contact-seller-btn"
          >
            📧 Contact Seller
          </a>
        )}

        <div className="item-footer">
          <span className="item-price">₹{item.price?.toLocaleString('en-IN')}</span>
          
          {currentUser?.role === 'ROLE_ADMIN' && (
            <div className="admin-actions">
              <button className="admin-del-btn" onClick={() => handleDeleteItem(item.id)}>🗑️</button>
            </div>
          )}

          <div className="item-seller-info">
            <span className="item-seller">Seller</span>
            {currentUser ? (
              <span className="item-seller-badge">✓ Verified</span>
            ) : (
              <span className="item-seller-private">🔒 Private</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function getEmoji(title = '') {
  const t = title.toLowerCase();
  if (t.includes('book') || t.includes('note')) return '📚';
  if (t.includes('laptop') || t.includes('macbook') || t.includes('computer')) return '💻';
  if (t.includes('phone') || t.includes('mobile') || t.includes('iphone')) return '📱';
  if (t.includes('cycle') || t.includes('bike')) return '🚲';
  if (t.includes('food') || t.includes('tiffin')) return '🍱';
  if (t.includes('headphone') || t.includes('earphone') || t.includes('airpod')) return '🎧';
  if (t.includes('camera')) return '📷';
  if (t.includes('chair') || t.includes('table') || t.includes('furniture')) return '🪑';
  return '📦';
}
