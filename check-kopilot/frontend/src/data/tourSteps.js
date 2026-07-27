export function buildTourSteps({ setSidebarOpen }) {
  const isMobile = () => window.innerWidth < 1024;

  return [
    {
      selector: '[data-tour="new-chat"]',
      title: "새 대화는 언제든 여기서",
      description:
        "새로운 주제로 질문하고 싶을 때 이 버튼을 누르면 대화가 새로 시작돼요.",
      beforeShow: () => {
        if (isMobile()) setSidebarOpen(true);
      },
      afterHide: () => {
        if (isMobile()) setSidebarOpen(false);
      },
    },
    {
      selector: '[data-tour="suggested-prompts"]',
      title: "예시 질문으로 빠르게 시작",
      description:
        "자주 찾는 질문 템플릿을 누르면 바로 질문이 전송돼요.",
    },
    {
      selector: '[data-tour="chat-input"]',
      title: "자연어로 편하게 질문하세요",
      description:
        "API 구조나 파라미터를 몰라도 괜찮아요. 원하는 걸 문장으로 물어보면 AI가 알맞은 CHECK API를 찾아 호출합니다.",
    },
    {
      selector: '[data-tour="key-metrics"]',
      title: "핵심 수치는 백엔드가 직접 계산",
      description:
        "LLM이 숫자를 만들어내지 않아요. 화면에 보이는 모든 수치는 백엔드 계산 결과 그대로입니다.",
    },
    {
      selector: '[data-tour="evidence-toggle"]',
      title: "근거까지 투명하게 공개",
      description:
        "이 버튼을 누르면 사용된 CHECK API, 원본 데이터, 계산식, 중간 계산값을 직접 확인할 수 있어요.",
    },
    {
      selector: '[data-tour="excel-download"]',
      title: "Excel로 바로 내보내기",
      description:
        "결과 요약, 원본 데이터, 계산 과정이 담긴 Excel 파일을 한 번에 받아 보고서에 바로 활용하세요.",
    },
  ];
}
