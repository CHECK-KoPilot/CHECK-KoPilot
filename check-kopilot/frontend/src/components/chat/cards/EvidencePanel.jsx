import { useState } from "react";
import { ChevronDown, ExternalLink, FileCode2 } from "lucide-react";
import { cn } from "../../../lib/utils";

/** evidence.rawData[]({name, rows:[{date,value}]})를 종목명 열이 있는 표로 합친다 */
function toRawRows(rawData) {
  const dates = [...new Set(rawData.flatMap((s) => s.rows.map((r) => r.date)))];
  return dates.map((date) => {
    const row = { date };
    for (const s of rawData) {
      const point = s.rows.find((r) => r.date === date);
      row[s.name] = point ? point.value : null;
    }
    return row;
  });
}

export default function EvidencePanel({ evidence, tourTarget = false }) {
  const [open, setOpen] = useState(false);
  const rawRows = toRawRows(evidence.rawData);

  return (
    <div className="rounded-xl border border-slate-200">
      <button
        data-tour={tourTarget ? "evidence-toggle" : undefined}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-2.5 text-left lg:py-3"
      >
        <span className="flex items-center gap-1.5 text-sm font-medium text-accent-700 lg:text-[15px]">
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
        <div className="space-y-4 border-t border-slate-100 px-4 py-4 text-sm">
          <section>
            <p className="mb-1.5 text-xs font-semibold text-slate-500">
              사용된 CHECK API
            </p>
            <ul className="space-y-1.5">
              {evidence.apiCalls.map((call) => (
                <li
                  key={call.api}
                  className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
                >
                  <div>
                    <p className="text-slate-700">{call.api}</p>
                    <code className="text-xs text-slate-400">{call.request}</code>
                  </div>
                  <a
                    href={call.specUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-xs text-accent-600 hover:underline"
                  >
                    명세 보기 <ExternalLink size={11} />
                  </a>
                </li>
              ))}
            </ul>
          </section>

          <section>
            <p className="mb-1.5 text-xs font-semibold text-slate-500">
              적용 계산식
            </p>
            <code className="block rounded-lg bg-slate-900 px-3 py-2 text-xs text-slate-100">
              {evidence.formula}
            </code>
          </section>

          <section>
            <p className="mb-1.5 text-xs font-semibold text-slate-500">
              중간 계산 과정
            </p>
            <ol className="list-decimal space-y-1 pl-4 text-slate-600">
              {evidence.steps.map((step) => (
                <li key={step.label}>
                  <span className="font-medium text-slate-700">{step.label}</span>
                  {step.detail ? ` — ${step.detail}` : ""}
                </li>
              ))}
            </ol>
          </section>

          <section>
            <p className="mb-1.5 text-xs font-semibold text-slate-500">
              원본 데이터
            </p>
            <div className="overflow-x-auto rounded-lg border border-slate-100">
              <table className="w-full text-xs">
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
          </section>
        </div>
      )}
    </div>
  );
}
