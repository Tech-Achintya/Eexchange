import React from 'react';
import { CATEGORIES } from '../constants/categories';

// Placeholder image function since generation is limited
const getIcon = (id) => `/src/assets/categories/${id}.png`;

export default function CategorySection({ activeCategory, onSelectCategory }) {
  return (
    <div className="category-section">
      {/* Grid of Cards */}
      <div className="category-grid">
        <div 
          className={`category-card ${!activeCategory ? 'active' : ''}`}
          onClick={() => onSelectCategory(null)}
        >
          <div className="category-icon-wrapper">
            <span className="category-emoji" style={{ fontSize: '1.5rem', display: 'flex' }}>
              🌐
            </span>
          </div>
          <span className="category-label">All Items</span>
        </div>
        {CATEGORIES.map((cat) => (
          <div 
            key={cat.id} 
            className={`category-card ${activeCategory === cat.id ? 'active' : ''}`}
            onClick={() => onSelectCategory(cat.id)}
          >
            <div className="category-icon-wrapper">
              <img 
                src={getIcon(cat.id)} 
                alt={cat.label} 
                className="category-icon-img" 
                onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} 
              />
              <span className="category-emoji" style={{ fontSize: '1.5rem', display: 'none' }}>
                {cat.icon}
              </span>
            </div>
            <span className="category-label">{cat.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
