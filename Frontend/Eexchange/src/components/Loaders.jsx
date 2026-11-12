export function PostCardSkeleton() {
  return (
    <div className="post-card skeleton-card">
      <div className="skeleton" style={{ aspectRatio: '4 / 3', borderRadius: 0 }} />
      <div style={{ padding: 18, display: 'grid', gap: 12 }}>
        <div className="skeleton" style={{ height: 12, width: '38%', borderRadius: 99 }} />
        <div className="skeleton" style={{ height: 18, width: '82%', borderRadius: 99 }} />
        <div className="skeleton" style={{ height: 14, width: '55%', borderRadius: 99 }} />
        <div className="skeleton" style={{ height: 28, width: '40%', borderRadius: 99 }} />
      </div>
    </div>
  );
}

export function PostGridSkeleton({ count = 6 }) {
  return (
    <div className="post-grid">
      {Array.from({ length: count }).map((_, index) => (
        <PostCardSkeleton key={index} />
      ))}
    </div>
  );
}

export function RowSkeleton() {
  return <div className="skeleton" style={{ height: 104, borderRadius: 20, width: '100%' }} />;
}

export function ListSkeleton({ count = 4 }) {
  return (
    <div style={{ display: 'grid', gap: 14 }}>
      {Array.from({ length: count }).map((_, index) => (
        <RowSkeleton key={index} />
      ))}
    </div>
  );
}

export function PageLoader({ label = 'Loading the good stuff…' }) {
  return (
    <div className="page-loader">
      <span className="blob" />
      <span>{label}</span>
    </div>
  );
}
