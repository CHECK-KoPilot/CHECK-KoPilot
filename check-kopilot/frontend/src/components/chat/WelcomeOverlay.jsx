import { Sparkles } from "lucide-react";
import KopilotIcon from "../common/KopilotIcon";
import Button from "../common/Button";

export default function WelcomeOverlay({ onStartTour, onDismiss }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-600/50 backdrop-blur-sm">
      <div className="flex flex-col items-center gap-6 px-6 text-center">
        <div className="relative flex h-28 w-28 items-center justify-center">
          <span className="absolute h-28 w-28 animate-ping rounded-2xl bg-accent-400/30" />
          <span className="absolute h-32 w-32 animate-pulse rounded-full bg-accent-300/25 blur-xl" />
          <KopilotIcon className="relative h-20 w-20 rounded-2xl shadow-lg shadow-accent-600/30 animate-welcome-pop" />
          <Sparkles className="absolute -top-2 -right-3 h-6 w-6 text-accent-400 animate-twinkle [animation-delay:0.1s]" />
          <Sparkles className="absolute -bottom-1 -left-5 h-4 w-4 text-accent-300 animate-twinkle [animation-delay:0.7s]" />
          <Sparkles className="absolute top-2 -left-7 h-3 w-3 text-accent-500 animate-twinkle [animation-delay:1.2s]" />
        </div>

        <div className="animate-welcome-fade-in [animation-delay:0.35s]">
          <p className="text-2xl font-bold text-white lg:text-3xl">CHECK Kopilot</p>
          <p className="mt-1.5 text-lg text-slate-200 lg:text-xl">
            자연어로 물어보면 금융 데이터를 바로 계산해드려요
          </p>
        </div>

        <div className="flex gap-3 animate-welcome-fade-in [animation-delay:0.6s]">
          <Button variant="secondary" size="md" onClick={onStartTour} className="lg:h-11 lg:px-5 lg:text-base">
            튜토리얼 보기
          </Button>
          <Button variant="primary" size="md" onClick={onDismiss} className="lg:h-11 lg:px-5 lg:text-base">
            시작하기
          </Button>
        </div>
      </div>
    </div>
  );
}
