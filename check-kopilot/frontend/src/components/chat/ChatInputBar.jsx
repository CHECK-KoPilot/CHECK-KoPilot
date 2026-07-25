import { useEffect, useState } from "react";
import { ArrowUp } from "lucide-react";
import { suggestedPrompts } from "../../data/mockConversation";

const FULL_PLACEHOLDER =
  "궁금한 금융 데이터를 자연어로 물어보세요 (예: 삼성전자랑 코스피 수익률 갭 알려줘)";
const SHORT_PLACEHOLDER = "금융 데이터를 물어보세요";

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(max-width: 639px)");
    const update = () => setIsMobile(mq.matches);
    update();
    mq.addEventListener("change", update);
    return () => mq.removeEventListener("change", update);
  }, []);

  return isMobile;
}

export default function ChatInputBar({ onSend, onSelectSuggestion }) {
  const [value, setValue] = useState("");
  const isMobile = useIsMobile();

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!value.trim()) return;
    onSend?.(value);
    setValue("");
  };

  return (
    <div className="mx-auto w-full max-w-3xl px-4 pb-3 pt-4 shadow-[0_-12px_20px_-14px_rgba(15,23,42,0.12)] sm:px-6 sm:pb-4 lg:max-w-4xl xl:max-w-5xl">
      <div
        data-tour="suggested-prompts"
        className="mb-4 flex flex-wrap gap-2"
      >
        {suggestedPrompts.map((p) => (
          <button
            key={p}
            onClick={() => onSelectSuggestion?.(p)}
            className="rounded-full border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:border-accent-300 hover:text-accent-600 lg:text-sm"
          >
            {p}
          </button>
        ))}
      </div>

      <form
        data-tour="chat-input"
        onSubmit={handleSubmit}
        className="flex items-end gap-4 rounded-2xl border border-slate-300 p-2 focus-within:border-accent-400 lg:p-2.5"
      >
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              handleSubmit(e);
            }
          }}
          rows={1}
          placeholder={isMobile ? SHORT_PLACEHOLDER : FULL_PLACEHOLDER}
          className="max-h-32 flex-1 resize-none bg-transparent px-2 py-1.5 text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none lg:text-base"
        />
        <button
          type="submit"
          disabled={!value.trim()}
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-accent-500 to-accent-300 text-white hover:from-accent-600 hover:to-accent-400 disabled:opacity-40 lg:h-9 lg:w-9"
        >
          <ArrowUp size={16} />
        </button>
      </form>
    </div>
  );
}
