import {
  Armchair,
  Bike,
  BookOpen,
  Dumbbell,
  Package,
  PencilRuler,
  Shirt,
  Smartphone,
} from 'lucide-react';

export const CONDITION_LABELS = {
  NEW: 'Brand new',
  LIKE_NEW: 'Like new',
  GOOD: 'Good',
  FAIR: 'Fair',
  POOR: 'Well loved',
};

export const CONDITION_OPTIONS = Object.entries(CONDITION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const POST_STATUS_META = {
  PENDING_REVIEW: { label: 'In review', tone: 'sun' },
  APPROVED: { label: 'Live', tone: 'green' },
  REJECTED: { label: 'Rejected', tone: 'red' },
  SOLD: { label: 'Sold', tone: 'lav' },
  HIDDEN: { label: 'Hidden', tone: 'red' },
  MANUAL_REVIEW: { label: 'Needs a look', tone: 'sky' },
  DELETED: { label: 'Deleted', tone: 'red' },
};

export const REPORT_REASONS = [
  { value: 'SCAM', label: 'Looks like a scam' },
  { value: 'SPAM', label: 'Spam or duplicate' },
  { value: 'INAPPROPRIATE', label: 'Inappropriate content' },
  { value: 'PROHIBITED_ITEM', label: 'Prohibited item' },
  { value: 'OTHER', label: 'Something else' },
];

export const SORT_OPTIONS = [
  { value: 'newest', label: 'Freshest first' },
  { value: 'priceAsc', label: 'Price: low to high' },
  { value: 'priceDesc', label: 'Price: high to low' },
];

export const MAX_IMAGES = 5;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const CATEGORY_STYLES = {
  'books-notes': {
    icon: BookOpen,
    bg: '#fff0cd',
    fg: '#a27300',
    emoji: '📚',
  },
  'mobiles-electronics': {
    icon: Smartphone,
    bg: '#e3efff',
    fg: '#2b6fd4',
    emoji: '💻',
  },
  'bicycles-vehicles': {
    icon: Bike,
    bg: '#def7e9',
    fg: '#12864c',
    emoji: '🚲',
  },
  'hostel-furniture': {
    icon: Armchair,
    bg: '#ece8ff',
    fg: '#6653e0',
    emoji: '🪑',
  },
  'clothing-fashion': {
    icon: Shirt,
    bg: '#ffe5ee',
    fg: '#d63870',
    emoji: '👕',
  },
  'sports-fitness': {
    icon: Dumbbell,
    bg: '#ffe9e6',
    fg: '#d14852',
    emoji: '🏸',
  },
  'stationery-supplies': {
    icon: PencilRuler,
    bg: '#d9f7f3',
    fg: '#0d7f75',
    emoji: '✏️',
  },
  others: {
    icon: Package,
    bg: '#f5ede2',
    fg: '#8a6b44',
    emoji: '🎁',
  },
};

export const FALLBACK_CATEGORY = CATEGORY_STYLES.others;

export function getCategoryStyle(slug) {
  return CATEGORY_STYLES[slug] || FALLBACK_CATEGORY;
}
