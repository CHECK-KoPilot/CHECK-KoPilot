import { Download, Sparkles } from "lucide-react";
import Button from "../../common/Button";
import KeyMetricsPanel from "./KeyMetricsPanel";
import ChartPanel from "./ChartPanel";
import EvidencePanel from "./EvidencePanel";

export default function IndicatorAnswerCard({ message, tourTarget = false }) {
  const { cardId, title, from, to, headline, chart, commentary, evidence } = message;

  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:p-5">
      <div className="mb-3">
        <h3 className="text-sm font-semibold text-slate-900 lg:text-base">{title}</h3>
        <p className="text-xs text-slate-400 lg:text-sm">
          {from} ~ {to}
        </p>
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

      <div className="flex justify-end gap-2">
        <Button
          as="a"
          href={`/api/cards/${cardId}/xlsx`}
          download
          variant="secondary"
          size="sm"
          data-tour={tourTarget ? "excel-download" : undefined}
        >
          <Download size={14} />
          Excel 다운로드
        </Button>
      </div>
    </div>
  );
}
