/**
 * "구현 방법 자세히" 버튼이 다음 턴으로 보낼 질문을 만든다.
 *
 * 두 카드가 같은 버튼을 쓰지만 근거로 삼을 게 다르다. 가이드 카드는 방금 찾아둔 후보 API를,
 * 지표 카드는 자기가 **실제로 호출한** API와 적용한 공식을 안다. 그 값을 질문에 실어 보내야
 * explain_recipe가 키워드로 엉뚱한 API를 집지 않는다 — "수익률 갭"의 '수익률'이 배당수익률
 * 필드에 걸려 기본정보 API를 추천하던 문제(이슈 #62).
 */

const STEPS =
  "①어떤 API를 어떤 파라미터로 호출하는지 ②응답에서 어떤 F코드 필드를 쓰는지 " +
  "③그 값들을 어떤 순서로 계산해 결과를 얻는지 ④호출 예시까지 단계별로 설명해줘.";

/** 가이드 카드 — 이미 찾은 후보 API를 지목해 LLM이 같은 검색을 반복하지 않게 한다 */
export function guideImplementationPrompt(topic, matched = []) {
  const apis = matched.map((api) => api.name).filter(Boolean).join(", ");
  return [
    `'${topic}'을(를) 직접 구현하는 방법을 자세히 알려줘.`,
    apis ? `앞서 찾은 ${apis}를 기준으로,` : "",
    STEPS,
  ]
    .filter(Boolean)
    .join(" ");
}

/**
 * 지표 카드 — 카드가 호출한 API의 apiId와 적용 공식을 그대로 넘긴다.
 * 레시피가 카드 근거 패널과 다른 API를 가리키면 같은 화면 안에서 서로 모순된다.
 */
export function indicatorImplementationPrompt(message) {
  const { title, evidence } = message ?? {};
  const calls = evidence?.apiCalls ?? [];
  const apis = calls
    .map((call) => (call.apiId ? `${call.api}(${call.apiId})` : call.api))
    .filter(Boolean)
    .join(", ");

  return [
    `방금 답변한 '${title}'을(를) CHECK API로 직접 구현하는 방법을 자세히 알려줘.`,
    apis ? `이 카드가 실제로 호출한 API는 ${apis}이고,` : "",
    evidence?.formula ? `적용한 공식은 "${evidence.formula}"야.` : "",
    "이 API와 공식을 기준으로",
    STEPS,
  ]
    .filter(Boolean)
    .join(" ");
}
