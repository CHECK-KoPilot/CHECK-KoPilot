import { useState } from "react";
import { ChevronDown, ExternalLink, FileCode2 } from "lucide-react";
import { cn } from "../../../lib/utils";

/** evidence.rawData[]({name, rows:[{date,value}]})를 종목명 열이 있는 표로 합친다 */
function toRawRows(rawData) {
  const series = rawData ?? [];
  const dates = [...new Set(series.flatMap((s) => (s.rows ?? []).map((r) => r.date)))];
  return dates.map((date) => {
    const row = { date };
    for (const s of series) {
      const point = (s.rows ?? []).find((r) => r.date === date);
      row[s.name] = point ? point.value : null;
    }
    return row;
  });
}

export default function EvidencePanel({ evidence, tourTarget = false }) {
  const [open, setOpen] = useState(false);
  // 백엔드 계약(MetricResult.Evidence)은 네 항목을 항상 채우지만, 여기서 방어하지 않으면
  // 한 항목이 비는 순간 렌더가 던지고 — ErrorBoundary가 없어 — 화면 전체가 백지가 된다(#100)
  const { apiCalls = [], rawData = [], formula = "", steps = [] } = evidence ?? {};
  const rawRows = toRawRows(rawData);

  return (
    <div className="rounded-xl border border-slate-200">
      <button
        data-tour={tourTarget ? "evidence-toggle" : undefined}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-2.5 text-left lg:py-3"
      >
        <span className="flex items-center gap-1.5 text-lg font-medium text-accent-700 lg:text-[19px]">
          <FileCode2 size={14} />
          근거 보기 — 사용된 API · 원본 데이터 · 계산식
        </span>
        <ChevronDown
          size={16}
          className={cn(
            "text-slate-400 transition-transform",
            open && "rotate-180"
          )}
        />
      </button>

      {open && (
        <div className="space-y-4 border-t border-slate-100 px-4 py-4 text-lg">
          <section>
            <p className="mb-1.5 text-base font-semibold text-slate-500">
              사용된 CHECK API
            </p>
            <ul className="space-y-1.5">
              {apiCalls.map((call) => (
                <li
                  key={`${call.api}-${call.request}`}
                  className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
                >
                  <div>
                    <p className="text-slate-700">{call.api}</p>
                    <code className="text-base text-slate-400">{call.request}</code>
                  </div>
                  <a
                    href={call.specUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-base text-accent-600 hover:underline"
                  >
                    명세 보기 <ExternalLink size={11} />
                  </a>
                </li>
              ))}
            </ul>
          </section>

          <section>
            <p className="mb-1.5 text-base font-semibold text-slate-500">
              적용 계산식
            </p>
            <code className="block rounded-lg bg-slate-900 px-3 py-2 text-base text-slate-100">
              {formula}
            </code>
          </section>

          <section>
            <p className="mb-1.5 text-base font-semibold text-slate-500">
              중간 계산 과정
            </p>
            <ol className="list-decimal space-y-1 pl-4 text-slate-600">
              {steps.map((step) => (
                <li key={step.label}>
                  <span className="font-medium text-slate-700">{step.label}</span>
                  {step.detail ? ` — ${step.detail}` : ""}
                </li>
              ))}
            </ol>
          </section>

          <section>
            <p className="mb-1.5 text-base font-semibold text-slate-500">
              원본 데이터
            </p>
            {rawRows.length === 0 ? (
              <p className="rounded-lg bg-slate-50 px-3 py-2 text-base text-slate-500">
                원본 데이터가 없습니다
              </p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-100">
                <table className="w-full text-base">
                  <thead>
                    <tr className="bg-slate-50 text-left text-slate-500">
                      {Object.keys(rawRows[0]).map((key) => (
                        <th key={key} className="px-3 py-1.5 font-medium">
                          {key}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rawRows.map((row, i) => (
                      <tr key={i} className="border-t border-slate-100">
                        {Object.values(row).map((val, j) => (
                          <td key={j} className="px-3 py-1.5 text-slate-600">
                            {val}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
