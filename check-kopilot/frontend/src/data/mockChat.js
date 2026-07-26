/**
 * 백엔드 미배포 기간 임시 데모용 mock 응답.
 * 스키마는 docs/api.md(MetricResult/ClarifyData/GuideData)를 그대로 따른다 —
 * 실제 백엔드가 배포되면 lib/sse.js의 폴백 경로만 지우면 된다.
 */

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const CARDS = {
  returnGap: {
    card: {
      cardId: "mock-return-gap-0001",
      metric: "RETURN_GAP",
      title: "삼성전자 vs 코스피 수익률 갭 (최근 1개월)",
      from: "2026-06-25",
      to: "2026-07-25",
      targets: [
        { code: "005930", name: "삼성전자" },
        { code: "KOSPI", name: "코스피" },
      ],
      headline: [
        { label: "삼성전자 기간수익률", value: 8.42, unit: "%" },
        { label: "코스피 기간수익률", value: 3.15, unit: "%" },
        { label: "수익률 갭", value: 5.27, unit: "%p" },
      ],
      chart: {
        chartType: "line",
        series: [
          {
            name: "삼성전자",
            points: [
              { label: "06/25", value: 0 },
              { label: "06/30", value: 1.2 },
              { label: "07/05", value: 2.8 },
              { label: "07/10", value: 3.5 },
              { label: "07/15", value: 5.1 },
              { label: "07/20", value: 6.9 },
              { label: "07/25", value: 8.42 },
            ],
          },
          {
            name: "코스피",
            points: [
              { label: "06/25", value: 0 },
              { label: "06/30", value: 0.4 },
              { label: "07/05", value: 1.1 },
              { label: "07/10", value: 0.8 },
              { label: "07/15", value: 1.9 },
              { label: "07/20", value: 2.5 },
              { label: "07/25", value: 3.15 },
            ],
          },
        ],
      },
      evidence: {
        apiCalls: [
          { api: "주식 일별 시세", request: "005930 / 2026-06-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
          { api: "지수 일별 시세", request: "KOSPI / 2026-06-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/index-daily" },
        ],
        rawData: [
          {
            name: "삼성전자",
            rows: [
              { date: "2026-06-25", value: 71500 },
              { date: "2026-07-10", value: 74000 },
              { date: "2026-07-25", value: 77520 },
            ],
          },
          {
            name: "코스피",
            rows: [
              { date: "2026-06-25", value: 2712.4 },
              { date: "2026-07-10", value: 2734.1 },
              { date: "2026-07-25", value: 2797.9 },
            ],
          },
        ],
        formula: "수익률 갭 = 기간수익률(삼성전자) − 기간수익률(코스피)",
        steps: [
          { label: "삼성전자 기간수익률", detail: "(77520 / 71500 − 1) × 100 = 8.42%" },
          { label: "코스피 기간수익률", detail: "(2797.9 / 2712.4 − 1) × 100 = 3.15%" },
          { label: "수익률 갭", detail: "8.42% − 3.15% = 5.27%p" },
        ],
      },
    },
    commentary:
      "최근 1개월간 삼성전자는 코스피 대비 5.27%p 초과 수익률을 기록했습니다. 특히 7월 둘째 주 이후 격차가 확대되는 흐름입니다.",
  },

  volatility: {
    card: {
      cardId: "mock-volatility-0001",
      metric: "VOLATILITY",
      title: "SK하이닉스 vs 삼성전자 변동성 비교 (최근 1개월)",
      from: "2026-06-25",
      to: "2026-07-25",
      targets: [
        { code: "000660", name: "SK하이닉스" },
        { code: "005930", name: "삼성전자" },
      ],
      headline: [
        { label: "SK하이닉스 연환산 변동성", value: 24.8, unit: "%" },
        { label: "삼성전자 연환산 변동성", value: 21.3, unit: "%" },
        { label: "변동성 차이", value: 3.5, unit: "%p" },
      ],
      chart: {
        chartType: "bar",
        series: [
          {
            name: "연환산 변동성",
            points: [
              { label: "SK하이닉스", value: 24.8 },
              { label: "삼성전자", value: 21.3 },
            ],
          },
        ],
      },
      evidence: {
        apiCalls: [
          { api: "주식 일별 시세", request: "000660 / 2026-06-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
          { api: "주식 일별 시세", request: "005930 / 2026-06-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
        ],
        rawData: [
          {
            name: "SK하이닉스",
            rows: [
              { date: "2026-06-25", value: 218000 },
              { date: "2026-07-10", value: 231000 },
              { date: "2026-07-25", value: 226500 },
            ],
          },
          {
            name: "삼성전자",
            rows: [
              { date: "2026-06-25", value: 71500 },
              { date: "2026-07-10", value: 74000 },
              { date: "2026-07-25", value: 77520 },
            ],
          },
        ],
        formula: "변동성(연환산) = 일별수익률 표본표준편차 × √252 × 100",
        steps: [
          { label: "SK하이닉스 변동성", detail: "일별수익률 표준편차 × √252 = 24.8%" },
          { label: "삼성전자 변동성", detail: "일별수익률 표준편차 × √252 = 21.3%" },
          { label: "변동성 차이", detail: "24.8% − 21.3% = 3.5%p" },
        ],
      },
    },
    commentary:
      "최근 1개월 기준 SK하이닉스가 삼성전자보다 변동성이 3.5%p 더 높습니다. 두 종목 모두 반도체 업황 이슈에 민감하게 반응한 구간입니다.",
  },

  etfGap: {
    card: {
      cardId: "mock-etf-gap-0001",
      metric: "ETF_NAV_GAP",
      title: "KODEX 200 ETF 괴리율 (최근 1주일)",
      from: "2026-07-18",
      to: "2026-07-25",
      targets: [{ code: "069500", name: "KODEX 200" }],
      headline: [
        { label: "시장가", value: 131010, unit: "원" },
        { label: "NAV(iNAV)", value: 130791.06, unit: "원" },
        { label: "괴리율", value: 0.17, unit: "%" },
      ],
      chart: {
        chartType: "line",
        series: [
          {
            name: "시장가",
            points: [
              { label: "07/18", value: 129800 },
              { label: "07/21", value: 130200 },
              { label: "07/23", value: 130600 },
              { label: "07/25", value: 131010 },
            ],
          },
          {
            name: "NAV(iNAV)",
            points: [
              { label: "07/18", value: 129750 },
              { label: "07/21", value: 130100 },
              { label: "07/23", value: 130480 },
              { label: "07/25", value: 130791.06 },
            ],
          },
        ],
      },
      evidence: {
        apiCalls: [
          { api: "주식 일별 시세 (ETP지표가치 포함)", request: "069500 / 2026-07-18~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
        ],
        rawData: [
          {
            name: "시장가",
            rows: [
              { date: "2026-07-18", value: 129800 },
              { date: "2026-07-25", value: 131010 },
            ],
          },
          {
            name: "NAV(iNAV)",
            rows: [
              { date: "2026-07-18", value: 129750 },
              { date: "2026-07-25", value: 130791.06 },
            ],
          },
        ],
        formula: "괴리율(%) = (시장가 − NAV) / NAV × 100",
        steps: [
          { label: "괴리율", detail: "(131010 − 130791.06) / 130791.06 × 100 = 0.17%" },
        ],
      },
    },
    commentary:
      "KODEX 200의 현재 시장가가 NAV 대비 0.17% 높게 거래되고 있습니다. 최근 1주일간 괴리율은 안정적인 범위에서 움직였습니다.",
  },

  periodSummary: {
    card: {
      cardId: "mock-period-summary-0001",
      metric: "PERIOD_SUMMARY",
      title: "네이버 기간 시세 요약 (최근 3개월)",
      from: "2026-04-25",
      to: "2026-07-25",
      targets: [{ code: "035420", name: "네이버" }],
      headline: [
        { label: "최고가", value: 245500, unit: "원" },
        { label: "최저가", value: 198000, unit: "원" },
        { label: "기간수익률", value: 12.4, unit: "%" },
      ],
      chart: {
        chartType: "line",
        series: [
          {
            name: "네이버",
            points: [
              { label: "04/25", value: 0 },
              { label: "05/25", value: 4.1 },
              { label: "06/25", value: 7.8 },
              { label: "07/25", value: 12.4 },
            ],
          },
        ],
      },
      evidence: {
        apiCalls: [
          { api: "주식 일별 시세", request: "035420 / 2026-04-25~2026-07-25", specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily" },
        ],
        rawData: [
          {
            name: "네이버",
            rows: [
              { date: "2026-04-25", value: 218000 },
              { date: "2026-06-25", value: 235000 },
              { date: "2026-07-25", value: 245000 },
            ],
          },
        ],
        formula: "기간수익률 = (마지막 종가 / 첫 종가 − 1) × 100",
        steps: [
          { label: "기간수익률", detail: "(245000 / 218000 − 1) × 100 = 12.4%" },
        ],
      },
    },
    commentary:
      "최근 3개월간 네이버는 12.4% 상승했으며, 7월 중순 245,500원까지 오르며 기간 내 최고가를 기록했습니다.",
  },
};

const CLARIFY = {
  query: "삼성",
  candidates: [
    { code: "005930", name: "삼성전자", market: "KOSPI" },
    { code: "009150", name: "삼성전기", market: "KOSPI" },
    { code: "018260", name: "삼성SDS", market: "KOSPI" },
  ],
};
const CLARIFY_COMMENTARY = "'삼성'으로 검색되는 종목이 여러 개예요. 위 후보 중 하나를 선택해 주세요.";

const CATALOG_LINES = [
  { apiId: "RETURN_GAP", name: "수익률 갭", summary: "두 대상(종목/지수)의 기간 수익률 차이" },
  { apiId: "VOLATILITY", name: "변동성 비교", summary: "일간수익률 표준편차, 연율화" },
  { apiId: "ETF_NAV_GAP", name: "괴리율", summary: "ETF 시장가 vs NAV(iNAV)" },
  { apiId: "MA_DEVIATION", name: "이동평균 이격도", summary: "현재가 / N일 이동평균 − 1" },
  { apiId: "RETURN_RANK", name: "상대수익률 랭킹", summary: "나열한 복수 종목의 기간 수익률 정렬" },
  { apiId: "PERIOD_SUMMARY", name: "기간 시세 요약", summary: "기간 OHLC 집계" },
];

function foreignNetBuyGuide(topic) {
  return {
    topic,
    matched: [
      {
        apiId: "stock-investor",
        name: "투자자별 매매동향",
        path: "/stock/m001/invest_trend",
        summary: "종목별 기관·외국인·개인 순매수 추이",
        params: [
          { name: "code", required: true },
          { name: "fromDate", required: true },
          { name: "toDate", required: true },
        ],
        docUrl: "https://checkapi.koscom.co.kr/spec/stock-investor",
        fields: [
          { code: "F0001", label: "외국인 순매수" },
          { code: "F0002", label: "기관 순매수" },
        ],
      },
    ],
    catalog: [{ apiId: "stock-investor", name: "투자자별 매매동향", summary: "종목별 순매수 추이" }],
  };
}

function genericGuide(topic) {
  return { topic, matched: [], catalog: CATALOG_LINES };
}
const GUIDE_COMMENTARY = "요청하신 지표는 현재 카탈로그에 없어, 대신 조합해 쓸 수 있는 CHECK API를 안내해 드렸습니다.";

function pickScenario(message) {
  const text = message.trim();
  if (/코스피/.test(text) && /(갭|수익률)/.test(text) && /삼성전자/.test(text)) return "returnGap";
  if (/괴리율/.test(text)) return "etfGap";
  if (/변동성/.test(text)) return "volatility";
  if (/수익률|시세|차트/.test(text) && !/삼성/.test(text)) return "periodSummary";
  if (/삼성/.test(text) && !/삼성전자/.test(text)) return "clarify";
  if (/순매수|수급/.test(text)) return "foreignGuide";
  return "guide";
}

/**
 * 실제 백엔드가 배포되지 않았을 때 SSE 스트림을 흉내낸다.
 * 이벤트 순서·필드는 docs/api.md와 동일 — 배포 후에는 lib/sse.js의 폴백 호출만 제거하면 된다.
 */
export async function mockStreamChat(message, onEvent) {
  const scenario = pickScenario(message);
  await delay(400);

  if (scenario === "clarify") {
    onEvent("clarify", CLARIFY);
    await delay(500);
    onEvent("text", { text: CLARIFY_COMMENTARY });
    await delay(150);
    onEvent("done", {});
    return;
  }

  if (scenario === "guide" || scenario === "foreignGuide") {
    const guide = scenario === "foreignGuide" ? foreignNetBuyGuide(message) : genericGuide(message);
    onEvent("guide", guide);
    await delay(500);
    onEvent("text", { text: GUIDE_COMMENTARY });
    await delay(150);
    onEvent("done", {});
    return;
  }

  const { card, commentary } = CARDS[scenario];
  onEvent("card", card);
  await delay(600);
  onEvent("text", { text: commentary });
  await delay(150);
  onEvent("done", {});
}
