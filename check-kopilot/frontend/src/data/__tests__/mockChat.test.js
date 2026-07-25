import { describe, it, expect } from "vitest";
import { mockStreamChat } from "../mockChat";

async function collect(message) {
  const events = [];
  await mockStreamChat(message, (event, data) => events.push([event, data]));
  return events;
}

describe("mockStreamChat", () => {
  it("지표 카드 시나리오는 card → text → done 순서로 실제 스키마 필드를 채워 보낸다", async () => {
    const events = await collect("삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘");
    expect(events.map(([e]) => e)).toEqual(["card", "text", "done"]);

    const [, card] = events[0];
    expect(card.cardId).toBeTruthy();
    expect(card.from).toBeTruthy();
    expect(card.to).toBeTruthy();
    expect(Array.isArray(card.headline)).toBe(true);
    expect(card.headline[0]).toHaveProperty("label");
    expect(card.headline[0]).toHaveProperty("value");
    expect(card.headline[0]).toHaveProperty("unit");
    expect(["line", "bar"]).toContain(card.chart.chartType);
    expect(card.chart.series[0].points[0]).toHaveProperty("label");
    expect(card.chart.series[0].points[0]).toHaveProperty("value");
    expect(card.evidence.apiCalls[0]).toHaveProperty("specUrl");
    expect(card.evidence.rawData[0]).toHaveProperty("rows");
    expect(card.evidence.steps[0]).toHaveProperty("detail");

    const [, text] = events[1];
    expect(typeof text.text).toBe("string");
  });

  it.each([
    ["KODEX 200 ETF 괴리율 최근 일주일 데이터 보여줘", "ETF_NAV_GAP"],
    ["SK하이닉스랑 삼성전자 변동성 비교해줘", "VOLATILITY"],
    ["네이버 최근 3개월 누적수익률 차트로 보여줘", "PERIOD_SUMMARY"],
  ])("'%s' → %s 카드", async (message, metric) => {
    const events = await collect(message);
    expect(events[0][0]).toBe("card");
    expect(events[0][1].metric).toBe(metric);
  });

  it("모호한 종목명은 clarify(query/candidates)를 보낸다", async () => {
    const events = await collect("삼성 지난주 시세 요약해줘");
    expect(events.map(([e]) => e)).toEqual(["clarify", "text", "done"]);
    const [, clarify] = events[0];
    expect(clarify.query).toBeTruthy();
    expect(clarify.candidates[0]).toEqual(
      expect.objectContaining({ code: expect.any(String), name: expect.any(String), market: expect.any(String) })
    );
  });

  it("카탈로그 밖 질문은 guide(topic/matched/catalog)를 보낸다", async () => {
    const events = await collect("삼성전자 외국인 순매수 동향 알려줘");
    expect(events.map(([e]) => e)).toEqual(["guide", "text", "done"]);
    const [, guide] = events[0];
    expect(guide.topic).toBeTruthy();
    expect(Array.isArray(guide.matched)).toBe(true);
    expect(Array.isArray(guide.catalog)).toBe(true);
  });

  it("완전히 낯선 질문도 guide로 폴백해 빈 화면을 만들지 않는다", async () => {
    const events = await collect("오늘 점심 뭐 먹지");
    expect(events.map(([e]) => e)).toEqual(["guide", "text", "done"]);
    const [, guide] = events[0];
    expect(guide.catalog.length).toBeGreaterThan(0);
  });
});
