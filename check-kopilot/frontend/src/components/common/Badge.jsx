import { cn } from "../../lib/utils";

const tones = {
  neutral: "bg-slate-100 text-slate-600",
  brand: "bg-accent-50 text-accent-700",
  up: "bg-up-50 text-up-600",
  down: "bg-down-50 text-down-600",
};

export default function Badge({ tone = "neutral", className, children }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium",
        tones[tone],
        className
      )}
    >
      {children}
    </span>
  );
}
