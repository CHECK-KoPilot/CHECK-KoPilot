import { useState } from "react";
import SidebarNav from "./SidebarNav";
import GlobalHeader from "./GlobalHeader";
import ComplianceFooterBar from "./ComplianceFooterBar";

export default function AppLayout({
  headerTitle,
  children,
  sidebarOpen: controlledOpen,
  onSidebarOpenChange,
  onNewChat,
  conversations,
  activeConversationId,
  onSelectConversation,
  onDeleteConversation,
}) {
  const [internalOpen, setInternalOpen] = useState(false);
  const isControlled = controlledOpen !== undefined && !!onSidebarOpenChange;
  const sidebarOpen = isControlled ? controlledOpen : internalOpen;
  const setSidebarOpen = isControlled ? onSidebarOpenChange : setInternalOpen;

  return (
    <div className="flex h-[100dvh] w-full bg-white text-slate-800">
      <SidebarNav
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onNewChat={onNewChat}
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelectConversation={onSelectConversation}
        onDeleteConversation={onDeleteConversation}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <GlobalHeader
          title={headerTitle}
          onMenuClick={() => setSidebarOpen(true)}
        />
        <div className="min-h-0 flex-1">{children}</div>
        <ComplianceFooterBar />
      </div>
    </div>
  );
}
