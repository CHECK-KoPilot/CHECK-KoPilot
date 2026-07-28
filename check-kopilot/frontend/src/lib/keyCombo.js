/**
 * 키 조합의 정규화 형식은 "ctrl+shift+<숫자|영문>" 하나뿐이다.
 *
 * Ctrl(mac은 ⌘)+Shift로 범위를 좁힌 이유는 브라우저 예약 조합을 피하기 위해서다.
 * Ctrl+T/W/N은 탭·창을 열고, Alt+숫자는 탭을 전환한다.
 */
const DIGIT = /^Digit([0-9])$/;
const LETTER = /^Key([A-Z])$/;

/** Shift를 누르면 event.key가 "!"로 바뀌므로 물리 키인 event.code를 본다. */
function baseKey(event) {
  const code = event.code ?? "";
  const digit = DIGIT.exec(code);
  if (digit) return digit[1];
  const letter = LETTER.exec(code);
  if (letter) return letter[1].toLowerCase();
  return null;
}

/** keydown 이벤트 → "ctrl+shift+1". 허용 범위 밖이면 null. */
export function comboFromEvent(event) {
  if (!(event.ctrlKey || event.metaKey)) return null;
  if (!event.shiftKey || event.altKey) return null;
  const key = baseKey(event);
  return key ? `ctrl+shift+${key}` : null;
}

/** 사람이 읽는 표기. 저장 형식은 플랫폼과 무관하게 하나이고 표시만 갈린다. */
export function formatCombo(combo, isMac = false) {
  const key = combo.split("+").at(-1) ?? "";
  const shown = key.length === 1 ? key.toUpperCase() : key;
  return isMac ? `⌘ + ⇧ + ${shown}` : `Ctrl + Shift + ${shown}`;
}

export function isMacPlatform() {
  return typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform ?? "");
}
