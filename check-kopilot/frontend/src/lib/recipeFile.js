/**
 * 가이드(레시피) 카드를 파일로 내려받게 만든다.
 *
 * 지표 카드의 xlsx는 원본 시계열이 필요해 백엔드(Apache POI)가 만들지만, 레시피는
 * API 목록·파라미터·필드·링크가 전부라 이미 카드 데이터 안에 있다. 코드 블록과 링크가
 * 살아야 쓸모가 있으므로 표(xlsx)가 아니라 마크다운으로 만든다 — 새 엔드포인트도, 의존성도 필요 없다.
 */

import { DISCLAIMER } from "./compliance";

/** 파일명에 쓸 수 없는 문자를 걷어낸다 */
export function safeFileName(topic) {
  const cleaned = String(topic ?? "")
    .replace(/[\\/:*?"<>|]/g, "")
    .trim()
    .replace(/\s+/g, "-");
  return cleaned.length > 0 ? cleaned.slice(0, 60) : "recipe";
}

export function buildRecipeMarkdown(message, explanation) {
  const { topic, matched = [], catalog = [] } = message ?? {};
  const lines = [];

  lines.push(`# ${topic} — CHECK API 레시피`, "");
  lines.push(
    `'${topic}'은(는) Check Kopilot 지표 카탈로그에 없는 항목입니다.`,
    "아래 CHECK API를 조합하면 동일한 데이터를 직접 산출할 수 있습니다.",
    "",
  );

  if (explanation) {
    lines.push("## 조합 방법", "", explanation, "");
  }

  if (matched.length > 0) {
    lines.push("## 필요한 CHECK API", "");
    for (const api of matched) {
      lines.push(`### ${api.name}`, "");
      if (api.summary) lines.push(api.summary, "");
      if (api.path) lines.push(`- 호출 경로: \`${api.path}\``);
      if (api.docUrl) lines.push(`- 명세: ${api.docUrl}`);

      const params = api.params ?? [];
      if (params.length > 0) {
        const required = params.filter((p) => p.required).map((p) => p.name);
        const optional = params.filter((p) => !p.required).map((p) => p.name);
        if (required.length > 0) lines.push(`- 필수 파라미터: ${required.join(", ")}`);
        if (optional.length > 0) lines.push(`- 선택 파라미터: ${optional.join(", ")}`);
      }

      const fields = api.fields ?? [];
      if (fields.length > 0) {
        lines.push("", "| F코드 | 필드 |", "| --- | --- |");
        for (const f of fields) lines.push(`| \`${f.code}\` | ${f.label} |`);
      }
      lines.push("");
    }
  }

  if (catalog.length > 0) {
    lines.push("## 함께 볼 만한 API", "");
    for (const c of catalog) lines.push(`- ${c.name} (\`${c.apiId}\`)`);
    lines.push("");
  }

  lines.push("---", "", DISCLAIMER, "");
  return lines.join("\n");
}

/** 브라우저에서 마크다운을 파일로 저장시킨다 */
export function downloadRecipeMarkdown(message, explanation) {
  const markdown = buildRecipeMarkdown(message, explanation);
  const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${safeFileName(message?.topic)}-레시피.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
