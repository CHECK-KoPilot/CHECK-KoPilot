function headers(token) {
  return { "X-Admin-Token": token };
}

export async function fetchDemandSummary(token) {
  const res = await fetch("/api/admin/demand/summary?limit=50", { headers: headers(token) });
  if (!res.ok) throw new Error("unauthorized");
  return res.json();
}

export async function fetchStats(token) {
  const res = await fetch("/api/admin/stats", { headers: headers(token) });
  if (!res.ok) throw new Error("unauthorized");
  return res.json();
}

/** '#/admin?k=xxxx' 에서 토큰 추출 */
export function tokenFromHash(hash) {
  const query = hash.split("?")[1] ?? "";
  return new URLSearchParams(query).get("k") ?? "";
}
