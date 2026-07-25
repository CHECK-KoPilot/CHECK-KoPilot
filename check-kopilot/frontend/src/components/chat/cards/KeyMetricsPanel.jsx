import { cn, formatPercent } from "../../../lib/utils";

export default function KeyMetricsPanel({ metrics }) {
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
      {metrics.map((m) => {
        const isPositive = m.value > 0;
        return (
          <div
            key={m.label}
            className={cn(
              "rounded-xl border p-3 lg:p-4",
              m.highlight
                ? "border-accent-200 bg-accent-50/60"
                : "border-slate-200 bg-slate-50"
            )}
          >
            <p className="text-xs text-slate-500 lg:text-sm">{m.label}</p>
            <p
              className={cn(
                "mt-1 text-xl font-semibold tabular-nums lg:text-2xl",
                m.unit === "%p" || m.unit === "%"
                  ? isPositive
                    ? "text-up-600"
                    : "text-down-600"
                  : "text-slate-900"
              )}
            >
              {m.unit === "%" || m.unit === "%p"
                ? formatPercent(m.value)
                : m.value}
              {m.unit !== "%" && m.unit !== "%p" ? (
                <span className="ml-0.5 text-sm font-normal text-slate-400">
                  {m.unit}
                </span>
              ) : (
                <span className="ml-0.5 text-sm font-normal">
                  {m.unit === "%p" ? "p" : ""}
                </span>
              )}
            </p>
          </div>
        );
      })}
    </div>
  );
}
