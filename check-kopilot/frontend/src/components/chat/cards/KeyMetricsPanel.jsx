import { cn, formatPercent } from "../../../lib/utils";

const PERCENT_UNITS = new Set(["%", "%p"]);

export default function KeyMetricsPanel({ headline }) {
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
      {headline.map((m) => {
        const isPercent = PERCENT_UNITS.has(m.unit);
        const isPositive = m.value > 0;
        return (
          <div
            key={m.label}
            className="rounded-xl border border-slate-200 bg-slate-50 p-3 lg:p-4"
          >
            <p className="text-base text-slate-500 lg:text-lg">{m.label}</p>
            <p
              className={cn(
                "mt-1 text-2xl font-semibold tabular-nums lg:text-[28px]",
                isPercent ? (isPositive ? "text-up-600" : "text-down-600") : "text-slate-900"
              )}
            >
              {isPercent ? formatPercent(m.value) : m.value}
              {!isPercent && (
                <span className="ml-0.5 text-lg font-normal text-slate-400">{m.unit}</span>
              )}
              {m.unit === "%p" && <span className="ml-0.5 text-lg font-normal">p</span>}
            </p>
          </div>
        );
      })}
    </div>
  );
}
