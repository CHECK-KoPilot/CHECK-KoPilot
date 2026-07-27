import { useEffect, useState } from "react";
import ChatPage from "./pages/ChatPage";
import AdminPage from "./admin/AdminPage";
import { tokenFromHash } from "./admin/adminApi";

// react-router 없이 해시 라우팅 1분기 — 데모 규모에 의존성 추가는 과설계.
// 다만 해시를 렌더 시점에 한 번만 읽으면 주소창으로 #/admin에 들어가도 새로고침 전엔
// 화면이 그대로여서, hashchange를 구독해 다시 그린다.
function App() {
  const [hash, setHash] = useState(() => window.location.hash);

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash);
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  return hash.startsWith("#/admin") ? <AdminPage token={tokenFromHash(hash)} /> : <ChatPage />;
}

export default App;
