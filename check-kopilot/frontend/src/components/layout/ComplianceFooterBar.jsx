import { ShieldAlert } from "lucide-react";
import { DISCLAIMER } from "../../lib/compliance";

export default function ComplianceFooterBar() {
  return (
    <div
      className="flex shrink-0 items-center justify-center gap-1.5 border-t border-slate-100 bg-slate-50 px-4 py-1.5 text-center lg:py-2"
      style={{
        paddingBottom: "max(env(safe-area-inset-bottom), 0.375rem)",
      }}
    >
      <ShieldAlert size={12} className="shrink-0 text-slate-400" />
      <p className="text-[17px] leading-snug text-slate-400 lg:text-lg">
        {DISCLAIMER}
      </p>
    </div>
  );
}
