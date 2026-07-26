import { useEffect, useMemo, useRef, useState } from "react";
import AppLayout from "../components/layout/AppLayout";
import ChatMessageList from "../components/chat/ChatMessageList";
import ChatInputBar from "../components/chat/ChatInputBar";
import LoadingScreen from "../components/common/LoadingScreen";
import ProductTour from "../components/tour/ProductTour";
import { buildTourSteps } from "../data/tourSteps";
import { streamChat } from "../lib/sse";
import { getSessionId, resetSessionId } from "../lib/session";

const CARD_EVENT_TYPES = {
  card: "indicator",
  clarify: "clarification",
  guide: "guide",
};

const LOADING_DURATION_MS = 1100;

export default function ChatPage() {
  const [messages, setMessages] = useState([]);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [tourOpen, setTourOpen] = useState(false);
  const [tourCardId, setTourCardId] = useState(null);
  const scrolledUserIdRef = useRef(null);
  const tourCardIdRef = useRef(null);

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), LOADING_DURATION_MS);
    return () => clearTimeout(timer);
  }, []);

  const tourSteps = useMemo(() => buildTourSteps({ setSidebarOpen }), []);

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
      await streamChat(getSessionId(), text, (event, data) => {
        if (event === "text") {
          setMessages((prev) => [
            ...prev,
            { id: `a-${Date.now()}`, type: "assistant", text: data.text },
          ]);
          return;
        }

        const cardType = CARD_EVENT_TYPES[event];
        if (cardType) {
          const id = `a-${Date.now()}`;
          if (cardType === "indicator" && !tourCardIdRef.current) {
            tourCardIdRef.current = id;
            setTourCardId(id);
          }
          setMessages((prev) => [...prev, { id, type: cardType, ...data }]);
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
    setMessages([]);
    scrolledUserIdRef.current = null;
    tourCardIdRef.current = null;
    setTourCardId(null);
    resetSessionId();
  };

  if (loading) {
    return <LoadingScreen durationMs={LOADING_DURATION_MS} />;
  }

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
            onStartTour={() => setTourOpen(true)}
            tourCardId={tourCardId}
          />
        </div>
        <ChatInputBar onSend={ask} onSelectSuggestion={ask} disabled={sending} />
      </div>
      {tourOpen && (
        <ProductTour steps={tourSteps} onClose={() => setTourOpen(false)} />
      )}
    </AppLayout>
  );
}
