import { useEffect, useRef, useState } from "react";
import { Keyboard, Plus, Trash2, Pencil } from "lucide-react";
import ShortcutFormModal from "./ShortcutFormModal";
import { formatCombo, isMacPlatform } from "../../lib/keyCombo";
import { deleteShortcut } from "../../lib/shortcutsApi";

export default function ShortcutMenu({ shortcuts, loadError, onReload, onFormOpenChange }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const rootRef = useRef(null);
  const mac = isMacPlatform();

  // 열린 드롭다운은 Escape와 바깥 클릭으로 닫는다 — 토글 버튼을 다시 찾아 눌러야만
  // 닫히면 채팅으로 넘어간 뒤에도 패널이 화면에 남는다. ProductTour와 같은 방식.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    const onPointerDown = (e) => {
      if (!rootRef.current?.contains(e.target)) setOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("pointerdown", onPointerDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("pointerdown", onPointerDown);
    };
  }, [open]);

  const openForm = (shortcut) => {
    setEditing(shortcut);
    setFormOpen(true);
    setOpen(false);
    onFormOpenChange(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
    onFormOpenChange(false);
  };

  const remove = async (id) => {
    try {
      await deleteShortcut(id);
    } catch {
      // 삭제 실패도 목록을 다시 부른다 — 서버 상태가 화면의 진실이다
    }
    onReload();
  };

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        aria-label="단축키"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="flex h-9 items-center gap-1.5 rounded-lg px-2 text-slate-600 hover:bg-slate-100"
      >
        <Keyboard size={18} />
        <span className="hidden text-base sm:block">단축키</span>
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-1 w-80 rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
          {loadError ? (
            <div className="px-2 py-3 text-sm text-slate-500">
              불러오지 못했습니다
              <button
                type="button"
                onClick={onReload}
                className="ml-2 text-accent-600 hover:underline"
              >
                다시 시도
              </button>
            </div>
          ) : (
            <ul className="max-h-72 overflow-y-auto">
              {shortcuts.length === 0 && (
                <li className="px-2 py-3 text-sm text-slate-500">아직 만든 단축키가 없어요</li>
              )}
              {shortcuts.map((s) => (
                <li key={s.id} className="flex items-start gap-2 rounded-lg px-2 py-2 hover:bg-slate-50">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-slate-900">{formatCombo(s.keyCombo, mac)}</p>
                    <p className="truncate text-sm text-slate-500">{s.prompt}</p>
                  </div>
                  <button
                    type="button"
                    aria-label={`${formatCombo(s.keyCombo, mac)} 수정`}
                    onClick={() => openForm(s)}
                    className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-200 hover:text-slate-600"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    aria-label={`${formatCombo(s.keyCombo, mac)} 삭제`}
                    onClick={() => remove(s.id)}
                    className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-600"
                  >
                    <Trash2 size={14} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            onClick={() => openForm(null)}
            className="mt-1 flex w-full items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-medium text-accent-600 hover:bg-accent-50"
          >
            <Plus size={16} /> 단축키 추가
          </button>
        </div>
      )}

      {formOpen && (
        <ShortcutFormModal
          editing={editing}
          existing={shortcuts}
          onSaved={() => { closeForm(); onReload(); }}
          onClose={closeForm}
        />
      )}
    </div>
  );
}
