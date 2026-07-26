import { HelpCircle } from "lucide-react";

export default function ClarificationCard({ message, onSelect }) {
  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:p-5">
      <div className="mb-3 flex items-start gap-2">
        <HelpCircle size={16} className="mt-0.5 shrink-0 text-accent-500" />
        <p className="text-sm text-slate-700 lg:text-base">
          '{message.query}' 검색 결과가 여러 개입니다. 어떤 종목을 말씀하시는 건가요?
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {message.candidates.map((c) => (
          <button
            key={c.code}
            onClick={() => onSelect?.(c)}
            className="rounded-full border border-slate-200 bg-white px-4 py-1.5 text-sm text-slate-700 transition-colors hover:border-accent-400 hover:bg-accent-50 hover:text-accent-700 lg:px-5 lg:py-2 lg:text-[15px]"
          >
            {c.name}
            <span className="ml-1 text-xs text-slate-400">
              {c.code} · {c.market}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
