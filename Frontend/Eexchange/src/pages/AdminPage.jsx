import React, { useState, useEffect } from 'react';
import './Admin.css';

export default function AdminPage({ user }) {
  const [pendingItems, setPendingItems] = useState([]);
  const [users, setUsers] = useState([]);
  const [allItems, setAllItems] = useState([]);
  const [activeTab, setActiveTab] = useState('items'); // 'items' | 'users' | 'all'
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState('');

  const token = () => localStorage.getItem('token');

  useEffect(() => {
    if (activeTab === 'items') fetchPending();
    else if (activeTab === 'users') fetchUsers();
    else fetchAllItems();
  }, [activeTab]);

  const fetchAllItems = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/items/all`);
      const data = await res.json();
      setAllItems(data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  const fetchPending = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/admin/items/pending`, {
        headers: { 'Authorization': `Bearer ${token()}` }
      });
      const data = await res.json();
      setPendingItems(data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/admin/users`, {
        headers: { 'Authorization': `Bearer ${token()}` }
      });
      const data = await res.json();
      setUsers(data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  const handleApprove = async (id) => {
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/admin/items/approve/${id}`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token()}` }
      });
      if (res.ok) {
        setPendingItems(prev => prev.filter(i => i.id !== id));
        setMsg('Item approved successfully!');
        setTimeout(() => setMsg(''), 3000);
      }
    } catch (e) { console.error(e); }
  };

  const handleDecline = async (id) => {
    if (!window.confirm('Are you sure you want to decline and delete this item?')) return;
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/admin/items/decline/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token()}` }
      });
      if (res.ok) {
        setPendingItems(prev => prev.filter(i => i.id !== id));
        setMsg('Item declined.');
        setTimeout(() => setMsg(''), 3000);
      }
    } catch (e) { console.error(e); }
  };

  const handleToggleBlock = async (userId) => {
    try {
      const res = await fetch(`${import.meta.env.VITE_API_URL}/admin/users/toggle-block/${userId}`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token()}` }
      });
      if (res.ok) {
        const updatedUser = await res.json();
        setUsers(prev => prev.map(u => u.id === userId ? updatedUser : u));
      }
    } catch (e) { console.error(e); }
  };

  return (
    <div className="admin-page">
      <div className="admin-header">
        <h1>Admin Control Panel 🛠️</h1>
        <p>Manage campus listings and user access</p>
      </div>

      <div className="admin-tabs">
        <button 
          className={activeTab === 'items' ? 'active' : ''} 
          onClick={() => setActiveTab('items')}
        >
          Pending Items ({pendingItems.length})
        </button>
        <button 
          className={activeTab === 'users' ? 'active' : ''} 
          onClick={() => setActiveTab('users')}
        >
          User Management
        </button>
        <button 
          className={activeTab === 'all' ? 'active' : ''} 
          onClick={() => setActiveTab('all')}
        >
          All Listings
        </button>
      </div>

      {msg && <div className="admin-msg">{msg}</div>}

      <div className="admin-content">
        {loading ? (
          <div className="admin-loading">Loading...</div>
        ) : activeTab === 'items' ? (
          <div className="pending-list">
            {pendingItems.length === 0 ? (
              <p className="no-data">No items waiting for approval.</p>
            ) : (
              pendingItems.map(item => (
                <div key={item.id} className="admin-item-card">
                  <div className="item-info">
                    <h3>{item.title}</h3>
                    <p className="item-meta">By: {item.userEmail} | ₹{item.price}</p>
                    <p className="item-desc">{item.description}</p>
                  </div>
                  <div className="item-actions">
                    <button className="btn-approve" onClick={() => handleApprove(item.id)}>Approve ✅</button>
                    <button className="btn-decline" onClick={() => handleDecline(item.id)}>Decline ❌</button>
                  </div>
                </div>
              ))
            )}
          </div>
        ) : activeTab === 'users' ? (
          <div className="user-table-wrapper">
            <table className="user-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {users.map(u => (
                  <tr key={u.id}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td><span className={`role-badge ${u.role}`}>{u.role?.replace('ROLE_', '')}</span></td>
                    <td>
                      <span className={`status-badge ${u.blocked ? 'blocked' : 'active'}`}>
                        {u.blocked ? 'Blocked' : 'Active'}
                      </span>
                    </td>
                    <td>
                      {u.email !== user.email && (
                        <button 
                          className={`btn-block ${u.blocked ? 'unblock' : ''}`}
                          onClick={() => handleToggleBlock(u.id)}
                        >
                          {u.blocked ? 'Unblock' : 'Block'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="all-items-admin">
            {allItems.map(item => (
              <div key={item.id} className="admin-item-card">
                <div className="item-info">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span className={`status-badge ${item.status.toLowerCase()}`}>{item.status}</span>
                    <h3>{item.title}</h3>
                  </div>
                  <p className="item-meta">Seller: {item.userEmail} | ₹{item.price}</p>
                </div>
                <div className="item-actions">
                  <button className="btn-decline" onClick={() => {
                    handleDecline(item.id);
                    setAllItems(prev => prev.filter(i => i.id !== item.id));
                  }}>Delete 🗑️</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
