import { getDeviceId } from "./deviceId";

/** 서버가 내려준 code로 화면이 분기할 수 있게 상태코드와 함께 실어 나른다. */
export class ShortcutApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function deviceHeaders() {
  return { "Content-Type": "application/json", "X-Device-Id": getDeviceId() };
}

async function parse(res) {
  if (res.ok) {
    if (res.status === 204) return null;
    try {
      return await res.json();
    } catch {
      // 200 OK인데 본문이 JSON이 아닐 수도 있다 — 에러로 취급해 알린다
      throw new ShortcutApiError(res.status, "INVALID_RESPONSE", "응답 형식이 잘못되었습니다");
    }
  }
  let body = {};
  try {
    body = await res.json();
  } catch {
    // 에러 바디가 JSON이 아닐 수 있다 — 상태코드만으로도 화면은 분기할 수 있다
  }
  throw new ShortcutApiError(res.status, body.code ?? "UNKNOWN", body.message ?? "요청에 실패했습니다");
}

export async function fetchCatalog() {
  return parse(await fetch("/api/catalog"));
}

export async function searchStocks(query, signal) {
  const q = encodeURIComponent(query);
  return parse(await fetch(`/api/stocks?q=${q}&limit=8`, { signal }));
}

export async function fetchShortcuts() {
  return parse(await fetch("/api/shortcuts", { headers: deviceHeaders() }));
}

export async function createShortcut(body) {
  return parse(await fetch("/api/shortcuts", {
    method: "POST", headers: deviceHeaders(), body: JSON.stringify(body),
  }));
}

export async function updateShortcut(id, body) {
  return parse(await fetch(`/api/shortcuts/${id}`, {
    method: "PUT", headers: deviceHeaders(), body: JSON.stringify(body),
  }));
}

export async function deleteShortcut(id) {
  return parse(await fetch(`/api/shortcuts/${id}`, {
    method: "DELETE", headers: deviceHeaders(),
  }));
}
