import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import AdminPage from "../AdminPage";

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn((url) => {
      if (String(url).includes("/stats")) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              questionCount: 20,
              cardCount: 14,
              guideCount: 6,
              catalogCoverageRate: 70,
            }),
        });
      }
      return Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve([
            {
              topic: "외국인 수급",
              requestCount: 5,
              explicitCount: 2,
              sessionCount: 4,
              matchedApiIds: "stock-investor",
              lastAt: "2026-07-22 10:00:00",
            },
          ]),
      });
    })
  );
});

describe("AdminPage", () => {
  it("통계와 수요 상위 항목을 렌더한다", async () => {
    render(<AdminPage token="test-token" />);
    await waitFor(() => expect(screen.getByText("외국인 수급")).toBeInTheDocument());
    expect(screen.getByText(/카탈로그 응답률/)).toBeInTheDocument();
    expect(screen.getByText("70%")).toBeInTheDocument();
    expect(screen.getByText("stock-investor")).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      "/api/admin/demand/summary?limit=50",
      expect.objectContaining({ headers: { "X-Admin-Token": "test-token" } })
    );
  });

  it("토큰이 없으면 접근 안내만 보여준다", () => {
    render(<AdminPage token="" />);
    expect(screen.getByText(/접근 권한이 없습니다/)).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("API가 실패하면(잘못된 토큰) 접근 안내만 보여준다", async () => {
    fetch.mockImplementation(() => Promise.resolve({ ok: false }));
    render(<AdminPage token="wrong-token" />);
    await waitFor(() => expect(screen.getByText(/접근 권한이 없습니다/)).toBeInTheDocument());
  });
});
