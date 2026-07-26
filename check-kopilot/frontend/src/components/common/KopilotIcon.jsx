export default function KopilotIcon({ className }) {
  return (
    <svg viewBox="0 0 512 512" className={className} fill="none">
      <defs>
        <linearGradient id="kopilot-bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#FAFAFC" />
          <stop offset="100%" stopColor="#EFEEF4" />
        </linearGradient>
        <linearGradient id="kopilot-bubble-back" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#FCC392" />
          <stop offset="100%" stopColor="#FBA860" />
        </linearGradient>
        <linearGradient id="kopilot-bubble-front" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#FA8D2E" />
          <stop offset="100%" stopColor="#BD5B05" />
        </linearGradient>
        <filter id="kopilot-shadow-blur" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="12" />
        </filter>
        <filter id="kopilot-highlight-blur" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="9" />
        </filter>
      </defs>

      <rect width="512" height="512" rx="112" fill="url(#kopilot-bg)" />

      <ellipse
        cx="298"
        cy="424"
        rx="140"
        ry="26"
        fill="#8f4504"
        opacity="0.22"
        filter="url(#kopilot-shadow-blur)"
      />

      <rect x="112" y="132" width="228" height="176" rx="48" fill="url(#kopilot-bubble-back)" />

      <rect x="172" y="192" width="252" height="204" rx="54" fill="url(#kopilot-bubble-front)" />
      <path d="M196 396 L244 396 L188 440 Z" fill="#BD5B05" />

      <ellipse
        cx="230"
        cy="228"
        rx="76"
        ry="22"
        fill="#ffffff"
        opacity="0.32"
        filter="url(#kopilot-highlight-blur)"
        transform="rotate(-22 230 228)"
      />

      <circle cx="254" cy="294" r="12" fill="#ffffff" />
      <circle cx="298" cy="294" r="12" fill="#ffffff" />
      <circle cx="342" cy="294" r="12" fill="#ffffff" />
    </svg>
  );
}
