import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  ArrowLeft,
  CircleDollarSign,
  ImagePlus,
  Info,
  Loader2,
  Plus,
  ShieldCheck,
  Trash2,
  UploadCloud,
} from 'lucide-react';
import { catalogApi, getErrorMessage, postsApi } from '../lib/api';
import {
  ACCEPTED_IMAGE_TYPES,
  CONDITION_OPTIONS,
  MAX_IMAGE_BYTES,
  MAX_IMAGES,
} from '../lib/constants';
import { getCategoryStyle } from '../lib/constants';
import { formatPrice, timeAgo } from '../lib/format';
import { useAuth } from '../context/AuthContext';
import EmptyState from '../components/EmptyState';
import { PageLoader } from '../components/Loaders';
import './PostFormPage.css';

const initialForm = {
  title: '',
  description: '',
  price: '',
  condition: 'GOOD',
  categoryId: '',
};

export default function PostFormPage({ mode = 'create' }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isEdit = mode === 'edit';

  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [existingImages, setExistingImages] = useState([]);
  const [newFiles, setNewFiles] = useState([]);
  const [focused, setFocused] = useState(false);
  const [loadingPost, setLoadingPost] = useState(isEdit);
  const [notFound, setNotFound] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [saving, setSaving] = useState(false);
  const inputRef = useRef(null);
  const previewsRef = useRef([]);

  useEffect(() => {
    catalogApi
      .categories()
      .then((response) => setCategories(response.data || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    previewsRef.current.forEach((url) => URL.revokeObjectURL(url));
    previewsRef.current = [];
    setNewFiles([]);
    setExistingImages([]);

    if (!isEdit) {
      setForm(initialForm);
      setLoadingPost(false);
      return;
    }

    let active = true;
    setLoadingPost(true);
    postsApi
      .detail(id)
      .then((response) => {
        if (!active) return;
        const post = response.data;
        if (String(post.sellerId) !== String(user?.id)) {
          setNotFound(true);
          return;
        }
        setForm({
          title: post.title || '',
          description: post.description || '',
          price: post.price ?? '',
          condition: post.condition || 'GOOD',
          categoryId: post.categoryId || '',
        });
        setExistingImages(post.imageUrls || []);
      })
      .catch(() => {
        if (active) setNotFound(true);
      })
      .finally(() => {
        if (active) setLoadingPost(false);
      });

    return () => { active = false; };
  }, [id, isEdit, user?.id]);

  useEffect(
    () => () => {
      previewsRef.current.forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );

  const update = (field, value) => setForm((previous) => ({ ...previous, [field]: value }));

  const addFiles = (fileList) => {
    const incoming = Array.from(fileList || []);
    if (!incoming.length) return;

    const available = MAX_IMAGES - existingImages.length - newFiles.length;
    if (available <= 0) {
      toast.error(`You can have at most ${MAX_IMAGES} photos.`);
      return;
    }

    const accepted = [];
    for (const file of incoming) {
      if (accepted.length >= available) {
        toast.error(`Only ${available} more photo${available === 1 ? '' : 's'} can be added.`);
        break;
      }
      if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
        toast.error(`${file.name} is not a JPEG, PNG or WebP image.`);
        continue;
      }
      if (file.size > MAX_IMAGE_BYTES) {
        toast.error(`${file.name} is over 5 MB.`);
        continue;
      }
      const preview = URL.createObjectURL(file);
      previewsRef.current.push(preview);
      accepted.push({ file, preview, key: `${file.name}-${file.size}-${Date.now()}-${accepted.length}` });
    }

    if (accepted.length) setNewFiles((previous) => [...previous, ...accepted]);
  };

  const removeNewFile = (key) => {
    setNewFiles((previous) => {
      const target = previous.find((item) => item.key === key);
      if (target) {
        URL.revokeObjectURL(target.preview);
        previewsRef.current = previewsRef.current.filter((url) => url !== target.preview);
      }
      return previous.filter((item) => item.key !== key);
    });
  };

  const validate = () => {
    if (form.title.trim().length < 5 || form.title.trim().length > 150) {
      return 'Title needs to be between 5 and 150 characters.';
    }
    if (form.description.trim().length < 10 || form.description.trim().length > 2000) {
      return 'Description needs to be between 10 and 2000 characters.';
    }
    if (!form.categoryId) return 'Pick a category for your find.';
    if (form.price === '' || Number.isNaN(Number(form.price))) return 'Add a price — 0 is fine for free stuff.';
    if (Number(form.price) < 0) return 'Price cannot be negative.';
    if (Number(form.price) > 9999999.99) return 'That price is a little too ambitious. Max is ₹99,99,999.99.';
    if (!/^\d+(\.\d{1,2})?$/.test(String(form.price).trim())) {
      return 'Use at most 2 decimal places for the price.';
    }
    if (!form.condition) return 'Pick the item condition.';
    if (!isEdit && newFiles.length === 0) return 'Add at least one photo so buyers can see it.';
    if (isEdit && existingImages.length + newFiles.length === 0) return 'Keep at least one photo.';
    if (existingImages.length + newFiles.length > MAX_IMAGES) return `Max ${MAX_IMAGES} photos.`;
    return '';
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitError('');
    const validation = validate();
    if (validation) {
      setSubmitError(validation);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title: form.title.trim(),
        description: form.description.trim(),
        categoryId: Number(form.categoryId),
        price: Number(form.price),
        condition: form.condition,
      };

      const data = new FormData();
      data.append('post', new Blob([JSON.stringify(payload)], { type: 'application/json' }));

      if (isEdit) {
        newFiles.forEach((item) => data.append('newImages', item.file));
        await postsApi.update(id, data);
        toast.success('Listing updated and sent for a fresh review ✨');
      } else {
        newFiles.forEach((item) => data.append('images', item.file));
        await postsApi.create(data);
        toast.success('Listing submitted! We’ll review it shortly 🎉');
      }
      navigate('/my-posts');
    } catch (requestError) {
      setSubmitError(getErrorMessage(requestError, 'Could not save the listing.'));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setSaving(false);
    }
  };

  if (loadingPost) {
    return (
      <div className="page">
        <div className="container"><PageLoader label="Loading your listing…" /></div>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="page">
        <div className="container">
          <EmptyState
            icon={Info}
            title="We couldn’t open that listing"
            description="It may have been removed, or it isn’t yours to edit."
            action={<Link to="/my-posts" className="btn btn-primary">Back to my listings</Link>}
          />
        </div>
      </div>
    );
  }

  const preview = newFiles[0]?.preview || existingImages[0];
  const selectedCategory = categories.find((category) => String(category.id) === String(form.categoryId));
  const selectedStyle = selectedCategory ? getCategoryStyle(selectedCategory.slug) : null;
  const totalPhotos = existingImages.length + newFiles.length;

  return (
    <div className="page post-form-page">
      <div className="container">
        <button type="button" className="detail-back" onClick={() => navigate(-1)}>
          <ArrowLeft size={16} /> Back
        </button>

        <div className="form-page-head">
          <div>
            <span className="eyebrow">{isEdit ? 'Polish it up' : 'New listing'}</span>
            <h1>{isEdit ? 'Give your listing a glow-up.' : 'What are you passing on?'}</h1>
            <p>
              {isEdit
                ? 'Every edit goes through a quick fresh review before it goes live again.'
                : 'A few photos, an honest condition and a fair price — that’s all it takes.'}
            </p>
          </div>
          <div className="form-head-note">
            <ShieldCheck size={18} />
            <span>All listings are reviewed before going live. Keep it campus-friendly.</span>
          </div>
        </div>

        {submitError && <div className="alert error">{submitError}</div>}

        <div className="form-layout">
          <form className="listing-form" onSubmit={handleSubmit} noValidate>
            <section className="panel">
              <h2 className="panel-title"><ImagePlus size={19} /> Photos</h2>
              <p className="panel-sub">
                Up to {MAX_IMAGES} photos. First photo becomes the cover. JPEG, PNG or WebP, max 5 MB each.
              </p>

              <div
                className={`dropzone ${focused ? 'is-dragging' : ''} ${totalPhotos ? 'has-files' : ''}`}
                onDragOver={(event) => { event.preventDefault(); setFocused(true); }}
                onDragLeave={() => setFocused(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setFocused(false);
                  addFiles(event.dataTransfer.files);
                }}
              >
                <div className="photo-grid">
                  {existingImages.map((url, index) => (
                    <div className="photo-tile" key={`${url}-${index}`}>
                      <img src={url} alt={`Existing photo ${index + 1}`} />
                      <span className="photo-tag">Current</span>
                    </div>
                  ))}

                  {newFiles.map((item, index) => (
                    <div className="photo-tile" key={item.key}>
                      <img src={item.preview} alt={`New photo ${index + 1}`} />
                      <button
                        type="button"
                        className="photo-remove"
                        onClick={() => removeNewFile(item.key)}
                        aria-label="Remove photo"
                      >
                        <Trash2 size={14} />
                      </button>
                      {existingImages.length === 0 && index === 0 && (
                        <span className="photo-tag cover">Cover</span>
                      )}
                    </div>
                  ))}

                  {totalPhotos < MAX_IMAGES && (
                    <button
                      type="button"
                      className="photo-add"
                      onClick={() => inputRef.current?.click()}
                    >
                      <UploadCloud size={24} />
                      <span>Add photo</span>
                      <small>{totalPhotos}/{MAX_IMAGES}</small>
                    </button>
                  )}
                </div>

                <input
                  ref={inputRef}
                  type="file"
                  accept={ACCEPTED_IMAGE_TYPES.join(',')}
                  multiple
                  hidden
                  onChange={(event) => {
                    addFiles(event.target.files);
                    event.target.value = '';
                  }}
                />

                <p className="dropzone-hint">
                  Drag & drop straight from your camera roll, or click “Add photo”.
                </p>
              </div>

              {isEdit && existingImages.length > 0 && (
                <p className="inline-note">
                  <Info size={14} /> Existing photos stay on the listing. New photos are added after them.
                </p>
              )}
            </section>

            <section className="panel">
              <h2 className="panel-title">The details</h2>
              <p className="panel-sub">Be specific — it helps the right person find it.</p>

              <div className="form-group">
                <label htmlFor="post-title">Title</label>
                <input
                  id="post-title"
                  maxLength={150}
                  placeholder="e.g. Casio FX-991 calculator · perfect for 1st year"
                  value={form.title}
                  onChange={(event) => update('title', event.target.value)}
                  required
                />
                <span className="char-count">{form.title.length}/150 · min 5</span>
              </div>

              <div className="form-group">
                <label htmlFor="post-description">Description</label>
                <textarea
                  id="post-description"
                  rows={6}
                  maxLength={2000}
                  placeholder="Condition, what’s included, why you’re selling, pickup spot…"
                  value={form.description}
                  onChange={(event) => update('description', event.target.value)}
                  required
                />
                <span className="char-count">{form.description.length}/2000 · min 10</span>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label htmlFor="post-price">Price</label>
                  <div className="price-input">
                    <span>₹</span>
                    <input
                      id="post-price"
                      type="number"
                      min="0"
                      max="9999999.99"
                      step="0.01"
                      placeholder="0 for free"
                      value={form.price}
                      onChange={(event) => update('price', event.target.value)}
                      required
                    />
                  </div>
                  <span className="field-hint">Fair campus pricing wins hearts.</span>
                </div>

                <div className="form-group">
                  <label htmlFor="post-category">Category</label>
                  <select
                    id="post-category"
                    value={form.categoryId}
                    onChange={(event) => update('categoryId', event.target.value)}
                    required
                  >
                    <option value="">Choose a category</option>
                    {categories.map((category) => (
                      <option key={category.id} value={category.id}>{category.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label>Condition</label>
                <div className="condition-grid">
                  {CONDITION_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`condition-option ${form.condition === option.value ? 'selected' : ''}`}
                      onClick={() => update('condition', option.value)}
                    >
                      <span className="condition-emoji">
                        {option.value === 'NEW' && '✨'}
                        {option.value === 'LIKE_NEW' && '🌟'}
                        {option.value === 'GOOD' && '👍'}
                        {option.value === 'FAIR' && '🙂'}
                        {option.value === 'POOR' && '🧡'}
                      </span>
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
            </section>

            <div className="form-submit-row">
              <button type="button" className="btn btn-light" onClick={() => navigate(-1)} disabled={saving}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary btn-lg" disabled={saving}>
                {saving ? <Loader2 size={18} className="spin" /> : <Plus size={18} />}
                {saving ? 'Saving…' : isEdit ? 'Save & resubmit' : 'Post my listing'}
              </button>
            </div>
          </form>

          <aside className="form-side">
            <div className="preview-card">
              <span className="preview-label">Live preview</span>
              <div className="preview-media" style={{ background: selectedStyle?.bg || 'var(--cream-2)' }}>
                {preview ? (
                  <img src={preview} alt="" />
                ) : (
                  <span>{selectedStyle?.emoji || '📦'}</span>
                )}
                <span className="preview-cond">
                  {CONDITION_OPTIONS.find((option) => option.value === form.condition)?.label}
                </span>
              </div>
              <div className="preview-body">
                <span className="preview-category">{selectedCategory?.name || 'Your category'}</span>
                <h3>{form.title || 'Your listing title will appear here'}</h3>
                <div className="preview-bottom">
                  <strong>{form.price === '' ? '₹—' : formatPrice(form.price)}</strong>
                  <span className="mini-seller"><span className="seller-dot" /> {user?.name?.split(' ')[0] || 'You'}</span>
                </div>
                <small className="preview-time">{timeAgo(new Date().toISOString())} · just now</small>
              </div>
            </div>

            <div className="tips-card">
              <h4><CircleDollarSign size={17} /> Quick pricing tip</h4>
              <p>
                Check what similar things are going for on the feed. A slightly lower
                price usually means a much faster sale.
              </p>
            </div>

            <div className="tips-card soft">
              <h4><ShieldCheck size={17} /> What happens next?</h4>
              <ul>
                <li>Your listing goes into a quick review.</li>
                <li>Approved listings appear in your campus feed.</li>
                <li>Interested students send you a message with contact info.</li>
              </ul>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
