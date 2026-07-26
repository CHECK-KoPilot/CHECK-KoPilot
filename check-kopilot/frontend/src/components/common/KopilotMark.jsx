// 로고(KopilotIcon)와 같은 말풍선 실루엣만 남긴 단색 버전 — 컬러 배경(버튼 등) 위에 얹어 쓰는 용도
export default function KopilotMark({ className }) {
  return (
    <svg viewBox="0 0 512 512" className={className} fill="none">
      <rect x="112" y="132" width="228" height="176" rx="48" fill="#ffffff" opacity="0.55" />
      <rect x="172" y="192" width="252" height="204" rx="54" fill="#ffffff" opacity="0.95" />
      <path d="M196 396 L244 396 L188 440 Z" fill="#ffffff" opacity="0.95" />
      <circle cx="254" cy="294" r="12" fill="#DB6906" />
      <circle cx="298" cy="294" r="12" fill="#DB6906" />
      <circle cx="342" cy="294" r="12" fill="#DB6906" />
    </svg>
  );
}
