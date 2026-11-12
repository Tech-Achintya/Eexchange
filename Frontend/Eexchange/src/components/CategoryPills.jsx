import { LayoutGrid } from 'lucide-react';
import { getCategoryStyle } from '../lib/constants';

export default function CategoryPills({ categories = [], active = null, onChange, includeAll = true }) {
  return (
    <div className="category-pills" role="tablist" aria-label="Filter by category">
      {includeAll && (
        <button
          type="button"
          role="tab"
          aria-selected={!active}
          className={`category-pill ${!active ? 'active' : ''}`}
          onClick={() => onChange?.(null)}
        >
          <span className="category-pill-icon" style={{ background: '#fff1c9', color: '#a27300' }}>
            <LayoutGrid size={15} />
          </span>
          All finds
        </button>
      )}

      {categories.map((category) => {
        const style = getCategoryStyle(category.slug);
        const Icon = style.icon;
        const selected = String(active) === String(category.id);
        return (
          <button
            key={category.id}
            type="button"
            role="tab"
            aria-selected={selected}
            className={`category-pill ${selected ? 'active' : ''}`}
            onClick={() => onChange?.(selected ? null : category.id)}
          >
            <span
              className="category-pill-icon"
              style={{ background: style.bg, color: style.fg }}
            >
              <Icon size={15} />
            </span>
            {category.name}
          </button>
        );
      })}
    </div>
  );
}
