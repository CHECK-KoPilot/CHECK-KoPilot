import { Code2, Download, Sparkles } from "lucide-react";
import Button from "../../common/Button";
import KeyMetricsPanel from "./KeyMetricsPanel";
import ChartPanel from "./ChartPanel";
import EvidencePanel from "./EvidencePanel";
import { indicatorImplementationPrompt } from "../../../lib/implementationPrompt";

export default function IndicatorAnswerCard({ message, tourTarget = false, onAskFollowUp }) {
  const { cardId, title, from, to, targets, headline, chart, commentary, evidence } = message;

  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:p-5">
      <div className="mb-3">
        <h3 className="text-sm font-semibold text-slate-900 lg:text-base">{title}</h3>
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          {targets?.map((t) => (
            <span
              key={t.code}
              className="rounded-full bg-accent-50 px-2 py-0.5 text-xs font-medium text-accent-700"
            >
              {t.name} {t.code}
            </span>
          ))}
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
            {from} ~ {to}
          </span>
        </div>
      </div>

      <div className="mb-3" data-tour={tourTarget ? "key-metrics" : undefined}>
        <KeyMetricsPanel headline={headline} />
      </div>

      <div className="mb-3">
        <ChartPanel chart={chart} />
      </div>

      {commentary && (
        <div className="mb-3 flex gap-2 rounded-xl bg-slate-50 px-3 py-2.5 lg:px-4 lg:py-3">
          <Sparkles size={14} className="mt-0.5 shrink-0 text-accent-500" />
          <p className="text-sm leading-relaxed text-slate-600 lg:text-[15px]">
            <span className="mr-1 text-xs font-medium text-slate-400 lg:text-sm">
              AI 해설
            </span>
            {commentary}
          </p>
        </div>
      )}

      <div className="mb-3">
        <EvidencePanel evidence={evidence} tourTarget={tourTarget} />
      </div>

      <div className="flex flex-wrap justify-end gap-2">
        {/* 카탈로그에 있는 지표라도 "직접 만들려면?"은 남는 질문이다. 카드가 호출한 API를
            그대로 물려주므로 레시피가 위 근거 패널과 같은 API를 가리킨다. */}
        <Button
          variant="secondary"
          size="sm"
          disabled={!onAskFollowUp}
          onClick={() => onAskFollowUp?.(indicatorImplementationPrompt(message))}
        >
          <Code2 size={14} />
          구현 방법 자세히
        </Button>
        <Button
          as="a"
          href={`/api/cards/${cardId}/xlsx`}
          download
          variant="secondary"
          size="sm"
          data-tour={tourTarget ? "excel-download" : undefined}
        >
          <Download size={14} />
          xlsx 다운로드
        </Button>
      </div>
    </div>
  );
}
