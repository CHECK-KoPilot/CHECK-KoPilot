import { useEffect, useRef, useState } from "react";
import AppLayout from "../components/layout/AppLayout";
import ChatMessageList from "../components/chat/ChatMessageList";
import ChatInputBar from "../components/chat/ChatInputBar";
import { streamChat, endSession } from "../lib/sse";
import {
  getSessionId,
  resetSessionId,
  setSessionId as persistSessionId,
} from "../lib/session";
import { loadTranscript, saveTranscript, clearTranscript } from "../lib/transcript";
import {
  listConversations,
  touchConversation,
  removeConversation,
} from "../lib/conversations";

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
  const [conversations, setConversations] = useState(listConversations);
  const scrollRef = useRef(null);
  const pinnedUserIdRef = useRef(null);
  const followingRef = useRef(false);

  useEffect(() => {
    saveTranscript(sessionId, messages);
  }, [sessionId, messages]);

  // 첫 질문을 그 대화의 제목으로 삼는다. 질문이 하나도 없는 대화는 인덱스에 올리지 않는다 —
  // "새 대화"만 눌러도 빈 항목이 목록에 쌓이면 안 된다.
  useEffect(() => {
    const firstQuestion = messages.find((m) => m.type === "user");
    if (!firstQuestion) return;
    setConversations(touchConversation(sessionId, firstQuestion.text));
  }, [sessionId, messages]);

  // 사용자가 직접 스크롤하면 따라가기를 멈춘다. scroll 이벤트는 아래 프로그램 스크롤도
  // 똑같이 발생시키므로, 사람이 움직였다는 게 확실한 입력 제스처만 본다.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const release = () => { followingRef.current = false; };
    el.addEventListener("wheel", release, { passive: true });
    el.addEventListener("touchmove", release, { passive: true });
    return () => {
      el.removeEventListener("wheel", release);
      el.removeEventListener("touchmove", release);
    };
  }, []);

  // 진행 중인 턴의 질문 말풍선을 화면 위쪽에 붙여 둔다.
  // 질문이 붙는 순간엔 아래에 아무것도 없어 말풍선이 맨 아래에 걸치는데, 한 번만 스크롤하면
  // 몇 초 뒤 도착한 카드가 화면 밖에 남는다. 답변이 쌓이는 동안 위치를 다시 잡아 준다.
  useEffect(() => {
    const lastUserMessage = [...messages].reverse().find((m) => m.type === "user");
    if (!lastUserMessage) return;
    if (lastUserMessage.id !== pinnedUserIdRef.current) {
      pinnedUserIdRef.current = lastUserMessage.id;
      followingRef.current = true;
    }
    if (!followingRef.current) return;
    // smooth로 걸면 레이아웃이 확정되기 전에 시작해 뒤늦게 화면이 튄다 — 즉시 이동한다.
    requestAnimationFrame(() => {
      document.getElementById(lastUserMessage.id)?.scrollIntoView({ block: "start" });
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

  /** 대화를 옮길 때 진행 중이던 턴의 스크롤 추적을 놓아준다 */
  const openConversation = (id, restored) => {
    setMessages(restored);
    pinnedUserIdRef.current = null;
    followingRef.current = false;
    setSessionId(id);
  };

  const handleNewChat = () => {
    // 떠나는 대화는 지우지 않는다 — 사이드바 목록에 남겨 두고 다시 열 수 있어야 한다.
    // 서버 컨텍스트(Redis)도 그대로 두면 TTL 2시간 안에 돌아왔을 때 AI가 앞 대화를 기억한다.
    openConversation(resetSessionId(), []);
  };

  const handleSelectConversation = (id) => {
    if (id === sessionId) return;
    const restored = loadTranscript(id);
    // 본문이 사라진 항목(용량 정리 등)은 목록에 남겨 둘 이유가 없다
    if (restored.length === 0) {
      setConversations(removeConversation(id));
      return;
    }
    openConversation(persistSessionId(id), restored);
  };

  const handleDeleteConversation = (id) => {
    // 지울 때만 서버 컨텍스트까지 함께 정리한다
    endSession(id);
    clearTranscript(id);
    setConversations(removeConversation(id));
    if (id === sessionId) handleNewChat();
  };

  return (
    <AppLayout
      headerTitle="새 대화"
      sidebarOpen={sidebarOpen}
      onSidebarOpenChange={setSidebarOpen}
      onNewChat={handleNewChat}
      conversations={conversations}
      activeConversationId={sessionId}
      onSelectConversation={handleSelectConversation}
      onDeleteConversation={handleDeleteConversation}
    >
      <div className="flex h-full flex-col">
        <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto">
          <ChatMessageList
            messages={messages}
            onSelectCandidate={handleSelectCandidate}
            onAskFollowUp={ask}
          />
        </div>
        <ChatInputBar onSend={ask} onSelectSuggestion={ask} disabled={sending} />
      </div>
    </AppLayout>
  );
}
