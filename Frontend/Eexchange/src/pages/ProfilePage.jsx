import React, { useState, useEffect } from 'react';
import './Profile.css';
import { CATEGORIES } from '../constants/categories';

const token = () => localStorage.getItem('token');

export default function ProfilePage({ user, onUpdateUser }) {
  const [tab, setTab] = useState('info'); // 'info' | 'posts' | 'add'
  const [myItems, setMyItems] = useState([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [editItem, setEditItem] = useState(null);

  // Profile update form
  const [profileForm, setProfileForm] = useState({ name: user?.name || '' });
  const [profileMsg, setProfileMsg] = useState('');

  // Add item form
  const [addForm, setAddForm] = useState({ title: '', description: '', price: '', category: 'others' });
  const [addMsg, setAddMsg] = useState('');

  // Edit item form
  const [editForm, setEditForm] = useState({ title: '', description: '', price: '', category: 'others' });
  const [editMsg, setEditMsg] = useState('');

  useEffect(() => {
    if (tab === 'posts') fetchMyItems();
  }, [tab]);

  const fetchMyItems = async () => {
    setLoadingItems(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/my`, {
        headers: { Authorization: `Bearer ${token()}` },
      });
      const data = await res.json();
      setMyItems(Array.isArray(data) ? data : []);
    } catch {
      setMyItems([]);
    } finally {
      setLoadingItems(false);
    }
  };

  const handleAddItem = async (e) => {
    e.preventDefault();
    setAddMsg('');
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/add`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token()}`,
        },
        body: JSON.stringify({
          title: addForm.title,
          description: addForm.description,
          price: parseFloat(addForm.price),
          category: addForm.category,
        }),
      });
      if (res.ok) {
        setAddMsg('success:Item posted! 🎉');
        setAddForm({ title: '', description: '', price: '', category: 'others' });
      } else {
        const errorData = await res.json();
        setAddMsg(`error:${errorData.message || 'Failed to post item'}`);
      }
    } catch {
      setAddMsg('error:Network error');
    }
  };

  const openEdit = (item) => {
    setEditItem(item);
    setEditForm({ 
      title: item.title, 
      description: item.description, 
      price: item.price, 
      category: item.category || 'others' 
    });
    setEditMsg('');
  };

  const handleEditItem = async (e) => {
    e.preventDefault();
    setEditMsg('');
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/edit/${editItem.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token()}`,
        },
        body: JSON.stringify({
          title: editForm.title,
          description: editForm.description,
          price: parseFloat(editForm.price),
          category: editForm.category,
        }),
      });
      if (res.ok) {
        setEditMsg('success:Updated successfully! ✅');
        fetchMyItems();
        setTimeout(() => setEditItem(null), 1200);
      } else {
        const errorData = await res.json();
        setEditMsg(`error:${errorData.message || 'Update failed'}`);
      }
    } catch {
      setEditMsg('error:Network error');
    }
  };

  const handleDeleteItem = async (itemId) => {
    if (!window.confirm('Are you sure you want to delete this item forever?')) return;
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/delete/${itemId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token()}` },
      });
      if (res.ok) {
        fetchMyItems();
      } else {
        const data = await res.json();
        alert(data.message || 'Failed to delete item');
      }
    } catch {
      alert('Network error');
    }
  };

  const TABS = [
    { id: 'info', label: '👤 My Info' },
    { id: 'posts', label: '📦 My Posts' },
    { id: 'add', label: '➕ Post Item' },
  ];

  return (
    <div className="profile-page">
      {/* Sidebar */}
      <aside className="profile-sidebar">
        <div className="profile-avatar">
          <span>{user?.name?.[0]?.toUpperCase() || '?'}</span>
        </div>
        <h2 className="profile-name">{user?.name || 'Student'}</h2>
        <p className="profile-email">{user?.email}</p>

        <nav className="profile-nav">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`profile-nav-btn ${tab === t.id ? 'active' : ''}`}
              onClick={() => { setTab(t.id); setEditItem(null); }}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </aside>

      {/* Main panel */}
      <main className="profile-main">
        {/* ─── My Info Tab ─── */}
        {tab === 'info' && (
          <div className="panel">
            <h3 className="panel-title">Account Details</h3>
            <div className="info-grid">
              <div className="info-row">
                <span className="info-label">Full Name</span>
                <span className="info-value">{user?.name}</span>
              </div>
              <div className="info-row">
                <span className="info-label">Email</span>
                <span className="info-value">{user?.email}</span>
              </div>
            </div>

            <div className="divider" />
            <h4 className="sub-title">Update Name</h4>
            <form
              className="simple-form"
              onSubmit={async (e) => {
                e.preventDefault();
                setProfileMsg('');
                try {
                  const res = await fetch(`${import.meta.env.VITE_API_URL}/user/update`, {
                    method: 'PUT',
                    headers: {
                      'Content-Type': 'application/json',
                      Authorization: `Bearer ${token()}`,
                    },
                    body: JSON.stringify({ name: profileForm.name }),
                  });
                  if (res.ok) {
                    const updatedUser = await res.json();
                    onUpdateUser(updatedUser);
                    localStorage.setItem('user', JSON.stringify(updatedUser));
                    setProfileMsg('success:Name updated successfully!');
                  } else {
                    const errorData = await res.json();
                    setProfileMsg(`error:${errorData.message || 'Failed to update name'}`);
                  }
                } catch {
                  setProfileMsg('error:Network error');
                }
              }}
            >
              <input
                placeholder="New name..."
                value={profileForm.name}
                onChange={(e) => setProfileForm({ name: e.target.value })}
              />
              {profileMsg && (
                <p className={profileMsg.startsWith('success') ? 'success-msg' : 'error-msg'}>
                  {profileMsg.split(':')[1]}
                </p>
              )}
              <button className="btn-primary" type="submit">Save Changes</button>
            </form>
          </div>
        )}

        {/* ─── My Posts Tab ─── */}
        {tab === 'posts' && (
          <div className="panel">
            <h3 className="panel-title">Your Listings</h3>

            {/* Edit modal */}
            {editItem && (
              <div className="edit-modal">
                <div className="edit-modal-box">
                  <div className="modal-header">
                    <h4>Edit Item</h4>
                    <button className="close-btn" onClick={() => setEditItem(null)}>✕</button>
                  </div>
                  <form onSubmit={handleEditItem} className="simple-form">
                    <div className="form-group">
                      <label>Title</label>
                      <input
                        value={editForm.title}
                        onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label>Description</label>
                      <textarea
                        rows={3}
                        value={editForm.description}
                        onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label>Price (₹)</label>
                      <input
                        type="number"
                        value={editForm.price}
                        onChange={(e) => setEditForm({ ...editForm, price: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label>Category</label>
                      <select
                        value={editForm.category}
                        onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                      >
                        {CATEGORIES.map(cat => (
                          <option key={cat.id} value={cat.id}>{cat.label}</option>
                        ))}
                      </select>
                    </div>
                    {editMsg && (
                      <p className={editMsg.startsWith('success') ? 'success-msg' : 'error-msg'}>
                        {editMsg.split(':')[1]}
                      </p>
                    )}
                    <button className="btn-primary" type="submit">Update Item</button>
                  </form>
                </div>
              </div>
            )}

            {loadingItems ? (
              <div className="loading-list">
                {[...Array(3)].map((_, i) => <div key={i} className="list-skeleton" />)}
              </div>
            ) : myItems.length === 0 ? (
              <div className="empty-state">
                <span className="empty-icon">📭</span>
                <p>No listings yet</p>
                <small>Go to "Post Item" to add your first one!</small>
              </div>
            ) : (
              <div className="my-items-list">
                {myItems.map((item) => (
                  <div key={item.id} className="my-item-row">
                    <div className="my-item-info">
                      <span className="my-item-emoji">{getEmoji(item.title)}</span>
                      <div>
                        <p className="my-item-title">{item.title}</p>
                        <p className="my-item-desc">{item.description}</p>
                      </div>
                    </div>
                    <div className="my-item-right">
                      <span className="item-price">₹{item.price?.toLocaleString('en-IN')}</span>
                      <span className={`tag ${
                        item.status === 'SOLD' ? 'tag-sold' : 
                        item.status === 'PENDING' ? 'tag-pending' : 'tag-available'
                      }`}>
                        {item.status === 'PENDING' ? 'Pending Approval' : item.status}
                      </span>
                      <button className="edit-btn" onClick={() => openEdit(item)}>
                        ✏️ Edit
                      </button>
                      <button className="delete-btn" onClick={() => handleDeleteItem(item.id)}>
                        🗑️ Delete
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ─── Add Item Tab ─── */}
        {tab === 'add' && (
          <div className="panel">
            <h3 className="panel-title">Post a New Item</h3>
            <form onSubmit={handleAddItem} className="simple-form">
              <div className="form-group">
                <label>Title</label>
                <input
                  placeholder="e.g. MacBook Air, Physics Textbook..."
                  value={addForm.title}
                  onChange={(e) => setAddForm({ ...addForm, title: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea
                  rows={4}
                  placeholder="Condition, what's included, how to contact..."
                  value={addForm.description}
                  onChange={(e) => setAddForm({ ...addForm, description: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Price (₹)</label>
                <input
                  type="number"
                  min="0"
                  placeholder="0"
                  value={addForm.price}
                  onChange={(e) => setAddForm({ ...addForm, price: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Category</label>
                <select
                  value={addForm.category}
                  onChange={(e) => setAddForm({ ...addForm, category: e.target.value })}
                >
                  {CATEGORIES.map(cat => (
                    <option key={cat.id} value={cat.id}>{cat.label}</option>
                  ))}
                </select>
              </div>
              {addMsg && (
                <p className={addMsg.startsWith('success') ? 'success-msg' : 'error-msg'}>
                  {addMsg.split(':')[1]}
                </p>
              )}
              <button className="btn-primary" type="submit">Post It 🚀</button>
            </form>
          </div>
        )}
      </main>
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
  return '📦';
}
