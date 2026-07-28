import { describe, it, expect, vi, beforeEach } from "vitest";
import { ShortcutApiError, fetchCatalog, searchStocks, fetchShortcuts, createShortcut, updateShortcut, deleteShortcut } from "../shortcutsApi";
import * as deviceIdModule from "../deviceId";

vi.mock("../deviceId");

beforeEach(() => {
  vi.clearAllMocks();
  deviceIdModule.getDeviceId.mockReturnValue("test-device-id");
});

describe("ShortcutApiError", () => {
  it("status와 code를 가진다", () => {
    const error = new ShortcutApiError(409, "KEY_TAKEN", "키 조합이 이미 등록되었습니다");
    expect(error.status).toBe(409);
    expect(error.code).toBe("KEY_TAKEN");
    expect(error.message).toBe("키 조합이 이미 등록되었습니다");
  });
});

describe("fetchCatalog", () => {
  it("성공 응답을 파싱한다", async () => {
    const catalog = [{ toolName: "return_gap", description: "수익률 갭" }];
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(catalog) })
    ));

    const result = await fetchCatalog();
    expect(result).toEqual(catalog);
  });

  it("200 OK이지만 JSON이 아닌 본문을 처리한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.reject(new SyntaxError("Invalid JSON"))
      })
    ));

    await expect(fetchCatalog())
      .rejects.toMatchObject({
        status: 200,
        code: "INVALID_RESPONSE"
      });
  });

  it("실패 응답에서 에러 코드를 추출한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ code: "INVALID_REQUEST", message: "잘못된 요청" })
      })
    ));

    await expect(fetchCatalog()).rejects.toMatchObject({
      status: 400,
      code: "INVALID_REQUEST",
      message: "잘못된 요청"
    });
  });
});

describe("searchStocks", () => {
  it("쿼리를 인코딩해서 요청한다", async () => {
    const stocks = [{ code: "005930", name: "삼성전자" }];
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(stocks) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await searchStocks("삼성", null);

    const callUrl = mockFetch.mock.calls[0][0];
    expect(callUrl).toContain("q=%EC%82%BC%EC%84%B1"); // "삼성" 인코딩됨
    expect(callUrl).toContain("limit=8");
  });

  it("AbortSignal을 전달한다", async () => {
    const stocks = [];
    const controller = new AbortController();
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(stocks) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await searchStocks("test", controller.signal);

    expect(mockFetch.mock.calls[0][1].signal).toBe(controller.signal);
  });
});

describe("fetchShortcuts", () => {
  it("X-Device-Id 헤더를 전송한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await fetchShortcuts();

    const headers = mockFetch.mock.calls[0][1].headers;
    expect(headers["X-Device-Id"]).toBe("test-device-id");
    expect(headers["Content-Type"]).toBe("application/json");
  });
});

describe("createShortcut", () => {
  it("성공하면 결과를 반환한다", async () => {
    const created = { id: "s1", keyCombo: "ctrl+shift+1" };
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(created) })
    );
    vi.stubGlobal("fetch", mockFetch);

    const result = await createShortcut({ keyCombo: "ctrl+shift+1" });

    expect(result).toEqual(created);
  });

  it("409 KEY_TAKEN 에러를 전달한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 409,
        json: () => Promise.resolve({ code: "KEY_TAKEN", message: "키가 이미 등록되었습니다" })
      })
    ));

    await expect(createShortcut({ keyCombo: "ctrl+shift+1" }))
      .rejects.toMatchObject({
        status: 409,
        code: "KEY_TAKEN"
      });
  });

  it("400 TARGET_COUNT_INVALID 에러를 전달한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ code: "TARGET_COUNT_INVALID", message: "종목이 너무 많습니다" })
      })
    ));

    await expect(createShortcut({ targets: [] }))
      .rejects.toMatchObject({
        status: 400,
        code: "TARGET_COUNT_INVALID"
      });
  });

  it("JSON이 아닌 에러 바디를 처리한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 500,
        json: () => Promise.reject(new SyntaxError("Invalid JSON"))
      })
    ));

    await expect(createShortcut({ keyCombo: "ctrl+shift+1" }))
      .rejects.toMatchObject({
        status: 500,
        code: "UNKNOWN"
      });
  });

  it("POST 메서드를 사용한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await createShortcut({ keyCombo: "ctrl+shift+1" });

    const options = mockFetch.mock.calls[0][1];
    expect(options.method).toBe("POST");
  });

  it("X-Device-Id 헤더를 전송한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await createShortcut({ keyCombo: "ctrl+shift+1" });

    const headers = mockFetch.mock.calls[0][1].headers;
    expect(headers["X-Device-Id"]).toBe("test-device-id");
  });
});

describe("updateShortcut", () => {
  it("성공하면 결과를 반환한다", async () => {
    const updated = { id: "s1", keyCombo: "ctrl+shift+2" };
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(updated) })
    );
    vi.stubGlobal("fetch", mockFetch);

    const result = await updateShortcut("s1", { keyCombo: "ctrl+shift+2" });

    expect(result).toEqual(updated);
  });

  it("PUT 메서드를 사용한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await updateShortcut("s1", { keyCombo: "ctrl+shift+2" });

    const options = mockFetch.mock.calls[0][1];
    expect(options.method).toBe("PUT");
  });

  it("URL에 ID를 포함한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) })
    );
    vi.stubGlobal("fetch", mockFetch);

    await updateShortcut("s123", { keyCombo: "ctrl+shift+2" });

    const url = mockFetch.mock.calls[0][0];
    expect(url).toContain("/api/shortcuts/s123");
  });
});

describe("deleteShortcut", () => {
  it("204 No Content 응답을 처리한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 204 })
    );
    vi.stubGlobal("fetch", mockFetch);

    const result = await deleteShortcut("s1");

    expect(result).toBe(null);
  });

  it("DELETE 메서드를 사용한다", async () => {
    const mockFetch = vi.fn(() =>
      Promise.resolve({ ok: true, status: 204 })
    );
    vi.stubGlobal("fetch", mockFetch);

    await deleteShortcut("s1");

    const options = mockFetch.mock.calls[0][1];
    expect(options.method).toBe("DELETE");
  });

  it("실패 응답에서 에러 코드를 추출한다", async () => {
    vi.stubGlobal("fetch", vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ code: "SHORTCUT_NOT_FOUND" })
      })
    ));

    await expect(deleteShortcut("s1"))
      .rejects.toMatchObject({
        status: 404,
        code: "SHORTCUT_NOT_FOUND"
      });
  });
});
