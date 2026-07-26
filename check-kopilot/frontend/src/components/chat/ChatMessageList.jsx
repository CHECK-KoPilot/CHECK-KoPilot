import { AlertCircle } from "lucide-react";
import UserMessage from "./UserMessage";
import KopilotIcon from "../common/KopilotIcon";
import IndicatorAnswerCard from "./cards/IndicatorAnswerCard";

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

// clarify/guide 전용 카드 컴포넌트는 Task18에서 별도로 붙인다.
// 그 전까지는 원문 JSON으로 표시한다.
function RawEventCard({ message }) {
  return (
    <div className="w-full rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs">
      <pre className="overflow-x-auto whitespace-pre-wrap">{JSON.stringify(message, null, 2)}</pre>
    </div>
  );
}

export default function ChatMessageList({ messages }) {
  if (messages.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 pb-4 pt-14 sm:px-6 sm:pb-6 sm:pt-16 lg:max-w-4xl lg:gap-5 lg:pb-8 lg:pt-20 xl:max-w-5xl">
      {messages.map((message) => {
        if (message.type === "user") {
          return (
            <div key={message.id} id={message.id}>
              <UserMessage text={message.text} />
            </div>
          );
        }
        if (message.type === "assistant") {
          return (
            <div key={message.id} id={message.id} className="flex justify-start">
              <div className="max-w-lg rounded-2xl rounded-tl-sm bg-slate-100 px-4 py-2.5 text-sm text-slate-800 lg:max-w-xl lg:px-5 lg:py-3 lg:text-base">
                {message.text}
              </div>
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
              <IndicatorAnswerCard message={message} />
            </div>
          );
        }
        if (message.type === "clarification" || message.type === "guide") {
          return (
            <div key={message.id} id={message.id}>
              <RawEventCard message={message} />
            </div>
          );
        }
        return null;
      })}
    </div>
  );
}
