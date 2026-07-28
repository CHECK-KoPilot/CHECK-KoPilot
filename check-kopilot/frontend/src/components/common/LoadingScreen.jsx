import KopilotIcon from "./KopilotIcon";

export default function LoadingScreen({ durationMs = 2000 }) {
  return (
    <div className="flex h-[100dvh] w-full flex-col items-center justify-center gap-5 bg-white">
      <div className="relative flex items-center justify-center">
        <span className="absolute h-20 w-20 animate-ping rounded-2xl bg-accent-400/30 lg:h-24 lg:w-24" />
        <KopilotIcon className="relative h-16 w-16 rounded-2xl shadow-lg shadow-accent-600/20 lg:h-20 lg:w-20" />
      </div>

      <div className="flex flex-col items-center gap-1">
        <p className="text-xl font-semibold text-slate-900 lg:text-[22px]">
          Kopilot
        </p>
        <p className="text-base text-slate-400 lg:text-lg">
          금융 데이터를 준비하고 있어요
        </p>
      </div>

      <div className="h-1 w-32 overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-accent-600"
          style={{
            animation: `loading-fill ${durationMs}ms ease-out forwards`,
          }}
        />
      </div>
    </div>
  );
}
