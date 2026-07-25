// 채팅 화면 프로토타입용 목업 데이터
// 실제로는 백엔드가 CHECK API 조회 + 계산을 수행한 결과를 그대로 내려준다는 가정의 형태를 따른다.

export const mockMessages = [
  {
    id: "u1",
    type: "user",
    text: "삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘",
  },
  {
    id: "a1",
    type: "indicator",
    title: "삼성전자 vs KOSPI · 수익률 갭",
    period: "2026.06.25 ~ 2026.07.25 (최근 1개월)",
    metrics: [
      { label: "삼성전자 기간수익률", value: 8.42, unit: "%" },
      { label: "KOSPI 기간수익률", value: 3.15, unit: "%" },
      { label: "수익률 갭", value: 5.27, unit: "%p", highlight: true },
    ],
    chart: {
      type: "line",
      series: [
        { name: "삼성전자", color: "up" },
        { name: "KOSPI", color: "down" },
      ],
      data: [
        { date: "06/25", 삼성전자: 0, KOSPI: 0 },
        { date: "06/30", 삼성전자: 1.2, KOSPI: 0.4 },
        { date: "07/05", 삼성전자: 2.8, KOSPI: 1.1 },
        { date: "07/10", 삼성전자: 3.5, KOSPI: 0.8 },
        { date: "07/15", 삼성전자: 5.1, KOSPI: 1.9 },
        { date: "07/20", 삼성전자: 6.9, KOSPI: 2.5 },
        { date: "07/25", 삼성전자: 8.42, KOSPI: 3.15 },
      ],
    },
    commentary:
      "최근 1개월간 삼성전자는 KOSPI 대비 5.27%p 초과 수익률을 기록했습니다. 특히 7월 둘째 주 이후 격차가 확대되는 흐름입니다.",
    evidence: {
      apis: [
        {
          name: "CHECK 주식 일별시세 조회 API",
          endpoint: "GET /check/v1/stock/daily-price",
          docUrl: "#",
        },
        {
          name: "CHECK 지수 일별시세 조회 API",
          endpoint: "GET /check/v1/index/daily-price",
          docUrl: "#",
        },
      ],
      formula: "수익률 갭 = 기간수익률(삼성전자) − 기간수익률(KOSPI)",
      steps: [
        "기간수익률(삼성전자) = (7/25 종가 − 6/25 종가) / 6/25 종가 × 100 = 8.42%",
        "기간수익률(KOSPI) = (7/25 종가 − 6/25 종가) / 6/25 종가 × 100 = 3.15%",
        "수익률 갭 = 8.42% − 3.15% = 5.27%p",
      ],
      rawDataPreview: [
        { date: "2026-06-25", 삼성전자: 71500, KOSPI: 2712.4 },
        { date: "2026-07-10", 삼성전자: 74000, KOSPI: 2734.1 },
        { date: "2026-07-25", 삼성전자: 77520, KOSPI: 2797.9 },
      ],
    },
  },
  {
    id: "u2",
    type: "user",
    text: "삼성 지난주 시세 요약해줘",
  },
  {
    id: "a2",
    type: "clarification",
    text: "‘삼성’으로 검색되는 종목이 여러 개입니다. 어떤 종목을 말씀하시는 건가요?",
    candidates: [
      { code: "005930", name: "삼성전자" },
      { code: "009150", name: "삼성전기" },
      { code: "018260", name: "삼성SDS" },
    ],
  },
  {
    id: "u3",
    type: "user",
    text: "삼성전자 외국인 순매수 동향 알려줘",
  },
  {
    id: "a3",
    type: "guide",
    title: "‘외국인 순매수 동향’은 현재 카탈로그에 없는 지표입니다",
    description:
      "대신 아래 CHECK API를 조합하면 동일한 데이터를 직접 산출할 수 있습니다.",
    apis: [
      {
        name: "CHECK 투자자별 매매동향 API",
        endpoint: "GET /check/v1/stock/investor-trend",
        docUrl: "#",
        params: ["종목코드", "조회시작일", "조회종료일", "투자자구분(외국인)"],
      },
    ],
    recipe: [
      "투자자별 매매동향 API를 종목코드=005930, 투자자구분=외국인으로 호출",
      "일자별 순매수수량(매수수량 − 매도수량) 필드를 추출",
      "조회 기간 내 값을 누적 합산하여 순매수 동향 추이 산출",
    ],
    requested: false,
  },
  {
    id: "u4",
    type: "user",
    text: "삼성전자 지금 사야 돼?",
  },
  {
    id: "a4",
    type: "indicator",
    complianceBanner:
      "투자 판단·매수 추천은 제공하지 않습니다. 대신 참고할 수 있는 사실 기반 지표를 보여드립니다.",
    title: "삼성전자 · 참고 지표",
    period: "2026.06.25 ~ 2026.07.25 (최근 1개월)",
    metrics: [
      { label: "기간수익률", value: 8.42, unit: "%" },
      { label: "변동성 (연환산)", value: 21.3, unit: "%" },
      { label: "KOSPI 대비 초과수익률", value: 5.27, unit: "%p", highlight: true },
    ],
    chart: {
      type: "line",
      series: [{ name: "삼성전자", color: "up" }],
      data: [
        { date: "06/25", 삼성전자: 0 },
        { date: "07/05", 삼성전자: 2.8 },
        { date: "07/15", 삼성전자: 5.1 },
        { date: "07/25", 삼성전자: 8.42 },
      ],
    },
    commentary:
      "최근 1개월 기준 수익률과 변동성 지표입니다. 투자 결정은 본인의 판단과 책임 하에 이루어져야 합니다.",
    evidence: {
      apis: [
        {
          name: "CHECK 주식 일별시세 조회 API",
          endpoint: "GET /check/v1/stock/daily-price",
          docUrl: "#",
        },
      ],
      formula: "변동성(연환산) = 일별수익률 표준편차 × √252 × 100",
      steps: [
        "최근 1개월 일별 종가로 일별수익률 산출",
        "일별수익률의 표준편차 계산",
        "연환산 = 표준편차 × √252 = 21.3%",
      ],
      rawDataPreview: [
        { date: "2026-06-25", 삼성전자: 71500 },
        { date: "2026-07-25", 삼성전자: 77520 },
      ],
    },
  },
];

export const suggestedPrompts = [
  "삼성전자랑 코스피, 최근 한 달 수익률 갭 알려줘",
  "KODEX 200 ETF 괴리율 최근 일주일 데이터 보여줘",
  "SK하이닉스랑 삼성전자 변동성 비교해줘",
  "네이버 최근 3개월 누적수익률 차트로 보여줘",
];

// 백엔드 연동 전까지, 자유 입력에 대한 응답을 흉내내기 위한 데모 응답 템플릿 4종
// (지표 답변 / 되묻기 / 가이드 / 컴플라이언스 배너 포함 지표)
export const demoResponseTemplates = mockMessages.filter(
  (m) => m.type !== "user"
);
