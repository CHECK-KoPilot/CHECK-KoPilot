import { cn } from "../../lib/utils";

const variants = {
  primary:
    "bg-linear-to-br from-accent-400 to-accent-700 text-white hover:from-accent-500 hover:to-accent-800 shadow-sm shadow-accent-700/20",
  secondary:
    "bg-white text-slate-700 border border-slate-200 hover:bg-slate-50",
  ghost: "text-slate-600 hover:bg-slate-100",
  chip:
    "bg-white text-slate-700 border border-slate-200 hover:border-accent-400 hover:text-accent-600",
};

const sizes = {
  sm: "h-8 px-3 text-sm",
  md: "h-10 px-4 text-sm",
};

export default function Button({
  as: Component = "button",
  variant = "primary",
  size = "md",
  className,
  children,
  ...props
}) {
  return (
    <Component
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed",
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {children}
    </Component>
  );
}
