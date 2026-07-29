import { Sparkles } from "lucide-react";

export default function TutorialButton({ onClick }) {
  return (
    <button
      onClick={onClick}
      className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[15px] font-medium text-slate-600 shadow-sm transition-colors hover:border-accent-300 hover:text-accent-600 lg:text-[17px]"
    >
      <Sparkles size={12} className="text-accent-500" />
      튜토리얼
    </button>
  );
}
