import { ShieldAlert } from "lucide-react";

export default function ComplianceFooterBar() {
  return (
    <div
      className="flex shrink-0 items-center justify-center gap-1.5 border-t border-slate-100 bg-slate-50 px-4 py-1.5 text-center lg:py-2"
      style={{
        paddingBottom: "max(env(safe-area-inset-bottom), 0.375rem)",
      }}
    >
      <ShieldAlert size={12} className="shrink-0 text-slate-400" />
      <p className="text-[11px] leading-snug text-slate-400 lg:text-xs">
        본 자료는 AI가 시장 데이터와 CHECK API 기반으로 생성한 정보성
        자료이며 투자권유가 아닙니다
      </p>
    </div>
  );
}
