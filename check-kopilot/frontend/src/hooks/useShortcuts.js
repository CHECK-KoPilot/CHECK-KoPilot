import { useCallback, useEffect, useRef, useState } from "react";
import { fetchShortcuts } from "../lib/shortcutsApi";
import { comboFromEvent } from "../lib/keyCombo";

/**
 * 단축키 목록과 전역 키 바인딩.
 *
 * 리스너는 한 번만 붙이고 최신 값은 ref로 읽는다 — 목록이 바뀔 때마다 addEventListener를
 * 다시 걸면, 키를 누른 순간과 리바인드가 겹쳤을 때 이벤트를 흘린다.
 */
export function useShortcuts({ onTrigger, enabled }) {
  const [shortcuts, setShortcuts] = useState([]);
  const [loadError, setLoadError] = useState(false);

  const shortcutsRef = useRef(shortcuts);
  const enabledRef = useRef(enabled);
  const onTriggerRef = useRef(onTrigger);

  shortcutsRef.current = shortcuts;
  enabledRef.current = enabled;
  onTriggerRef.current = onTrigger;

  const reload = useCallback(async () => {
    try {
      const list = await fetchShortcuts();
      setShortcuts(Array.isArray(list) ? list : []);
      setLoadError(false);
    } catch {
      setShortcuts([]);
      setLoadError(true);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (!enabledRef.current) return;
      // 한글 조합 중의 keydown은 조합 확정용이다 — 여기서 발사하면 입력 도중 질문이 나간다
      if (event.isComposing || event.keyCode === 229) return;

      const combo = comboFromEvent(event);
      if (!combo) return;

      const hit = shortcutsRef.current.find((s) => s.keyCombo === combo);
      if (!hit) return; // 등록 안 된 조합은 브라우저에 그대로 넘긴다

      event.preventDefault();
      onTriggerRef.current?.(hit.prompt);
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return { shortcuts, loadError, reload };
}
