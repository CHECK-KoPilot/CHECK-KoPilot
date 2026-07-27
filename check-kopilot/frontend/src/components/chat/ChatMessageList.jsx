import { AlertCircle } from "lucide-react";
import UserMessage from "./UserMessage";
import AssistantText from "./AssistantText";
import KopilotIcon from "../common/KopilotIcon";
import TutorialButton from "./TutorialButton";
import IndicatorAnswerCard from "./cards/IndicatorAnswerCard";
import ClarificationCard from "./cards/ClarificationCard";
import GuideRecipeCard from "./cards/GuideRecipeCard";
import { tourDemoCard } from "../../data/tourDemoCard";

function EmptyState({ onStartTour }) {
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
      <TutorialButton onClick={onStartTour} />
    </div>
  );
}


/**
 * 가이드 카드 뒤에 붙는 해설을 찾는다. 카드(guide 이벤트)와 해설(text 이벤트)이
 * 별도 메시지로 흐르기 때문에, 레시피를 파일로 저장할 때 둘을 다시 묶어야 한다.
 */
function followingAssistantText(messages, index) {
  for (let i = index + 1; i < messages.length; i++) {
    if (messages[i].type === "assistant") return messages[i].text;
    if (messages[i].type === "user") break;   // 다음 질문까지 가면 이 카드의 해설이 아니다
  }
  return undefined;
}

export default function ChatMessageList({
  messages,
  onSelectCandidate,
  onAskFollowUp,
  onStartTour,
  tourCardId,
  tourOpen,
}) {
  if (messages.length === 0) {
    if (!tourOpen) return <EmptyState onStartTour={onStartTour} />;
    // 투어 4~6단계(핵심 수치·근거·xlsx 다운로드)는 지표 카드 위에서만 설명할 수 있는데,
    // 빈 대화에는 보여줄 카드가 없다. 투어가 열려 있는 동안만 예시 카드를 띄운다.
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 pb-4 pt-14 sm:px-6 sm:pb-6 sm:pt-16 lg:max-w-4xl lg:gap-5 lg:pb-8 lg:pt-20 xl:max-w-5xl">
        <IndicatorAnswerCard message={tourDemoCard} tourTarget />
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 pb-4 pt-14 sm:px-6 sm:pb-6 sm:pt-16 lg:max-w-4xl lg:gap-5 lg:pb-8 lg:pt-20 xl:max-w-5xl">
      {messages.map((message, index) => {
        if (message.type === "user") {
          return (
            <div key={message.id} id={message.id}>
              <UserMessage text={message.text} />
            </div>
          );
        }
        if (message.type === "assistant") {
          return (
            <div key={message.id} id={message.id}>
              <AssistantText text={message.text} />
            </div>
          );
        }
        if (message.type === "error") {
          return (
            <div
              key={message.id}
              id={message.id}
              className="flex items-start gap-2 rounded-2xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700"
            >
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              <p>{message.text}</p>
            </div>
          );
        }
        if (message.type === "indicator") {
          return (
            <div key={message.id} id={message.id}>
              <IndicatorAnswerCard
                message={message}
                onAskFollowUp={onAskFollowUp}
                tourTarget={message.id === tourCardId}
              />
            </div>
          );
        }
        if (message.type === "clarification") {
          return (
            <div key={message.id} id={message.id}>
              <ClarificationCard
                message={message}
                onSelect={(candidate) => onSelectCandidate?.(candidate)}
              />
            </div>
          );
        }
        if (message.type === "guide") {
          return (
            <div key={message.id} id={message.id}>
              <GuideRecipeCard
                message={message}
                explanation={followingAssistantText(messages, index)}
                onAskFollowUp={onAskFollowUp}
              />
            </div>
          );
        }
        return null;
      })}
    </div>
  );
}
