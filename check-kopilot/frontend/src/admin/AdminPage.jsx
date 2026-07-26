import { useEffect, useState } from "react";
import { fetchDemandSummary, fetchStats } from "./adminApi";

const STAT_LABELS = [
  { key: "questionCount", label: "총 질문" },
  { key: "cardCount", label: "지표 카드 응답" },
  { key: "guideCount", label: "가이드(미지원) 응답" },
  { key: "catalogCoverageRate", label: "카탈로그 응답률", unit: "%" },
];

export default function AdminPage({ token }) {
  const [rows, setRows] = useState([]);
  const [stats, setStats] = useState(null);
  const [denied, setDenied] = useState(false);

  useEffect(() => {
    if (!token) {
      setDenied(true);
      return;
    }
    Promise.all([fetchDemandSummary(token), fetchStats(token)])
      .then(([r, s]) => {
        setRows(r);
        setStats(s);
      })
      .catch(() => setDenied(true));
  }, [token]);

  if (denied) {
    return (
      <div className="flex h-dvh items-center justify-center text-sm text-slate-500">
        접근 권한이 없습니다.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="text-xl font-semibold text-slate-900 lg:text-2xl">지표 수요 대시보드</h1>
      <p className="mt-1 text-sm text-slate-400">
        카탈로그 밖 질문(가이드 카드 발생)을 자동 수집한 결과입니다. 다음에 구현할 지표의 우선순위 근거로
        사용합니다.
      </p>

      {stats && (
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {STAT_LABELS.map(({ key, label, unit = "" }) => (
            <div key={key} className="rounded-xl border border-slate-200 bg-slate-50 p-3 lg:p-4">
              <p className="text-xs text-slate-500 lg:text-sm">{label}</p>
              <p className="mt-1 text-xl font-semibold tabular-nums text-slate-900 lg:text-2xl">
                {stats[key]}
                {unit}
              </p>
            </div>
          ))}
        </div>
      )}

      <div className="mt-6 overflow-x-auto rounded-lg border border-slate-100">
        <table className="w-full text-xs lg:text-sm">
          <thead>
            <tr className="bg-slate-50 text-left text-slate-500">
              <th className="px-3 py-1.5 font-medium">요청 주제</th>
              <th className="px-3 py-1.5 font-medium">요청 수</th>
              <th className="px-3 py-1.5 font-medium">명시 요청</th>
              <th className="px-3 py-1.5 font-medium">세션 수</th>
              <th className="px-3 py-1.5 font-medium">관련 CHECK API</th>
              <th className="px-3 py-1.5 font-medium">최근</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.topic} className="border-t border-slate-100">
                <td className="px-3 py-1.5 text-slate-700">{r.topic}</td>
                <td className="px-3 py-1.5 text-slate-600">{r.requestCount}</td>
                <td className="px-3 py-1.5 text-slate-600">{r.explicitCount}</td>
                <td className="px-3 py-1.5 text-slate-600">{r.sessionCount}</td>
                <td className="px-3 py-1.5 text-slate-600">{r.matchedApiIds ?? "-"}</td>
                <td className="px-3 py-1.5 text-slate-400">{r.lastAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 && (
        <p className="mt-3 text-sm text-slate-400">아직 수집된 수요가 없습니다.</p>
      )}
    </div>
  );
}
