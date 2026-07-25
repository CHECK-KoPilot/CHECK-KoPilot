import UserMessage from "./UserMessage";
import IndicatorAnswerCard from "./cards/IndicatorAnswerCard";
import ClarificationCard from "./cards/ClarificationCard";
import GuideRecipeCard from "./cards/GuideRecipeCard";
import KopilotIcon from "../common/KopilotIcon";

function EmptyState() {
  return (
    <div className="flex h-full min-h-[50vh] flex-col items-center justify-center gap-3 px-4 text-center">
      <KopilotIcon className="h-14 w-14 rounded-2xl shadow-sm lg:h-16 lg:w-16" />
      <div>
        <p className="text-base font-semibold text-slate-800 lg:text-lg">
          무엇을 도와드릴까요?
        </p>
        <p className="mt-1 text-sm text-slate-400 lg:text-base">
          궁금한 금융 데이터를 자연어로 물어보세요
        </p>
      </div>
    </div>
  );
}

export default function ChatMessageList({ messages, onSelectCandidate }) {
  if (messages.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 pb-4 pt-14 sm:px-6 sm:pb-6 sm:pt-16 lg:max-w-4xl lg:gap-5 lg:pb-8 lg:pt-20 xl:max-w-5xl">
      {messages.map((message) => {
        if (message.type === "user") {
          return <UserMessage key={message.id} text={message.text} />;
        }
        if (message.type === "indicator") {
          return (
            <IndicatorAnswerCard
              key={message.id}
              message={message}
              tourTarget={message.id === "a1"}
            />
          );
        }
        if (message.type === "clarification") {
          return (
            <ClarificationCard
              key={message.id}
              message={message}
              onSelect={(candidate) => onSelectCandidate?.(message, candidate)}
            />
          );
        }
        if (message.type === "guide") {
          return <GuideRecipeCard key={message.id} message={message} />;
        }
        return null;
      })}
    </div>
  );
}
