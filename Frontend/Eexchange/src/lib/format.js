import { formatDistanceToNowStrict, format } from 'date-fns';

export function formatPrice(value) {
  const amount = Number(value ?? 0);
  if (Number.isNaN(amount)) return '₹0';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: amount % 1 === 0 ? 0 : 2,
    minimumFractionDigits: 0,
  }).format(amount);
}

export function timeAgo(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return `${formatDistanceToNowStrict(date)} ago`;
}

export function formatDate(value, pattern = 'dd MMM yyyy') {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return format(date, pattern);
}

export function initials(name = '') {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || '?';
}

export function pluralize(count, singular, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function firstImageOf(post) {
  if (!post) return null;
  if (post.firstImageUrl) return post.firstImageUrl;
  if (Array.isArray(post.imageUrls) && post.imageUrls.length) return post.imageUrls[0];
  if (Array.isArray(post.images) && post.images.length) return post.images[0]?.url;
  return null;
}
