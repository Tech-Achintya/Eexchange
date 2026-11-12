import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  BadgeCheck,
  Camera,
  Handshake,
  Heart,
  Search,
  ShieldCheck,
  Star,
  Upload,
  Wallet,
} from 'lucide-react';
import { catalogApi } from '../lib/api';
import { FALLBACK_CATEGORY, getCategoryStyle } from '../lib/constants';
import './LandingPage.css';

const fallbackCategories = [
  { id: 1, name: 'Books & Notes', slug: 'books-notes' },
  { id: 2, name: 'Mobiles & Electronics', slug: 'mobiles-electronics' },
  { id: 3, name: 'Bicycles & Vehicles', slug: 'bicycles-vehicles' },
  { id: 4, name: 'Hostel & Furniture', slug: 'hostel-furniture' },
  { id: 5, name: 'Clothing & Fashion', slug: 'clothing-fashion' },
  { id: 6, name: 'Sports & Fitness', slug: 'sports-fitness' },
  { id: 7, name: 'Stationery & Supplies', slug: 'stationery-supplies' },
  { id: 8, name: 'Others', slug: 'others' },
];

const marqueeWords = [
  'textbooks', 'cycles', 'laptops', 'hostel decor', 'notes', 'sneakers',
  'earphones', 'lab coats', 'badminton rackets', 'mini fridges', 'desk lamps',
  'graduation fits',
];

export default function LandingPage() {
  const [categories, setCategories] = useState(fallbackCategories);

  useEffect(() => {
    let active = true;
    catalogApi
      .categories()
      .then((response) => {
        if (active && Array.isArray(response?.data) && response.data.length) {
          setCategories(response.data);
        }
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="landing">
      <section className="hero">
        <div className="blob blob-a" />
        <div className="blob blob-b" />
        <div className="blob blob-c" />
        <div className="container hero-grid">
          <motion.div
            className="hero-copy"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          >
            <span className="eyebrow">
              Student-only · Campus-verified
            </span>

            <h1>
              Your campus.
              <br />
              Your stuff.
              <br />
              <span className="highlight">
                Your price.
                <svg viewBox="0 0 220 18" preserveAspectRatio="none" aria-hidden="true">
                  <path d="M4 12 C 55 2, 140 2, 216 9" />
                </svg>
              </span>
            </h1>

            <p className="hero-lead">
              The marketplace where students buy, sell and pass on campus finds.
              Textbooks, cycles, room decor — all from people you might sit next to
              in class.
            </p>

            <div className="hero-cta">
              <Link to="/auth?mode=signup" className="btn btn-primary btn-lg">
                Join your campus <ArrowRight size={18} />
              </Link>
              <Link to="/auth" className="btn btn-light btn-lg">
                I already have an account
              </Link>
            </div>

            <div className="hero-trust">
              <div className="trust-avatars" aria-hidden="true">
                <span style={{ background: 'var(--grad-lav)' }}>A</span>
                <span style={{ background: 'var(--grad-mint)' }}>R</span>
                <span style={{ background: 'var(--grad-sky)' }}>S</span>
                <span style={{ background: 'var(--grad-sun)', color: '#7a5600' }}>K</span>
              </div>
              <div>
                <div className="trust-stars">
                  {Array.from({ length: 5 }).map((_, index) => (
                    <Star key={index} size={13} fill="currentColor" />
                  ))}
                </div>
                <small>Made for verified college students</small>
              </div>
            </div>
          </motion.div>

          <motion.div
            className="hero-visual"
            initial={{ opacity: 0, scale: 0.94, rotate: 2 }}
            animate={{ opacity: 1, scale: 1, rotate: 0 }}
            transition={{ duration: 0.7, delay: 0.12, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="hero-card main">
              <div className="hero-card-media">
                <span>💻</span>
                <span className="hero-cond">Like new</span>
              </div>
              <div className="hero-card-body">
                <div className="hero-card-meta">
                  <span>Electronics</span>
                  <span>2h ago</span>
                </div>
                <h3>MacBook Air M1 · 8/256</h3>
                <div className="hero-card-foot">
                  <strong>₹42,000</strong>
                  <span className="mini-seller">
                    <span className="seller-dot" /> Aditi · 2nd yr
                  </span>
                </div>
              </div>
            </div>

            <motion.div className="float-tag tag-price" animate={{ y: [0, -11, 0] }} transition={{ duration: 5, repeat: Infinity }}>
              <Wallet size={16} /> Student price
            </motion.div>
            <motion.div className="float-tag tag-sold" animate={{ y: [0, 10, 0] }} transition={{ duration: 6, repeat: Infinity, delay: 0.6 }}>
              <Handshake size={16} /> Sold in 2 hrs
            </motion.div>
            <motion.div className="float-tag tag-safe" animate={{ y: [0, -8, 0] }} transition={{ duration: 5.4, repeat: Infinity, delay: 1.1 }}>
              <ShieldCheck size={16} /> AI-moderated
            </motion.div>
            <div className="float-sticker" aria-hidden="true">sunshine only ☀️</div>
          </motion.div>
        </div>
      </section>

      <div className="marquee" aria-hidden="true">
        <div className="marquee-track">
          {[...marqueeWords, ...marqueeWords].map((word, index) => (
            <span key={`${word}-${index}`}>
              {word} <span style={{ opacity: 0.5, margin: '0 4px' }}>•</span>
            </span>
          ))}
        </div>
      </div>

      <section className="section" id="why">
        <div className="container">
          <div className="section-head">
            <span className="eyebrow">Why students love it</span>
            <h2>Less “is this still available?”<br />more “see you at the library”.</h2>
            <p>
              EExchange keeps campus trading simple, safe and a little bit fun.
              No shipping, no strangers, no drama.
            </p>
          </div>

          <div className="feature-grid">
            <motion.div className="feature-card" whileHover={{ y: -6 }} transition={{ type: 'spring', stiffness: 300 }}>
              <div className="feature-icon" style={{ background: '#ffe9e6', color: 'var(--coral-deep)' }}>
                <BadgeCheck size={26} />
              </div>
              <h3>Only your college</h3>
              <p>
                Sign up with your institutional email and browse finds from your own
                campus. Everyone is verified, so meetups stay close and comfortable.
              </p>
              <span className="feature-sticker">verified people only</span>
            </motion.div>

            <motion.div className="feature-card" whileHover={{ y: -6 }} transition={{ type: 'spring', stiffness: 300 }}>
              <div className="feature-icon" style={{ background: 'var(--mint-soft)', color: '#0d7f75' }}>
                <Camera size={26} />
              </div>
              <h3>Show it, don’t describe it</h3>
              <p>
                Add up to 5 photos to every listing. Honest condition tags help you
                know exactly what you’re getting before you message.
              </p>
              <span className="feature-sticker">5 pics per listing</span>
            </motion.div>

            <motion.div className="feature-card" whileHover={{ y: -6 }} transition={{ type: 'spring', stiffness: 300 }}>
              <div className="feature-icon" style={{ background: 'var(--sun-soft)', color: '#a27300' }}>
                <ShieldCheck size={26} />
              </div>
              <h3>We keep it friendly</h3>
              <p>
                Every new listing gets a quick AI review, and you can report anything
                that feels off. Good campus vibes, protected.
              </p>
              <span className="feature-sticker">zero weird stuff</span>
            </motion.div>
          </div>
        </div>
      </section>

      <section className="section categories-showcase" id="categories">
        <div className="container">
          <div className="section-head row">
            <div>
              <span className="eyebrow">Find your thing</span>
              <h2>Everything a campus actually needs.</h2>
            </div>
            <Link to="/auth?mode=signup" className="btn btn-mint">
              Explore all finds <ArrowRight size={17} />
            </Link>
          </div>

          <div className="showcase-grid">
            {categories.map((category, index) => {
              const style = getCategoryStyle(category.slug) || FALLBACK_CATEGORY;
              const Icon = style.icon;
              return (
                <motion.div
                  key={category.id}
                  initial={{ opacity: 0, y: 18 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, amount: 0.3 }}
                  transition={{ delay: Math.min(index * 0.04, 0.3), duration: 0.4 }}
                >
                  <Link
                    to="/auth?mode=signup"
                    className="showcase-card"
                    style={{ background: style.bg }}
                  >
                    <span className="showcase-icon" style={{ color: style.fg }}>
                      <Icon size={24} />
                    </span>
                    <strong>{category.name}</strong>
                    <span className="showcase-emoji">{style.emoji}</span>
                  </Link>
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>

      <section className="section how" id="how">
        <div className="container how-grid">
          <div className="how-copy">
            <span className="eyebrow">How it works</span>
            <h2>Three steps. One very happy wallet.</h2>
            <p>You could be clearing out your hostel room before the next chai break.</p>

            <div className="steps">
              <div className="step">
                <span className="step-num">01</span>
                <div>
                  <h4><Upload size={17} /> Post it in a minute</h4>
                  <p>Snap a few photos, add a price and hit publish. We handle the boring bits.</p>
                </div>
              </div>
              <div className="step">
                <span className="step-num">02</span>
                <div>
                  <h4><Search size={17} /> Someone on campus spots it</h4>
                  <p>Only verified students from your college see your listing.</p>
                </div>
              </div>
              <div className="step">
                <span className="step-num">03</span>
                <div>
                  <h4><Handshake size={17} /> Meet, hand it over, get paid</h4>
                  <p>Interested students send you a message with their contact info.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="how-visual">
            <div className="phone-frame">
              <div className="phone-screen">
                <div className="phone-top">
                  <span className="phone-dot" />
                  <div>
                    <strong>Campus feed</strong>
                    <small>Your college · live</small>
                  </div>
                  <Heart size={17} fill="var(--coral)" color="var(--coral)" />
                </div>
                <div className="mini-post" style={{ background: '#fff0cd' }}>
                  <span>📚</span>
                  <div>
                    <strong>Semester 3 notes set</strong>
                    <small>₹250 · Like new</small>
                  </div>
                </div>
                <div className="mini-post" style={{ background: '#e3efff' }}>
                  <span>🎧</span>
                  <div>
                    <strong>Sony headphones</strong>
                    <small>₹1,900 · Good</small>
                  </div>
                </div>
                <div className="mini-post" style={{ background: '#def7e9' }}>
                  <span>🚲</span>
                  <div>
                    <strong>Hybrid cycle</strong>
                    <small>₹4,200 · Well loved</small>
                  </div>
                </div>
                <div className="phone-cta">Sell something ✨</div>
              </div>
            </div>
            <div className="how-badge badge-1">🎉 Sold!</div>
            <div className="how-badge badge-2">💬 New interest</div>
          </div>
        </div>
      </section>

      <section className="final-cta">
        <div className="container">
          <div className="cta-card">
            <div className="cta-doodle doodle-1">📦</div>
            <div className="cta-doodle doodle-2">💸</div>
            <div className="cta-doodle doodle-3">🌱</div>
            <span className="eyebrow">Ready when you are</span>
            <h2>Your next great campus find<br />is one signup away.</h2>
            <p>
              Join with your college email, verify once, and start trading with
              students from your own campus.
            </p>
            <Link to="/auth?mode=signup" className="btn btn-lg cta-btn">
              Create my free account <ArrowRight size={18} />
            </Link>
            <small>Free forever · No listing fees · Only your campus</small>
          </div>
        </div>
      </section>
    </div>
  );
}
