import { useEffect, useRef, useState } from "react";
import AppLayout from "../components/layout/AppLayout";
import ChatMessageList from "../components/chat/ChatMessageList";
import ChatInputBar from "../components/chat/ChatInputBar";
import { streamChat, endSession } from "../lib/sse";
import { getSessionId, resetSessionId } from "../lib/session";
import { loadTranscript, saveTranscript, clearTranscript } from "../lib/transcript";

const CARD_EVENT_TYPES = {
  card: "indicator",
  clarify: "clarification",
  guide: "guide",
};

export default function ChatPage() {
  // 서버는 새로고침 후에도 세션 컨텍스트를 기억한다 — 화면도 같이 복원해야 맥락이 어긋나지 않는다
  const [sessionId, setSessionId] = useState(getSessionId);
  const [messages, setMessages] = useState(() => loadTranscript(getSessionId()));
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const scrolledUserIdRef = useRef(null);

  useEffect(() => {
    saveTranscript(sessionId, messages);
  }, [sessionId, messages]);

  // 새로 보낸 질문 말풍선이 화면 위쪽에 오도록 스크롤
  useEffect(() => {
    const lastUserMessage = [...messages].reverse().find((m) => m.type === "user");
    if (!lastUserMessage || lastUserMessage.id === scrolledUserIdRef.current) return;
    scrolledUserIdRef.current = lastUserMessage.id;
    requestAnimationFrame(() => {
      document
        .getElementById(lastUserMessage.id)
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, [messages]);

  const ask = async (text) => {
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, type: "user", text },
    ]);
    setSending(true);

    try {
      await streamChat(sessionId, text, (event, data) => {
        if (event === "text") {
          setMessages((prev) => [
            ...prev,
            { id: `a-${Date.now()}`, type: "assistant", text: data.text },
          ]);
          return;
        }

        const cardType = CARD_EVENT_TYPES[event];
        if (cardType) {
          setMessages((prev) => [
            ...prev,
            { id: `a-${Date.now()}`, type: cardType, ...data },
          ]);
          return;
        }

        if (event === "error") {
          setMessages((prev) => [
            ...prev,
            { id: `err-${Date.now()}`, type: "error", text: data.message },
          ]);
        }
      });
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: `err-${Date.now()}`,
          type: "error",
          text: "일시적인 오류가 발생했습니다. 다시 시도해 주세요.",
        },
      ]);
    } finally {
      setSending(false);
    }
  };

  const handleSelectCandidate = (candidate) => {
    ask(`${candidate.name}(${candidate.code}) 기준으로 진행해줘`);
  };

  const handleNewChat = () => {
    // 떠나는 세션은 양쪽을 함께 정리한다 — 서버 컨텍스트(Redis)와 화면 기록.
    // UUID만 새로 만들면 서버 컨텍스트가 TTL 2시간 동안 남는다.
    const leaving = getSessionId();
    endSession(leaving);
    clearTranscript(leaving);
    setMessages([]);
    scrolledUserIdRef.current = null;
    setSessionId(resetSessionId());
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
          <ChatMessageList messages={messages} onSelectCandidate={handleSelectCandidate} />
        </div>
        <ChatInputBar onSend={ask} onSelectSuggestion={ask} disabled={sending} />
      </div>
    </AppLayout>
  );
}
