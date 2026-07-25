import { useState } from "react";
import AppLayout from "../components/layout/AppLayout";
import ChatMessageList from "../components/chat/ChatMessageList";
import ChatInputBar from "../components/chat/ChatInputBar";
import {
  mockMessages,
  demoResponseTemplates,
} from "../data/mockConversation";

function pickRandomResponse() {
  const template =
    demoResponseTemplates[
      Math.floor(Math.random() * demoResponseTemplates.length)
    ];
  return { ...template, id: `a-${Date.now()}` };
}

export default function ChatPage() {
  const [messages, setMessages] = useState([]);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleSend = (text) => {
    // 임시: 실제 백엔드 연동 전까지, 자유 입력에는 데모 응답 4종 중 하나를 무작위로 보여준다.
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, type: "user", text },
      pickRandomResponse(),
    ]);
  };

  const handleSelectSuggestion = (text) => {
    // 미리 정해둔 추천 질문은 무작위가 아니라, 항상 같은 지표 답변 카드를 확정적으로 보여준다.
    const template = demoResponseTemplates.find((m) => m.id === "a1");
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, type: "user", text },
      { ...template, id: `a-${Date.now()}`, title: text },
    ]);
  };

  const handleSelectCandidate = (clarificationMessage, candidate) => {
    const sample = mockMessages.find((m) => m.id === "a1");
    setMessages((prev) => [
      ...prev,
      {
        ...sample,
        id: `a-${Date.now()}`,
        title: `${candidate.name} · 지난주 시세 요약`,
      },
    ]);
  };

  const handleNewChat = () => {
    setMessages([]);
  };

  return (
    <AppLayout
      headerTitle="새 대화"
      sidebarOpen={sidebarOpen}
      onSidebarOpenChange={setSidebarOpen}
      onNewChat={handleNewChat}
    >
      <div className="flex h-full flex-col">
        <div className="min-h-0 flex-1 overflow-y-auto">
          <ChatMessageList
            messages={messages}
            onSelectCandidate={handleSelectCandidate}
          />
        </div>
        <ChatInputBar
          onSend={handleSend}
          onSelectSuggestion={handleSelectSuggestion}
        />
      </div>
    </AppLayout>
  );
}
