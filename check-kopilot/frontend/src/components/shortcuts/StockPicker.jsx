import { useEffect, useId, useState } from "react";
import { X } from "lucide-react";
import { searchStocks } from "../../lib/shortcutsApi";
import { stockLabel } from "../../lib/promptTemplate";

const MIN_QUERY = 2;
const DEBOUNCE_MS = 200;

/**
 * 종목 자동완성 칩 입력.
 *
 * 마스터가 4천 행이라 목록을 통째로 못 내린다. 입력할 때마다 서버에 묻되,
 * 앞선 요청은 중단한다 — 늦게 도착한 옛 응답이 최신 후보를 덮으면 안 된다.
 */
export default function StockPicker({ value, onChange, max }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const full = value.length >= max;
  const resultsId = useId();

  useEffect(() => {
    if (full || query.trim().length < MIN_QUERY) {
      setResults([]);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchStocks(query.trim(), controller.signal)
        .then((found) => setResults(found ?? []))
        .catch(() => setResults([])); // 검색 실패는 자동완성만 비운다 — 입력은 계속된다
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, full]);

  const add = (stock) => {
    const label = stockLabel(stock);
    if (value.includes(label)) return;
    onChange([...value, label]);
    setQuery("");
    setResults([]);
  };

  return (
    <div className="rounded-lg border border-slate-300 p-2 focus-within:border-accent-400">
      <div className="flex flex-wrap gap-1.5">
        {value.map((label) => (
          <span
            key={label}
            className="inline-flex items-center gap-1 rounded-full bg-slate-100 py-1 pl-2.5 pr-1 text-sm text-slate-700"
          >
            {label}
            <button
              type="button"
              aria-label={`${label} 제거`}
              onClick={() => onChange(value.filter((v) => v !== label))}
              className="flex h-5 w-5 items-center justify-center rounded-full text-slate-400 hover:bg-slate-200 hover:text-slate-600"
            >
              <X size={12} />
            </button>
          </span>
        ))}
      </div>

      <div className="relative">
        <input
          role="combobox"
          aria-label="종목 검색"
          aria-expanded={results.length > 0}
          aria-controls={resultsId}
          value={query}
          disabled={full}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={full ? `종목 ${max}개를 모두 채웠어요` : "종목명을 입력하세요"}
          className="mt-1 w-full bg-transparent px-1 py-1 text-base text-slate-800 placeholder:text-slate-400 focus:outline-none disabled:cursor-not-allowed"
        />

        {results.length > 0 && (
          <ul
            id={resultsId}
            className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
          >
            {results.map((stock) => (
              <li key={stock.code}>
                <button
                  type="button"
                  onClick={() => add(stock)}
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-base hover:bg-slate-50"
                >
                  <span className="text-slate-800">{stock.name}</span>
                  <span className="text-sm text-slate-400">{stock.code}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
