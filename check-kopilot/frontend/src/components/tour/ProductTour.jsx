import { useEffect, useRef, useState } from "react";
import { X, ArrowLeft, ArrowRight } from "lucide-react";
import { cn } from "../../lib/utils";

const TOOLTIP_WIDTH = 380;
const GAP = 14;

function getRect(selector) {
  const el = document.querySelector(selector);
  return el ? el.getBoundingClientRect() : null;
}

export default function ProductTour({ steps, onClose }) {
  const [stepIndex, setStepIndex] = useState(0);
  const [rect, setRect] = useState(null);
  const tooltipRef = useRef(null);
  const step = steps[stepIndex];
  const isFirst = stepIndex === 0;
  const isLast = stepIndex === steps.length - 1;

  useEffect(() => {
    let cancelled = false;

    async function show() {
      setRect(null);
      if (step.beforeShow) {
        step.beforeShow();
        await new Promise((r) => setTimeout(r, 260));
      }
      const el = document.querySelector(step.selector);
      if (!el) return;
      el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
      await new Promise((r) => setTimeout(r, 320));
      if (cancelled) return;
      setRect(el.getBoundingClientRect());
    }

    show();

    return () => {
      cancelled = true;
      step.afterHide?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stepIndex]);

  useEffect(() => {
    function recalc() {
      const r = getRect(step.selector);
      if (r) setRect(r);
    }
    window.addEventListener("resize", recalc);
    window.addEventListener("scroll", recalc, true);
    return () => {
      window.removeEventListener("resize", recalc);
      window.removeEventListener("scroll", recalc, true);
    };
  }, [step]);

  useEffect(() => {
    function handleKey(e) {
      if (e.key === "Escape") handleClose();
      if (e.key === "ArrowRight") handleNext();
      if (e.key === "ArrowLeft") handlePrev();
    }
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stepIndex]);

  const handleClose = () => {
    step.afterHide?.();
    onClose();
  };

  const handleNext = () => {
    if (isLast) {
      handleClose();
      return;
    }
    setStepIndex((i) => i + 1);
  };

  const handlePrev = () => {
    if (!isFirst) setStepIndex((i) => i - 1);
  };

  const vw = typeof window !== "undefined" ? window.innerWidth : 1024;
  const vh = typeof window !== "undefined" ? window.innerHeight : 768;
  const tooltipWidth = Math.min(TOOLTIP_WIDTH, vw - 32);

  let tooltipStyle = null;
  if (rect) {
    const spaceBelow = vh - rect.bottom;
    const placeBelow = spaceBelow > 200 || spaceBelow > rect.top;
    const top = placeBelow ? rect.bottom + GAP : undefined;
    const bottom = !placeBelow ? vh - rect.top + GAP : undefined;
    const idealLeft = rect.left + rect.width / 2 - tooltipWidth / 2;
    const left = Math.min(Math.max(idealLeft, 16), vw - tooltipWidth - 16);
    tooltipStyle = { top, bottom, left, width: tooltipWidth };
  }

  return (
    <div className="fixed inset-0 z-[100]">
      {/* 하이라이트 없이 대기 중일 때(스크롤/포커스 이동 중) 전체 딤 */}
      <div
        className={cn(
          "fixed inset-0 bg-slate-900/55 transition-opacity duration-200",
          rect ? "opacity-0" : "opacity-100"
        )}
      />

      {/* 스포트라이트: 대상 요소만 뚫려 보이게 */}
      {rect && (
        <div
          className="fixed rounded-xl transition-all duration-300 ease-out"
          style={{
            top: rect.top - 8,
            left: rect.left - 8,
            width: rect.width + 16,
            height: rect.height + 16,
            boxShadow:
              "0 0 0 3px rgba(249,119,6,0.85), 0 0 0 9999px rgba(15,23,42,0.55)",
            pointerEvents: "none",
          }}
        />
      )}

      <button
        onClick={handleClose}
        className="fixed right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-500 shadow-md hover:bg-white"
        style={{ top: "max(env(safe-area-inset-top), 1rem)" }}
        aria-label="투어 닫기"
      >
        <X size={18} />
      </button>

      {rect && tooltipStyle && (
        <div
          ref={tooltipRef}
          className="fixed rounded-2xl bg-white p-4 shadow-2xl transition-all duration-300 ease-out"
          style={tooltipStyle}
        >
          <p className="mb-1 text-lg font-medium text-accent-600 lg:text-xl">
            {stepIndex + 1} / {steps.length}
          </p>
          <h3 className="text-lg font-semibold text-slate-900 lg:text-xl">
            {step.title}
          </h3>
          <p className="mt-1.5 text-lg leading-relaxed text-slate-500 lg:text-xl">
            {step.description}
          </p>

          <div className="mt-4 flex items-center justify-between">
            <div className="flex gap-1.5">
              {steps.map((_, i) => (
                <span
                  key={i}
                  className={cn(
                    "h-1.5 rounded-full transition-all",
                    i === stepIndex ? "w-5 bg-accent-600" : "w-1.5 bg-slate-200"
                  )}
                />
              ))}
            </div>

            <div className="flex items-center gap-1.5">
              {!isFirst && (
                <button
                  onClick={handlePrev}
                  className="flex h-10 w-10 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100"
                  aria-label="이전"
                >
                  <ArrowLeft size={18} />
                </button>
              )}
              <button
                onClick={handleNext}
                className="flex h-10 items-center gap-1 rounded-lg bg-accent-600 px-4 text-lg font-medium text-white hover:bg-accent-700 lg:text-xl"
              >
                {isLast ? "완료" : "다음"}
                {!isLast && <ArrowRight size={16} />}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
