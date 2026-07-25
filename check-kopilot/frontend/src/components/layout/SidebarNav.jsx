import { LayoutGrid, User, Settings, X } from "lucide-react";
import { cn } from "../../lib/utils";
import KopilotMark from "../common/KopilotMark";

const navLinks = [
  { icon: LayoutGrid, label: "지표 카탈로그" },
  { icon: User, label: "마이페이지" },
  { icon: Settings, label: "설정" },
];

function groupByDate(conversations) {
  return conversations.reduce((acc, conv) => {
    acc[conv.date] = acc[conv.date] || [];
    acc[conv.date].push(conv);
    return acc;
  }, {});
}

export default function SidebarNav({
  open,
  onClose,
  onNewChat,
  conversations = [],
}) {
  const grouped = groupByDate(conversations);

  return (
    <>
      {/* 모바일 드로어 뒷배경 */}
      {open && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-72 shrink-0 flex-col border-r border-slate-200 bg-slate-50 shadow-xl transition-transform duration-200 ease-out",
          "lg:static lg:z-auto lg:w-72 lg:translate-x-0 lg:shadow-none xl:w-80",
          open ? "translate-x-0" : "-translate-x-full"
        )}
        style={{
          paddingTop: "env(safe-area-inset-top)",
          paddingBottom: "env(safe-area-inset-bottom)",
        }}
      >
        <div className="flex items-center gap-2 p-3">
          <button
            data-tour="new-chat"
            onClick={() => {
              onNewChat?.();
              onClose();
            }}
            className={cn(
              "flex flex-1 items-center gap-2 rounded-lg bg-linear-to-br from-accent-500 to-accent-300 px-3 py-2.5",
              "text-sm font-medium text-white shadow-sm shadow-accent-700/20 hover:from-accent-600 hover:to-accent-400",
              "lg:py-3 lg:text-[15px]"
            )}
          >
            <KopilotMark className="h-4 w-4 shrink-0" />
            새 대화 시작
          </button>
          <button
            onClick={onClose}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-200/60 lg:hidden"
          >
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-3 py-2">
          {conversations.length === 0 ? (
            <p className="px-2 py-4 text-center text-xs text-slate-400">
              아직 대화 내역이 없습니다
            </p>
          ) : (
            Object.entries(grouped).map(([date, items]) => (
              <div key={date} className="mb-4">
                <p className="mb-1.5 px-2 text-xs font-medium text-slate-400 lg:text-[13px]">
                  {date}
                </p>
                <div className="space-y-0.5">
                  {items.map((conv) => (
                    <button
                      key={conv.id}
                      className="block w-full truncate rounded-md px-2 py-1.5 text-left text-sm text-slate-600 hover:bg-slate-200/60 lg:py-2 lg:text-[15px]"
                      title={conv.title}
                    >
                      {conv.title}
                    </button>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>

        <div className="border-t border-slate-200 p-3 space-y-0.5">
          {navLinks.map(({ icon: Icon, label }) => (
            <button
              key={label}
              className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-600 hover:bg-slate-200/60 lg:text-[15px]"
            >
              <Icon size={16} />
              {label}
            </button>
          ))}
        </div>
      </aside>
    </>
  );
}
