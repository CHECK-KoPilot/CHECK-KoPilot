import { clsx } from "clsx";

export function cn(...inputs) {
  return clsx(inputs);
}

export function formatPercent(value, { withSign = true } = {}) {
  const sign = withSign && value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)}%`;
}

export function formatNumber(value) {
  return new Intl.NumberFormat("ko-KR").format(value);
}
