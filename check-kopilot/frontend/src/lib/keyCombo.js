/**
 * 키 조합의 정규화 형식은 "ctrl+shift+<숫자|영문>" 하나뿐이다.
 *
 * Ctrl(mac은 ⌘)+Shift로 범위를 좁힌 이유는 브라우저 예약 조합을 피하기 위해서다.
 * Ctrl+T/W/N은 탭·창을 열고, Alt+숫자는 탭을 전환한다.
 *
 * 다만 Ctrl+Shift 안에도 브라우저가 preventDefault를 무시하는 조합이 남아 있다.
 * 아래 글자들은 등록 자체를 막는다 — W를 잡으면 창이 닫혀 작성 중인 폼이 날아가고,
 * T는 저장해두면 눌러도 지표가 아니라 닫은 탭이 되살아난다.
 */
const RESERVED_LETTERS = new Set(["t", "w", "n", "q", "i", "j", "p"]);
const DIGIT = /^Digit([0-9])$/;
const LETTER = /^Key([A-Z])$/;

/** Shift를 누르면 event.key가 "!"로 바뀌므로 물리 키인 event.code를 본다. */
function baseKey(event) {
  const code = event.code ?? "";
  const digit = DIGIT.exec(code);
  if (digit) return digit[1];
  const letter = LETTER.exec(code);
  if (letter) {
    const lower = letter[1].toLowerCase();
    return RESERVED_LETTERS.has(lower) ? null : lower;
  }
  return null;
}

/**
 * 예약 글자'라서만' 거부된 입력인지. 수식키까지 맞을 때만 참이다.
 * Ctrl+T처럼 Shift가 빠진 입력은 예약 여부와 무관하게 형식 안내를 받아야 한다.
 */
export function isReservedOnly(event) {
  if (!(event.ctrlKey || event.metaKey)) return false;
  if (!event.shiftKey || event.altKey) return false;
  const letter = LETTER.exec(event.code ?? "");
  return Boolean(letter) && RESERVED_LETTERS.has(letter[1].toLowerCase());
}

/** 폼 안내 문구용 — 왜 어떤 글자는 안 잡히는지 알려준다. */
export const RESERVED_HINT = "T·W·N·Q·I·J·P는 브라우저가 먼저 가져가 쓸 수 없어요";

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
