// 튜토리얼 4~6단계(핵심 수치·근거·xlsx 다운로드)는 실제 지표 카드 위에서만 설명할 수 있다.
// 빈 대화 화면에는 카드가 없으므로, 투어가 열려 있는 동안만 보여줄 예시 데이터다.
export const tourDemoCard = {
  cardId: "tour-demo",
  title: "삼성전자 vs 코스피 수익률 갭 (예시)",
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
          { label: "07/10", value: 5.1 },
          { label: "07/25", value: 8.42 },
        ],
      },
      {
        name: "코스피",
        points: [
          { label: "06/25", value: 0 },
          { label: "07/10", value: 1.8 },
          { label: "07/25", value: 3.15 },
        ],
      },
    ],
  },
  commentary:
    "이 카드는 튜토리얼 예시입니다. 실제 대화에서는 질문하신 종목과 기간에 맞춰 계산됩니다.",
  evidence: {
    apiCalls: [
      {
        apiId: "stock-daily",
        api: "주식 일별 시세",
        request: "005930",
        specUrl: "https://checkapi.koscom.co.kr/spec/stock-daily",
      },
    ],
    rawData: [
      {
        name: "삼성전자",
        rows: [
          { date: "2026-06-25", value: 71500 },
          { date: "2026-07-25", value: 77500 },
        ],
      },
    ],
    formula: "수익률 갭 = 기간수익률(삼성전자) − 기간수익률(코스피)",
    steps: [{ label: "수익률 갭", detail: "8.42% − 3.15% = 5.27%p" }],
  },
};
