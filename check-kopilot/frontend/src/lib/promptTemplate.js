export const PERIODS = [
  { code: "1M", label: "최근 1개월", phrase: "최근 1개월" },
  { code: "3M", label: "최근 3개월", phrase: "최근 3개월" },
  { code: "6M", label: "최근 6개월", phrase: "최근 6개월" },
  { code: "1Y", label: "최근 1년", phrase: "최근 1년" },
];

/** 칩·저장값 표기. 되묻기 카드가 쓰는 "이름(코드)"와 같은 형식으로 맞춘다. */
export function stockLabel({ name, code }) {
  return code ? `${name}(${code})` : name;
}

/** "삼성전자(005930)" → "삼성전자". 프롬프트 본문에는 코드를 넣지 않는다 — 문장이 읽기 나빠진다. */
export function nameOf(label) {
  return label.replace(/\([^)]*\)$/, "").trim();
}

export function needsPeriod(template) {
  return template.includes("{period}");
}

/** 한글 마지막 글자에 받침이 있는지 — "와/과"를 고르는 데만 쓴다. */
function hasFinalConsonant(text) {
  const last = text.at(-1) ?? "";
  const code = last.charCodeAt(0);
  if (code < 0xac00 || code > 0xd7a3) return false; // 한글이 아니면 "와"로 둔다
  return (code - 0xac00) % 28 !== 0;
}

function joinNames(names) {
  if (names.length === 0) return "";
  if (names.length === 1) return names[0];
  if (names.length === 2) return `${names[0]}${hasFinalConsonant(names[0]) ? "과" : "와"} ${names[1]}`;
  return names.join(", ");
}

/**
 * 템플릿 → 실제로 전송될 문장.
 *
 * 치환 후 남는 공백을 정리하는 이유는 기간을 안 고른 경우 "{period}" 자리가 비기 때문이다.
 */
export function buildPrompt({ template, targetLabels, periodCode }) {
  const phrase = PERIODS.find((p) => p.code === periodCode)?.phrase ?? "";
  return template
    .replace("{targets}", joinNames(targetLabels.map(nameOf)))
    .replace("{period}", phrase)
    .replace(/\s+/g, " ")
    .trim();
}
