import { useState } from "react";
import { ChevronDown, Compass, ExternalLink, Check, Download, Code2 } from "lucide-react";
import Button from "../../common/Button";
import { cn } from "../../../lib/utils";
import { getSessionId } from "../../../lib/session";
import { downloadRecipeMarkdown } from "../../../lib/recipeFile";
import { guideImplementationPrompt } from "../../../lib/implementationPrompt";

export default function GuideRecipeCard({ message, explanation, onAskFollowUp }) {
  const [requested, setRequested] = useState(false);
  const [pending, setPending] = useState(false);
  const [catalogOpen, setCatalogOpen] = useState(false);
  // knownMetric = 카탈로그에 있는 지표의 구현 방법을 물은 경우.
  // 같은 카드를 쓰되 "없는 지표입니다"라고 말하면 안 되고, 카탈로그 추가 요청도 의미가 없다.
  const { topic, matched = [], catalog = [], knownMetric = false } = message;

  const requestCatalog = async () => {
    setPending(true);
    try {
      await fetch("/api/catalog-requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: getSessionId(),
          topic,
          matchedApiIds: matched.map((api) => api.apiId).join(","),
        }),
      });
    } catch {
      // 임시: 백엔드 미배포 기간에는 요청 실패도 접수된 것처럼 보여준다
    } finally {
      setRequested(true);
      setPending(false);
    }
  };

  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:p-5">
      <div className="mb-3 flex items-start gap-2">
        <Compass size={16} className="mt-0.5 shrink-0 text-accent-500" />
        <div>
          <h3 className="text-sm font-semibold text-slate-900 lg:text-base">
            {knownMetric
              ? `'${topic}' 직접 구현하기`
              : `'${topic}'은 현재 카탈로그에 없는 지표입니다`}
          </h3>
          <p className="mt-0.5 text-sm text-slate-500 lg:text-[15px]">
            {knownMetric
              ? "이 카드가 실제로 호출한 CHECK API입니다. 같은 값을 직접 산출할 수 있습니다."
              : "대신 아래 CHECK API를 조합하면 동일한 데이터를 직접 산출할 수 있습니다."}
          </p>
        </div>
      </div>

      {matched.length > 0 && (
        <section className="mb-3">
          <p className="mb-1.5 text-xs font-semibold text-slate-500">
            필요한 CHECK API
          </p>
          <div className="space-y-2">
            {matched.map((api) => (
              <div
                key={api.apiId}
                className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2"
              >
                <div className="flex items-center justify-between">
                  <p className="text-sm text-slate-700">{api.name}</p>
                  {api.docUrl && (
                    <a
                      href={api.docUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="flex items-center gap-1 text-xs text-accent-600 hover:underline"
                    >
                      명세 보기 <ExternalLink size={11} />
                    </a>
                  )}
                </div>
                {api.summary && (
                  <p className="text-xs text-slate-400">{api.summary}</p>
                )}
                <code className="text-xs text-slate-400">{api.path}</code>
                {(api.params ?? []).length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {api.params.map((p) => (
                      <span
                        key={p.name}
                        className="rounded bg-white px-1.5 py-0.5 text-[11px] text-slate-500 border border-slate-200"
                      >
                        {p.name}
                        {p.required ? "*" : ""}
                      </span>
                    ))}
                  </div>
                )}
                {(api.fields ?? []).length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {api.fields.map((f) => (
                      <span
                        key={f.code}
                        className="rounded-full bg-accent-50 px-2 py-0.5 text-[11px] text-accent-700"
                      >
                        {f.label}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {catalog.length > 0 && (
        <section className="mb-4 rounded-xl border border-slate-200">
          <button
            onClick={() => setCatalogOpen((v) => !v)}
            className="flex w-full items-center justify-between px-3 py-2 text-left"
          >
            <span className="text-xs font-semibold text-slate-500">
              다른 후보 API ({catalog.length}건)
            </span>
            <ChevronDown
              size={14}
              className={cn("text-slate-400 transition-transform", catalogOpen && "rotate-180")}
            />
          </button>

          {catalogOpen && (
            <div className="flex flex-wrap gap-1.5 border-t border-slate-100 px-3 py-2.5">
              {catalog.map((c) => (
                <span
                  key={c.apiId}
                  title={c.summary}
                  className="rounded-full border border-slate-200 px-2.5 py-1 text-xs text-slate-600"
                >
                  {c.name}
                </span>
              ))}
            </div>
          )}
        </section>
      )}

      <div className="flex flex-wrap justify-end gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => downloadRecipeMarkdown(message, explanation)}
        >
          <Download size={14} />
          레시피 저장
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={!onAskFollowUp}
          onClick={() => onAskFollowUp?.(guideImplementationPrompt(topic, matched))}
        >
          <Code2 size={14} />
          구현 방법 자세히
        </Button>
        {!knownMetric && (
          <Button
            variant={requested ? "secondary" : "primary"}
            size="sm"
            disabled={requested || pending}
            onClick={requestCatalog}
          >
            {requested ? <Check size={14} /> : null}
            {requested ? "요청 완료" : "카탈로그 추가 요청"}
          </Button>
        )}
      </div>
    </div>
  );
}
