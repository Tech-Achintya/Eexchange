import { motion } from 'framer-motion';
import { BadgeCheck, Heart, ShieldCheck, Wallet } from 'lucide-react';
import './CampusExchangeAnimations.css';

export default function CampusExchangeAuthArt() {
  return (
    <aside className="auth-art-pleasant">
      <div className="auth-art-dots" />

      <div className="auth-art-header">
        <span className="auth-badge">
          campus marketplace
        </span>
        <h2>
          Good stuff, <br />
          <span className="highlight-text">passed forward.</span>
        </h2>
        <p className="auth-art-sub">
          Exchange books, gadgets, notes, and cycles with fellow students right on your campus.
        </p>
      </div>

      <div className="campus-exchange-stage" aria-label="College students exchanging study notes on campus">
        {/* Floating conversational bubbles */}
        <div className="campus-bubble campus-bubble-boy">
          <span>📚</span>
          <span>&ldquo;Here are the notes!&rdquo;</span>
        </div>

        <div className="campus-bubble campus-bubble-girl">
          <span>🙌</span>
          <span>&ldquo;Thank you so much!&rdquo;</span>
        </div>

        <div className="campus-bubble campus-bubble-center">
          <Heart size={13} fill="#ff6b6b" color="#ff6b6b" />
          <span>Handover on Campus · ₹0 fees</span>
        </div>

        {/* High-fidelity Vector Scene */}
        <svg
          className="campus-exchange-svg"
          viewBox="0 0 500 340"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <defs>
            <linearGradient id="sunGlow" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#ffebb3" stopOpacity="0.8" />
              <stop offset="100%" stopColor="#ffd269" stopOpacity="0.1" />
            </linearGradient>

            <linearGradient id="grassGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#ddf8ec" />
              <stop offset="100%" stopColor="#c5f0de" />
            </linearGradient>

            <linearGradient id="pathGrad" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#fdf0e2" />
              <stop offset="50%" stopColor="#fbf3ea" />
              <stop offset="100%" stopColor="#faebd7" />
            </linearGradient>

            <linearGradient id="boyHoodie" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#ff8a65" />
              <stop offset="100%" stopColor="#ff6b6b" />
            </linearGradient>

            <linearGradient id="boyPants" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3d3b5e" />
              <stop offset="100%" stopColor="#2c2844" />
            </linearGradient>

            <linearGradient id="girlSweater" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#4de2d0" />
              <stop offset="100%" stopColor="#22bba9" />
            </linearGradient>

            <linearGradient id="girlSkirt" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#9687f8" />
              <stop offset="100%" stopColor="#7c6af3" />
            </linearGradient>

            <linearGradient id="bookGrad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#ffc93c" />
              <stop offset="100%" stopColor="#ff9f1c" />
            </linearGradient>

            <linearGradient id="foliageGrad1" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#6ee7b7" />
              <stop offset="100%" stopColor="#27c2b3" />
            </linearGradient>
            <linearGradient id="foliageGrad2" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#34d399" />
              <stop offset="100%" stopColor="#10b981" />
            </linearGradient>

            <filter id="softGlow" x="-25%" y="-25%" width="150%" height="150%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
          </defs>

          {/* BACKGROUND: Soft Sun & Clouds */}
          <circle cx="250" cy="110" r="95" fill="url(#sunGlow)" />

          <g className="anim-cloud-slow" opacity="0.75">
            <path
              d="M 60 70 C 60 62, 70 56, 80 59 C 85 52, 97 53, 102 61 C 109 59, 117 64, 117 72 C 117 78, 110 82, 100 82 L 68 82 C 62 82, 60 76, 60 70 Z"
              fill="#ffffff"
            />
          </g>
          <g className="anim-cloud-gentle" opacity="0.7">
            <path
              d="M 370 65 C 370 58, 378 54, 386 56 C 391 50, 401 51, 405 57 C 411 56, 417 60, 417 67 C 417 73, 411 76, 403 76 L 376 76 C 371 76, 370 71, 370 65 Z"
              fill="#ffffff"
            />
          </g>

          {/* Distant Campus Library Tower silhouette */}
          <g opacity="0.3">
            <rect x="232" y="105" width="36" height="85" rx="3" fill="#a499d6" />
            <polygon points="232,105 250,78 268,105" fill="#8f83c8" />
            <circle cx="250" cy="98" r="5" fill="#fff" />
            <circle cx="250" cy="98" r="1.5" fill="#7567b5" />
            <rect x="238" y="120" width="8" height="14" rx="4" fill="#f4f1ff" />
            <rect x="254" y="120" width="8" height="14" rx="4" fill="#f4f1ff" />
            <rect x="238" y="145" width="8" height="14" rx="4" fill="#f4f1ff" />
            <rect x="254" y="145" width="8" height="14" rx="4" fill="#f4f1ff" />
            <rect x="185" y="135" width="47" height="55" rx="3" fill="#b9b0e2" />
            <rect x="268" y="135" width="47" height="55" rx="3" fill="#b9b0e2" />
          </g>

          {/* Campus Lawn & Pathway */}
          <path
            d="M 0 220 Q 140 195, 250 205 T 500 215 L 500 340 L 0 340 Z"
            fill="url(#grassGrad)"
          />
          <path
            d="M 60 340 C 130 275, 190 250, 250 248 C 310 246, 370 270, 440 340 Z"
            fill="url(#pathGrad)"
          />

          {/* Left Campus Tree (Far left corner) */}
          <g transform="translate(10, 80)">
            <path
              d="M 28 200 C 26 150, 32 110, 37 80 C 40 70, 44 80, 46 110 C 48 145, 47 185, 49 200 Z"
              fill="#9a7b63"
            />
            <g className="anim-leaves">
              <circle cx="36" cy="65" r="32" fill="url(#foliageGrad1)" />
              <circle cx="60" cy="58" r="26" fill="url(#foliageGrad2)" />
              <circle cx="42" cy="38" r="28" fill="url(#foliageGrad1)" />
              <circle cx="20" cy="54" r="20" fill="url(#foliageGrad2)" opacity="0.9" />
              <circle cx="34" cy="52" r="2.8" fill="#ff7f7f" />
              <circle cx="52" cy="42" r="2.5" fill="#ffc93c" />
            </g>
          </g>

          {/* Parked Campus Bicycle in left background */}
          <g transform="translate(48, 222) scale(0.62)" opacity="0.88">
            <circle cx="20" cy="40" r="17" stroke="#565073" strokeWidth="2.5" fill="none" />
            <circle cx="20" cy="40" r="2.5" fill="#565073" />
            <circle cx="72" cy="40" r="17" stroke="#565073" strokeWidth="2.5" fill="none" />
            <circle cx="72" cy="40" r="2.5" fill="#565073" />
            <path
              d="M 20 40 L 42 40 L 62 22 L 36 22 Z M 42 40 L 50 16 L 72 40 M 36 22 L 30 14"
              stroke="#27c2b3"
              strokeWidth="2.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
            <path d="M 58 13 L 64 16 L 69 16" stroke="#565073" strokeWidth="2.5" strokeLinecap="round" />
            <path d="M 25 14 L 35 14" stroke="#ff6b6b" strokeWidth="3" strokeLinecap="round" />
            <rect x="65" y="16" width="10" height="8" rx="2" fill="#ffe28a" stroke="#cca43b" strokeWidth="1" />
          </g>

          {/* ===================================================
              BOY CHARACTER (Firmly on LEFT: X ≈ 105, Y ≈ 135)
             =================================================== */}
          <g transform="translate(105, 135)">
            <motion.g
              animate={{ y: [0, -4, 0] }}
              transition={{ duration: 3.6, repeat: Infinity, ease: 'easeInOut' }}
            >
              {/* Shadow under feet */}
              <ellipse cx="40" cy="142" rx="24" ry="5.5" fill="#1b1735" opacity="0.13" />

              {/* Legs & Shoes */}
              <path d="M 30 84 L 27 128" stroke="url(#boyPants)" strokeWidth="11" strokeLinecap="round" />
              <ellipse cx="23" cy="132" rx="9.5" ry="4.5" fill="#ffffff" stroke="#e0d6cb" strokeWidth="1.5" />
              <path d="M 18 132 C 19 129, 26 129, 30 132" stroke="#27c2b3" strokeWidth="2" strokeLinecap="round" />

              <path d="M 49 84 L 53 128" stroke="url(#boyPants)" strokeWidth="11" strokeLinecap="round" />
              <ellipse cx="57" cy="132" rx="9.5" ry="4.5" fill="#ffffff" stroke="#e0d6cb" strokeWidth="1.5" />
              <path d="M 52 132 C 53 129, 60 129, 64 132" stroke="#27c2b3" strokeWidth="2" strokeLinecap="round" />

              {/* Torso / Coral Hoodie */}
              <path
                d="M 22 36 C 22 32, 32 30, 42 30 C 52 30, 60 32, 60 36 L 62 86 C 62 88, 56 90, 42 90 C 26 90, 20 88, 20 86 Z"
                fill="url(#boyHoodie)"
              />
              <path d="M 35 30 Q 42 40, 49 30" fill="#fffaf2" />
              <path d="M 38 38 L 38 48" stroke="#ffffff" strokeWidth="1.4" strokeLinecap="round" />
              <path d="M 45 38 L 45 46" stroke="#ffffff" strokeWidth="1.4" strokeLinecap="round" />
              <path
                d="M 28 64 Q 42 60, 54 64 L 52 78 Q 42 80, 30 78 Z"
                fill="#f4526e"
                opacity="0.35"
              />

              {/* Left Arm (Relaxed) */}
              <path
                d="M 22 38 Q 12 58, 26 68"
                stroke="url(#boyHoodie)"
                strokeWidth="9"
                strokeLinecap="round"
                fill="none"
              />

              {/* Head & Face */}
              <rect x="38" y="22" width="8" height="9" fill="#f8cdb2" rx="2.5" />
              <ellipse cx="42" cy="17" rx="13" ry="14" fill="#fcdac2" />
              <path
                d="M 29 15 C 29 5, 40 2, 53 5 C 56 9, 55 15, 55 17 C 51 12, 45 12, 38 14 C 34 16, 31 17, 29 15 Z"
                fill="#4a3728"
              />
              <circle cx="30" cy="18" r="3" fill="#f8cdb2" />
              <path d="M 42 15 Q 45 12, 48 15" stroke="#241f3d" strokeWidth="1.7" strokeLinecap="round" fill="none" />
              <circle cx="45" cy="20" r="2.8" fill="#ff8fa3" opacity="0.5" />
              <path d="M 42 21 Q 45 25, 49 22" stroke="#241f3d" strokeWidth="1.5" strokeLinecap="round" fill="none" />

              {/* Extended Right Arm (Giving notes forward) */}
              <motion.g
                animate={{ x: [0, 4, 0] }}
                transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
              >
                <path
                  d="M 54 40 Q 74 44, 95 50"
                  stroke="url(#boyHoodie)"
                  strokeWidth="9"
                  strokeLinecap="round"
                  fill="none"
                />
                <circle cx="97" cy="50" r="4.8" fill="#fcdac2" />
              </motion.g>
            </motion.g>
          </g>

          {/* ===================================================
              CENTRAL EXCHANGE ITEM: Notes Bundle & Arc
              Firmly at X ≈ 232, Y ≈ 185
             =================================================== */}
          <g transform="translate(232, 182)">
            <motion.g
              animate={{ y: [0, -5, 0], scale: [1, 1.04, 1] }}
              transition={{ duration: 2.7, repeat: Infinity, ease: 'easeInOut' }}
            >
              <circle cx="18" cy="14" r="22" fill="#ffd269" opacity="0.32" filter="url(#softGlow)" />
              <g transform="rotate(-6 18 14)">
                <rect x="2" y="3" width="32" height="22" rx="3.5" fill="#d97706" opacity="0.3" />
                <rect x="4" y="2" width="30" height="22" rx="3" fill="#fffbf2" />
                <line x1="8" y1="7" x2="22" y2="7" stroke="#e6dcd0" strokeWidth="1.3" strokeLinecap="round" />
                <line x1="8" y1="12" x2="26" y2="12" stroke="#e6dcd0" strokeWidth="1.3" strokeLinecap="round" />
                <rect x="0" y="0" width="32" height="22" rx="3.5" fill="url(#bookGrad)" />
                <path d="M 20 0 L 20 12 L 23 9 L 26 12 L 26 0 Z" fill="#ff6b6b" />
                <rect x="5" y="5" width="11" height="11" rx="2" fill="#ffffff" opacity="0.9" />
                <path d="M 7 8 L 13 8 M 7 12 L 12 12" stroke="#ff9f1c" strokeWidth="1.3" strokeLinecap="round" />
              </g>
            </motion.g>
          </g>

          {/* Smooth dotted transfer arc */}
          <path
            className="anim-exchange-dash"
            d="M 205 190 Q 250 156, 295 190"
            stroke="#8b7cf6"
            strokeWidth="2.4"
            strokeLinecap="round"
            fill="none"
            opacity="0.85"
          />

          {/* Floating subtle campus dots/hearts (No 4-point Gemini star) */}
          <g className="anim-sparkle" transform="translate(244, 150)">
            <circle cx="0" cy="0" r="3" fill="#ffc93c" />
          </g>
          <g className="anim-sparkle-2" transform="translate(270, 168)">
            <circle cx="0" cy="0" r="2.5" fill="#ff6b6b" />
          </g>
          <g className="anim-sparkle-3" transform="translate(225, 168)">
            <circle cx="0" cy="0" r="2.2" fill="#27c2b3" />
          </g>

          {/* ===================================================
              GIRL CHARACTER (Firmly on RIGHT: X ≈ 340, Y ≈ 135)
             =================================================== */}
          <g transform="translate(340, 135)">
            <motion.g
              animate={{ y: [0, -4, 0] }}
              transition={{ duration: 3.9, repeat: Infinity, ease: 'easeInOut', delay: 0.3 }}
            >
              {/* Shadow under feet */}
              <ellipse cx="40" cy="142" rx="24" ry="5.5" fill="#1b1735" opacity="0.13" />

              {/* Backpack behind her */}
              <rect x="48" y="38" width="15" height="32" rx="6" fill="#8b7cf6" opacity="0.85" />
              <rect x="52" y="47" width="9" height="13" rx="3.5" fill="#a499ff" />

              {/* Legs & Shoes */}
              <path d="M 33 88 L 30 128" stroke="#48426d" strokeWidth="9.5" strokeLinecap="round" />
              <ellipse cx="25" cy="132" rx="8.5" ry="4.5" fill="#ffffff" stroke="#e0d6cb" strokeWidth="1.5" />
              <path d="M 20 132 C 22 129, 27 129, 31 132" stroke="#ff6b6b" strokeWidth="2" strokeLinecap="round" />

              <path d="M 48 88 L 50 128" stroke="#48426d" strokeWidth="9.5" strokeLinecap="round" />
              <ellipse cx="54" cy="132" rx="8.5" ry="4.5" fill="#ffffff" stroke="#e0d6cb" strokeWidth="1.5" />
              <path d="M 49 132 C 51 129, 56 129, 60 132" stroke="#ff6b6b" strokeWidth="2" strokeLinecap="round" />

              {/* Pleated Skirt */}
              <path
                d="M 28 72 L 54 72 L 60 90 C 49 92, 34 92, 22 90 Z"
                fill="url(#girlSkirt)"
              />
              <line x1="35" y1="72" x2="33" y2="90" stroke="#6856e0" strokeWidth="1" />
              <line x1="45" y1="72" x2="47" y2="90" stroke="#6856e0" strokeWidth="1" />

              {/* Torso / Mint Sweater */}
              <path
                d="M 26 36 C 26 32, 35 30, 42 30 C 50 30, 56 32, 56 36 L 54 74 C 47 76, 34 76, 28 74 Z"
                fill="url(#girlSweater)"
              />
              <path d="M 35 30 Q 41 36, 47 30" fill="#ffffff" opacity="0.9" />
              <circle cx="41" cy="44" r="1.6" fill="#138a7c" />
              <circle cx="41" cy="53" r="1.6" fill="#138a7c" />

              {/* Head & Hair */}
              <rect x="37" y="22" width="8" height="9" fill="#f8cdb2" rx="2.5" />
              <ellipse cx="41" cy="17" rx="12" ry="13" fill="#fcdac2" />
              <path
                d="M 26 19 C 24 4, 58 2, 56 19 C 58 26, 55 33, 52 39 C 49 34, 50 26, 48 22 C 41 13, 33 14, 30 22 C 28 26, 28 33, 24 37 C 24 31, 25 24, 26 19 Z"
                fill="#5c3826"
              />
              <path
                d="M 31 12 C 34 7, 46 6, 50 12"
                stroke="#8b7cf6"
                strokeWidth="3"
                strokeLinecap="round"
                fill="none"
              />
              <circle cx="49" cy="18" r="2.8" fill="#f8cdb2" />
              <path d="M 33 15 Q 36 12, 39 15" stroke="#241f3d" strokeWidth="1.7" strokeLinecap="round" fill="none" />
              <circle cx="35" cy="19" r="2.8" fill="#ff8fa3" opacity="0.5" />
              <path d="M 33 21 Q 37 25, 40 21" stroke="#241f3d" strokeWidth="1.5" strokeLinecap="round" fill="none" />

              {/* Reaching Arms (Eagerly receiving notes from left) */}
              <motion.g
                animate={{ x: [0, -4, 0] }}
                transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
              >
                <path
                  d="M 35 40 Q 15 45, -8 48"
                  stroke="url(#girlSweater)"
                  strokeWidth="8.5"
                  strokeLinecap="round"
                  fill="none"
                />
                <circle cx="-10" cy="49" r="4.5" fill="#fcdac2" />

                <path
                  d="M 28 44 Q 10 52, -18 52"
                  stroke="url(#girlSweater)"
                  strokeWidth="8.5"
                  strokeLinecap="round"
                  fill="none"
                />
                <circle cx="-20" cy="52" r="4.5" fill="#fcdac2" />
              </motion.g>
            </motion.g>
          </g>

          {/* Foreground Lawn Daisies */}
          <circle cx="130" cy="310" r="2.8" fill="#ffc93c" />
          <circle cx="135" cy="312" r="2.2" fill="#ff6b6b" />
          <circle cx="370" cy="308" r="2.8" fill="#ff8fb1" />
          <circle cx="400" cy="320" r="3" fill="#ffc93c" />
          <circle cx="430" cy="312" r="2.5" fill="#8b7cf6" />
        </svg>
      </div>

      {/* Trust perks list */}
      <ul className="auth-pleasant-perks">
        <li>
          <BadgeCheck size={16} /> College-email verified
        </li>
        <li>
          <ShieldCheck size={16} /> AI-moderated deals
        </li>
        <li>
          <Wallet size={16} /> 100% free peer exchange
        </li>
      </ul>
    </aside>
  );
}
