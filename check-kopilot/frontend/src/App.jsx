import ChatPage from "./pages/ChatPage";
import AdminPage from "./admin/AdminPage";
import { tokenFromHash } from "./admin/adminApi";

// react-router 없이 해시 라우팅 1분기 — 데모 규모에 의존성 추가는 과설계
function App() {
  const isAdmin = window.location.hash.startsWith("#/admin");
  return isAdmin ? <AdminPage token={tokenFromHash(window.location.hash)} /> : <ChatPage />;
}

export default App;
