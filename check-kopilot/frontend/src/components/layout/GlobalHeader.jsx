import { ChevronDown, Menu } from "lucide-react";
import KopilotIcon from "../common/KopilotIcon";

export default function GlobalHeader({ title = "새 대화", onMenuClick }) {
  return (
    <header
      className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-3 lg:h-16 lg:px-6"
      style={{ paddingTop: "env(safe-area-inset-top)" }}
    >
      <div className="flex min-w-0 items-center gap-2">
        <button
          onClick={onMenuClick}
          aria-label="메뉴 열기"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 lg:hidden"
        >
          <Menu size={20} />
        </button>

        <KopilotIcon className="h-7 w-7 shrink-0 rounded-md lg:h-8 lg:w-8" />
        <span className="shrink-0 text-sm font-semibold text-slate-900 lg:text-base">
          Kopilot
        </span>
        <span className="mx-1 hidden h-4 w-px bg-slate-200 sm:mx-2 sm:block" />
        <span className="truncate text-sm text-slate-500 lg:text-[15px]">
          {title}
        </span>
      </div>

      <button className="flex shrink-0 items-center gap-2 rounded-full py-1 pl-1 pr-2 hover:bg-slate-100 lg:pr-3">
        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-600 lg:h-8 lg:w-8 lg:text-sm">
          김
        </div>
        <span className="hidden text-sm text-slate-600 sm:block lg:text-[15px]">
          김트레이더
        </span>
        <ChevronDown size={14} className="hidden text-slate-400 sm:block" />
      </button>
    </header>
  );
}
