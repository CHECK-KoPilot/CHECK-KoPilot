import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import Button from "../common/Button";
import StockPicker from "./StockPicker";
import KeyComboInput from "./KeyComboInput";
import { PERIODS, buildPrompt, needsPeriod } from "../../lib/promptTemplate";
import { createShortcut, fetchCatalog, updateShortcut } from "../../lib/shortcutsApi";

const DEFAULT_PERIOD = "3M";

export default function ShortcutFormModal({ editing, existing, onSaved, onClose }) {
  const [catalog, setCatalog] = useState([]);
  const [toolName, setToolName] = useState(editing?.toolName ?? "");
  const [targets, setTargets] = useState(editing?.targets ?? []);
  const [period, setPeriod] = useState(editing?.period ?? DEFAULT_PERIOD);
  const [keyCombo, setKeyCombo] = useState(editing?.keyCombo ?? null);
  const [prompt, setPrompt] = useState(editing?.prompt ?? "");
  // 편집 중인 프리셋의 문구는 이미 사람이 확정한 것이다 — 열자마자 덮어쓰지 않는다
  const [promptEdited, setPromptEdited] = useState(Boolean(editing));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchCatalog()
      .then((items) => {
        setCatalog(items ?? []);
        setToolName((current) => current || items?.[0]?.toolName || "");
      })
      .catch(() => setError("지표 목록을 불러오지 못했습니다"));
  }, []);

  const selected = useMemo(
    () => catalog.find((item) => item.toolName === toolName) ?? null,
    [catalog, toolName]
  );

  const periodUsed = selected ? needsPeriod(selected.promptTemplate) : false;

  // 선택이 바뀌면 문구를 다시 만든다. 단, 사람이 손댄 뒤로는 건드리지 않는다.
  useEffect(() => {
    if (!selected || promptEdited) return;
    setPrompt(buildPrompt({
      template: selected.promptTemplate,
      targetLabels: targets,
      periodCode: periodUsed ? period : null,
    }));
  }, [selected, targets, period, periodUsed, promptEdited]);

  const conflict = existing.find((s) => s.keyCombo === keyCombo && s.id !== editing?.id) ?? null;
  const targetsOk = selected
    && targets.length >= selected.minTargets
    && targets.length <= selected.maxTargets;
  const canSave = Boolean(selected) && targetsOk && Boolean(keyCombo) && !conflict
    && prompt.trim().length > 0 && !saving;

  const save = async () => {
    setSaving(true);
    setError(null);
    const body = {
      keyCombo,
      toolName,
      targets,
      period: periodUsed ? period : null,
      prompt: prompt.trim(),
    };
    try {
      const saved = editing
        ? await updateShortcut(editing.id, body)
        : await createShortcut(body);
      onSaved(saved);
    } catch (e) {
      setError(e.message ?? "저장에 실패했습니다");
    } finally {
      setSaving(false);
    }
  };

  const regenerate = () => {
    if (!selected) return;
    setPromptEdited(false);
    setPrompt(buildPrompt({
      template: selected.promptTemplate,
      targetLabels: targets,
      periodCode: periodUsed ? period : null,
    }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-full w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">
            {editing ? "단축키 수정" : "단축키 추가"}
          </h2>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"
          >
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label htmlFor="shortcut-tool" className="mb-1 block text-sm font-medium text-slate-700">
              분석할 카탈로그
            </label>
            <select
              id="shortcut-tool"
              aria-label="분석할 카탈로그"
              value={toolName}
              onChange={(e) => setToolName(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
            >
              {catalog.map((item) => (
                <option key={item.toolName} value={item.toolName}>{item.label}</option>
              ))}
            </select>
            {selected && (
              <p className="mt-1 text-sm text-slate-500">
                종목 {selected.minTargets === selected.maxTargets
                  ? `${selected.minTargets}개`
                  : `${selected.minTargets}~${selected.maxTargets}개`}
              </p>
            )}
          </div>

          <div>
            <span className="mb-1 block text-sm font-medium text-slate-700">종목</span>
            <StockPicker
              value={targets}
              onChange={setTargets}
              max={selected?.maxTargets ?? 1}
            />
          </div>

          {periodUsed && (
            <div>
              <label htmlFor="shortcut-period" className="mb-1 block text-sm font-medium text-slate-700">
                기간
              </label>
              <select
                id="shortcut-period"
                aria-label="기간"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
              >
                {PERIODS.map((p) => (
                  <option key={p.code} value={p.code}>{p.label}</option>
                ))}
              </select>
            </div>
          )}

          <div>
            <span className="mb-1 block text-sm font-medium text-slate-700">단축키 조합</span>
            <KeyComboInput
              value={keyCombo}
              onChange={setKeyCombo}
              conflictLabel={conflict ? conflict.prompt : null}
            />
          </div>

          <div>
            <div className="mb-1 flex items-center justify-between">
              <label htmlFor="shortcut-prompt" className="text-sm font-medium text-slate-700">
                프롬프트 예시
              </label>
              <button
                type="button"
                onClick={regenerate}
                className="text-sm text-slate-500 hover:text-accent-600"
              >
                다시 생성
              </button>
            </div>
            <textarea
              id="shortcut-prompt"
              aria-label="프롬프트"
              rows={3}
              value={prompt}
              onChange={(e) => { setPromptEdited(true); setPrompt(e.target.value); }}
              maxLength={300}
              className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-base text-slate-800 focus:border-accent-400 focus:outline-none"
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>취소</Button>
          <Button variant="primary" onClick={save} disabled={!canSave}>저장</Button>
        </div>
      </div>
    </div>
  );
}
