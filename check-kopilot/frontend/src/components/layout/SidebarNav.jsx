import { LayoutGrid, User, Settings, X, Trash2 } from "lucide-react";
import { cn } from "../../lib/utils";
import KopilotMark from "../common/KopilotMark";

const navLinks = [
  { icon: LayoutGrid, label: "지표 카탈로그" },
  { icon: User, label: "마이페이지" },
  { icon: Settings, label: "설정" },
];

const DAY = 24 * 60 * 60 * 1000;

/** 목록 머리글에 쓸 날짜 라벨. 가까운 날은 "오늘/어제"가 훑기 쉽다 */
function dateLabel(updatedAt) {
  const day = (ms) => Math.floor(new Date(ms).setHours(0, 0, 0, 0) / DAY);
  const diff = day(Date.now()) - day(updatedAt ?? Date.now());
  if (diff <= 0) return "오늘";
  if (diff === 1) return "어제";
  return new Date(updatedAt).toISOString().slice(0, 10);
}

/** 최근순 배열을 순서 그대로 날짜별로 묶는다 */
function groupByDate(conversations) {
  const groups = new Map();
  for (const conv of conversations) {
    const label = dateLabel(conv.updatedAt);
    if (!groups.has(label)) groups.set(label, []);
    groups.get(label).push(conv);
  }
  return [...groups.entries()];
}

export default function SidebarNav({
  open,
  onClose,
  onNewChat,
  conversations = [],
  activeConversationId,
  onSelectConversation,
  onDeleteConversation,
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
            onClick={() => {
              onNewChat?.();
              onClose();
            }}
            data-tour="new-chat"
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
            grouped.map(([date, items]) => (
              <div key={date} className="mb-4">
                <p className="mb-1.5 px-2 text-xs font-medium text-slate-400 lg:text-[13px]">
                  {date}
                </p>
                <div className="space-y-0.5">
                  {items.map((conv) => (
                    <div
                      key={conv.id}
                      className={cn(
                        "group flex items-center rounded-md pr-1 hover:bg-slate-200/60",
                        conv.id === activeConversationId && "bg-slate-200/80"
                      )}
                    >
                      <button
                        onClick={() => {
                          onSelectConversation?.(conv.id);
                          onClose();
                        }}
                        className="min-w-0 flex-1 truncate px-2 py-1.5 text-left text-sm text-slate-600 lg:py-2 lg:text-[15px]"
                        title={conv.title}
                      >
                        {conv.title}
                      </button>
                      <button
                        onClick={() => onDeleteConversation?.(conv.id)}
                        aria-label={`${conv.title} 대화 삭제`}
                        className="flex h-7 w-7 shrink-0 items-center justify-center rounded text-slate-400 opacity-0 hover:bg-slate-300/60 hover:text-slate-600 focus-visible:opacity-100 group-hover:opacity-100"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
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
