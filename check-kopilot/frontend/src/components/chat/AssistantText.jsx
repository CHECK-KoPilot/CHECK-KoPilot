/**
 * LLM 해설 말풍선. 모델이 마크다운을 섞어 보내므로 최소한만 해석한다 —
 * 코드 펜스, **굵게**, `인라인 코드`, 줄바꿈. 카드 데이터는 백엔드가 만든 구조를
 * 그대로 렌더하니 여기서 필요한 건 딱 이 정도이고, 라이브러리를 얹을 이유가 없다.
 *
 * 원문을 그대로 노출하는 대신 해석만 하며, 마크다운이 아닌 텍스트는 그대로 통과한다.
 */

const FENCE = /```(\w*)\n?([\s\S]*?)```/g;
const INLINE = /(\*\*[\s\S]+?\*\*|`[^`]+`)/g;
const HEADING = /^\s{0,3}(#{1,6})\s+(.+)$/;

/** **굵게**와 `코드`만 태그로 바꾼다. 줄바꿈은 CSS(whitespace-pre-wrap)가 살린다 */
function renderInline(text, keyPrefix) {
  return text.split(INLINE).map((part, i) => {
    const key = `${keyPrefix}-${i}`;
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return <strong key={key}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith("`") && part.endsWith("`") && part.length > 2) {
      return (
        <code
          key={key}
          className="rounded bg-slate-200/70 px-1 py-0.5 font-mono text-[0.9em]"
        >
          {part.slice(1, -1)}
        </code>
      );
    }
    return part;
  });
}

/**
 * 텍스트 블록을 문단으로 옮긴다. '### 1. API 선택' 같은 헤딩 줄만 따로 떼어 굵게 세우고,
 * 나머지 줄은 이어 붙여 한 문단으로 만든다 — 줄바꿈은 CSS가 살린다.
 */
function renderTextBlock(text, keyPrefix) {
  const parts = [];
  let buffer = [];

  const flush = () => {
    const body = buffer.join("\n").replace(/^\n+|\n+$/g, "");
    buffer = [];
    if (body.length === 0) return;
    parts.push(
      <p key={`${keyPrefix}-p${parts.length}`} className="whitespace-pre-wrap break-words">
        {renderInline(body, `${keyPrefix}-p${parts.length}`)}
      </p>
    );
  };

  for (const line of text.split("\n")) {
    const heading = HEADING.exec(line);
    if (!heading) {
      buffer.push(line);
      continue;
    }
    flush();
    parts.push(
      <p key={`${keyPrefix}-h${parts.length}`} className="font-semibold text-slate-900">
        {renderInline(heading[2], `${keyPrefix}-h${parts.length}`)}
      </p>
    );
  }
  flush();
  return parts;
}

/** 코드 펜스를 기준으로 자른다 → [{type:'text'|'code', value}] */
function splitBlocks(text) {
  const blocks = [];
  let cursor = 0;

  FENCE.lastIndex = 0;
  for (let match = FENCE.exec(text); match !== null; match = FENCE.exec(text)) {
    if (match.index > cursor) {
      blocks.push({ type: "text", value: text.slice(cursor, match.index) });
    }
    blocks.push({ type: "code", value: match[2].replace(/\n$/, "") });
    cursor = match.index + match[0].length;
  }
  if (cursor < text.length) {
    blocks.push({ type: "text", value: text.slice(cursor) });
  }
  return blocks.filter((b) => b.value.trim().length > 0);
}

export default function AssistantText({ text }) {
  const blocks = splitBlocks(String(text ?? ""));

  return (
    <div className="flex justify-start">
      <div className="max-w-lg space-y-2 rounded-2xl rounded-tl-sm bg-slate-100 px-4 py-2.5 text-lg text-slate-800 lg:max-w-xl lg:px-5 lg:py-3 lg:text-xl">
        {blocks.map((block, i) =>
          block.type === "code" ? (
            <pre
              key={i}
              className="overflow-x-auto rounded-lg bg-slate-800 px-3 py-2 text-base text-slate-100"
            >
              <code>{block.value}</code>
            </pre>
          ) : (
            renderTextBlock(block.value, String(i))
          )
        )}
      </div>
    </div>
  );
}
