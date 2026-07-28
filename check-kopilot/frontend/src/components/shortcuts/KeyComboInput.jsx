import { useId, useState } from "react";
import { comboFromEvent, formatCombo, isMacPlatform } from "../../lib/keyCombo";

const RULE = "Ctrl(⌘)+Shift와 숫자·영문 한 글자 조합만 등록할 수 있어요";

/**
 * 키 조합 캡처.
 *
 * 텍스트로 받아 적게 하면 사용자가 실제로 누를 수 있는 조합인지 알 수 없다. 그래서
 * 실제 keydown을 잡되, 브라우저 예약 조합을 피하려고 범위를 좁혀 둔다.
 */
export default function KeyComboInput({ value, onChange, conflictLabel }) {
  const [hint, setHint] = useState(null);
  const mac = isMacPlatform();
  const hintId = useId();

  const capture = (event) => {
    // 모든 키를 먹는 게 아니라, Ctrl/⌘ 조합만 먹는다.
    // 그래야 Tab/Escape 같은 네이티브 네비게이션이 작동한다.
    if (["Control", "Shift", "Alt", "Meta"].includes(event.key)) return;
    if (!event.ctrlKey && !event.metaKey) return;

    // Ctrl/⌘ 조합만 기본 동작을 막는다 — Ctrl+S 같은 조합이 브라우저로 새어 나가면 안 된다
    event.preventDefault();

    const combo = comboFromEvent(event);
    if (!combo) {
      setHint(RULE);
      return;
    }
    setHint(null);
    onChange(combo);
  };

  return (
    <div>
      <button
        type="button"
        aria-label="키 조합"
        aria-describedby={hint ? hintId : undefined}
        onKeyDown={capture}
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-left text-base text-slate-800 focus:border-accent-400 focus:outline-none"
      >
        {value ? formatCombo(value, mac) : "여기를 누른 뒤 키를 눌러 주세요"}
      </button>

      {conflictLabel && (
        <p className="mt-1 text-sm text-red-600" aria-live="polite">
          이미 &lsquo;{conflictLabel}&rsquo;이(가) 쓰는 조합이에요
        </p>
      )}
      {hint && !conflictLabel && (
        <p className="mt-1 text-sm text-slate-500" id={hintId} aria-live="polite">
          {hint}
        </p>
      )}
    </div>
  );
}
