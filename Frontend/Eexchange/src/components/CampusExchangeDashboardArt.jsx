import { motion } from 'framer-motion';
import { CheckCircle2, Heart } from 'lucide-react';
import './CampusExchangeAnimations.css';

export default function CampusExchangeDashboardArt({ collegeName = 'Campus Quad', verified = true }) {
  return (
    <div className="dashboard-exchange-hero" aria-label="Campus exchange students sharing items">
      {/* Floating Status Pills */}
      <div className="dashboard-swap-pill">
        <span className="badge-dot" />
        <span>Direct Campus Swap</span>
      </div>

      <div className="dashboard-campus-badge">
        <span>🎓</span>
        <span>{collegeName}</span>
        {verified && <CheckCircle2 size={12} color="#1fb96b" />}
      </div>

      {/* Vector Campus Exchange Scene */}
      <svg
        className="dashboard-exchange-svg"
        viewBox="0 0 440 230"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <radialGradient id="dashSun" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#ffe9b3" stopOpacity="0.75" />
            <stop offset="100%" stopColor="#fff3d6" stopOpacity="0" />
          </radialGradient>

          <linearGradient id="dashLawn1" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#def6ed" />
            <stop offset="100%" stopColor="#c5f0e1" />
          </linearGradient>

          <linearGradient id="dashLawn2" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#fdf0e2" />
            <stop offset="100%" stopColor="#f7e4ce" />
          </linearGradient>

          <linearGradient id="dashGirlJacket" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ff9fae" />
            <stop offset="100%" stopColor="#ff6b8b" />
          </linearGradient>

          <linearGradient id="dashBoyHoodie" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#5ed0fa" />
            <stop offset="100%" stopColor="#3b96f5" />
          </linearGradient>

          <linearGradient id="dashItemGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#ffc93c" />
            <stop offset="100%" stopColor="#ff8a5b" />
          </linearGradient>

          <filter id="dashGlow" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="3.5" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        {/* Ambient Sun Glow & Campus Cloud */}
        <circle cx="220" cy="75" r="80" fill="url(#dashSun)" />

        <g className="anim-cloud-slow" opacity="0.65">
          <path
            d="M 45 42 C 45 36, 53 32, 63 34 C 67 28, 77 28, 83 35 C 89 34, 97 38, 97 44 C 97 50, 91 53, 83 53 L 53 53 C 47 53, 45 48, 45 42 Z"
            fill="#ffffff"
          />
        </g>
        <g className="anim-cloud-gentle" opacity="0.6">
          <path
            d="M 330 35 C 330 30, 338 26, 346 28 C 350 23, 360 23, 364 29 C 370 28, 378 32, 378 37 C 378 42, 372 45, 364 45 L 338 45 C 332 45, 330 40, 330 35 Z"
            fill="#ffffff"
          />
        </g>

        {/* Distant campus archway silhouette */}
        <g opacity="0.28">
          <rect x="202" y="80" width="36" height="55" rx="2" fill="#9e91d1" />
          <path d="M 202 100 C 202 86, 238 86, 238 100 L 238 135 L 202 135 Z" fill="#fdfaf6" />
          <circle cx="165" cy="108" r="16" fill="#a4e2cc" />
          <circle cx="275" cy="110" r="15" fill="#a4e2cc" />
        </g>

        {/* Campus Hills & Pathway */}
        <path
          d="M 0 150 Q 120 130, 220 142 T 440 148 L 440 230 L 0 230 Z"
          fill="url(#dashLawn1)"
        />
        <path
          d="M 60 230 C 130 185, 180 175, 220 175 C 260 175, 310 185, 380 230 Z"
          fill="url(#dashLawn2)"
        />

        {/* Campus bench on left */}
        <g transform="translate(35, 155)" opacity="0.82">
          <rect x="0" y="14" width="34" height="3.5" rx="1.5" fill="#a38265" />
          <rect x="0" y="20" width="34" height="3.5" rx="1.5" fill="#a38265" />
          <line x1="5" y1="18" x2="3" y2="30" stroke="#565073" strokeWidth="2" strokeLinecap="round" />
          <line x1="29" y1="18" x2="31" y2="30" stroke="#565073" strokeWidth="2" strokeLinecap="round" />
          <rect x="13" y="7" width="6" height="7" rx="1.2" fill="#ffffff" stroke="#e4d6c8" strokeWidth="0.9" />
          <path d="M 19 9 Q 22 10, 19 12" stroke="#e4d6c8" strokeWidth="0.9" fill="none" />
        </g>

        {/* ===================================================
            GIRL (Firmly on LEFT: X ≈ 85, Y ≈ 78)
           =================================================== */}
        <g transform="translate(85, 78)">
          <motion.g
            animate={{ y: [0, -3.5, 0] }}
            transition={{ duration: 3.5, repeat: Infinity, ease: 'easeInOut' }}
          >
            {/* Ground shadow */}
            <ellipse cx="32" cy="116" rx="20" ry="4.5" fill="#1b1735" opacity="0.13" />

            {/* Legs & Sneakers */}
            <path d="M 24 72 L 21 106" stroke="#3b3756" strokeWidth="8.5" strokeLinecap="round" />
            <ellipse cx="18" cy="109" rx="7.5" ry="3.8" fill="#ffffff" stroke="#ddd3c8" strokeWidth="1.2" />
            <path d="M 14 109 C 15 107, 20 107, 23 109" stroke="#ff6b8b" strokeWidth="1.5" strokeLinecap="round" />

            <path d="M 38 72 L 41 106" stroke="#3b3756" strokeWidth="8.5" strokeLinecap="round" />
            <ellipse cx="44" cy="109" rx="7.5" ry="3.8" fill="#ffffff" stroke="#ddd3c8" strokeWidth="1.2" />
            <path d="M 40 109 C 41 107, 46 107, 49 109" stroke="#ff6b8b" strokeWidth="1.5" strokeLinecap="round" />

            {/* Torso / Pink Campus Jacket */}
            <path
              d="M 18 34 C 18 30, 26 29, 32 29 C 38 29, 46 30, 46 34 L 45 74 C 39 76, 25 76, 19 74 Z"
              fill="url(#dashGirlJacket)"
            />
            <path d="M 28 29 Q 32 35, 36 29" fill="#ffffff" />
            <line x1="32" y1="34" x2="32" y2="73" stroke="#ffffff" strokeWidth="1.2" opacity="0.7" />

            {/* Head & Hair */}
            <rect x="29" y="22" width="6" height="8" fill="#f8cdb2" rx="2" />
            <ellipse cx="32" cy="18" rx="10" ry="11" fill="#fcdac2" />
            <path
              d="M 22 19 C 21 8, 44 7, 42 19 C 43 24, 41 29, 39 33 C 36 29, 38 24, 36 21 C 31 15, 26 16, 24 22 Z"
              fill="#452c1e"
            />
            <circle cx="21" cy="21" r="3.5" fill="#8b7cf6" />
            <path d="M 21 22 Q 14 28, 16 35" stroke="#452c1e" strokeWidth="4" strokeLinecap="round" fill="none" />
            <path d="M 33 17 Q 36 14, 38 17" stroke="#241f3d" strokeWidth="1.4" strokeLinecap="round" fill="none" />
            <circle cx="35" cy="21" r="2" fill="#ff7f99" opacity="0.5" />
            <path d="M 33 22 Q 36 25, 39 22" stroke="#241f3d" strokeWidth="1.3" strokeLinecap="round" fill="none" />

            {/* Reaching arm giving gadget */}
            <motion.g
              animate={{ x: [0, 3, 0] }}
              transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
            >
              <path
                d="M 40 40 Q 56 44, 72 48"
                stroke="url(#dashGirlJacket)"
                strokeWidth="7.5"
                strokeLinecap="round"
                fill="none"
              />
              <circle cx="74" cy="48" r="3.8" fill="#fcdac2" />
            </motion.g>
          </motion.g>
        </g>

        {/* ===================================================
            EXCHANGE ITEM: Gift / College Gadget Box
            Firmly at X ≈ 195, Y ≈ 112
           =================================================== */}
        <g transform="translate(195, 112)">
          <motion.g
            animate={{ y: [0, -4, 0], scale: [1, 1.03, 1] }}
            transition={{ duration: 2.6, repeat: Infinity, ease: 'easeInOut' }}
          >
            <circle cx="15" cy="12" r="17" fill="#ffd269" opacity="0.32" filter="url(#dashGlow)" />
            <rect x="2" y="3" width="26" height="19" rx="3.5" fill="url(#dashItemGrad)" />
            <rect x="13" y="3" width="3.5" height="19" fill="#ffffff" opacity="0.85" />
            <rect x="2" y="10" width="26" height="3.5" fill="#ffffff" opacity="0.85" />
            <circle cx="14.5" cy="12" r="2.8" fill="#ffffff" />
          </motion.g>
        </g>

        {/* Dotted exchange arc */}
        <path
          className="anim-exchange-dash"
          d="M 170 126 Q 208 100, 246 126"
          stroke="#4d96ff"
          strokeWidth="2.2"
          strokeLinecap="round"
          fill="none"
          opacity="0.85"
        />

        {/* Floating subtle dots */}
        <circle cx="210" cy="98" r="2.5" fill="#ffc93c" />
        <circle cx="228" cy="112" r="2.2" fill="#ff6b6b" />
        <circle cx="192" cy="114" r="2" fill="#27c2b3" />

        {/* ===================================================
            BOY (Firmly on RIGHT: X ≈ 290, Y ≈ 78)
           =================================================== */}
        <g transform="translate(290, 78)">
          <motion.g
            animate={{ y: [0, -3.5, 0] }}
            transition={{ duration: 3.8, repeat: Infinity, ease: 'easeInOut', delay: 0.3 }}
          >
            {/* Ground shadow */}
            <ellipse cx="32" cy="116" rx="20" ry="4.5" fill="#1b1735" opacity="0.13" />

            {/* Legs */}
            <path d="M 25 74 L 23 108" stroke="#2d2b45" strokeWidth="8.5" strokeLinecap="round" />
            <ellipse cx="19" cy="111" rx="7.5" ry="3.8" fill="#ffffff" stroke="#ddd3c8" strokeWidth="1.2" />
            <path d="M 15 111 C 16 109, 21 109, 24 111" stroke="#3b96f5" strokeWidth="1.5" strokeLinecap="round" />

            <path d="M 39 74 L 41 108" stroke="#2d2b45" strokeWidth="8.5" strokeLinecap="round" />
            <ellipse cx="45" cy="111" rx="7.5" ry="3.8" fill="#ffffff" stroke="#ddd3c8" strokeWidth="1.2" />
            <path d="M 41 111 C 42 109, 47 109, 50 111" stroke="#3b96f5" strokeWidth="1.5" strokeLinecap="round" />

            {/* Torso / Sky Blue College Hoodie */}
            <path
              d="M 18 34 C 18 30, 26 29, 32 29 C 38 29, 46 30, 46 34 L 45 74 C 39 76, 25 76, 19 74 Z"
              fill="url(#dashBoyHoodie)"
            />
            <path d="M 28 29 Q 32 34, 36 29" fill="#ffffff" />
            <line x1="30" y1="35" x2="30" y2="44" stroke="#ffffff" strokeWidth="1.1" strokeLinecap="round" />
            <line x1="34" y1="35" x2="34" y2="44" stroke="#ffffff" strokeWidth="1.1" strokeLinecap="round" />

            {/* Head & Hair */}
            <rect x="29" y="22" width="6" height="8" fill="#f8cdb2" rx="2" />
            <ellipse cx="32" cy="18" rx="10" ry="11" fill="#fcdac2" />
            <path
              d="M 21 18 C 21 10, 30 7, 41 10 C 44 13, 43 19, 43 21 C 40 16, 34 16, 29 17 C 25 18, 23 19, 21 18 Z"
              fill="#32241b"
            />
            <circle cx="23" cy="19" r="2.2" fill="#f8cdb2" />
            <path d="M 26 17 Q 28 14, 31 17" stroke="#241f3d" strokeWidth="1.4" strokeLinecap="round" fill="none" />
            <circle cx="28" cy="21" r="2" fill="#ff7f99" opacity="0.45" />
            <path d="M 26 22 Q 29 25, 32 22" stroke="#241f3d" strokeWidth="1.3" strokeLinecap="round" fill="none" />

            {/* Receiving hands extended toward left */}
            <motion.g
              animate={{ x: [0, -3, 0] }}
              transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
            >
              <path
                d="M 24 42 Q 8 46, -10 50"
                stroke="url(#dashBoyHoodie)"
                strokeWidth="7.5"
                strokeLinecap="round"
                fill="none"
              />
              <circle cx="-12" cy="50" r="3.8" fill="#fcdac2" />
            </motion.g>

            {/* Right arm thumbs up */}
            <path
              d="M 42 40 Q 50 42, 52 52"
              stroke="url(#dashBoyHoodie)"
              strokeWidth="7.5"
              strokeLinecap="round"
              fill="none"
            />
            <circle cx="53" cy="54" r="3.3" fill="#fcdac2" />
          </motion.g>
        </g>

        {/* Small lawn daisies */}
        <circle cx="95" cy="205" r="2" fill="#ffc93c" />
        <circle cx="335" cy="210" r="2" fill="#ff8fb1" />
        <circle cx="355" cy="204" r="1.8" fill="#27c2b3" />
      </svg>
    </div>
  );
}
