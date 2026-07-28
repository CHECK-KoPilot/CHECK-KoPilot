import { randomUUID } from "./uuid";

const KEY = "kopilot.deviceId";

/**
 * 단축키 프리셋의 소유자 키.
 *
 * sessionId는 "새 대화"마다 새로 발급되므로 프리셋을 묶을 수 없다.
 * 로그인이 없는 MVP에서 "이 브라우저"를 가리키는 값이 따로 필요하다.
 */
export function getDeviceId() {
  const saved = localStorage.getItem(KEY);
  if (saved) return saved;
  const fresh = randomUUID();
  localStorage.setItem(KEY, fresh);
  return fresh;
}
