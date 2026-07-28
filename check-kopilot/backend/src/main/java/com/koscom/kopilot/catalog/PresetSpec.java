package com.koscom.kopilot.catalog;

/**
 * 단축키 폼이 지표를 고르고 문장을 만들 때 쓰는 메타.
 *
 * {@code promptTemplate}의 치환 토큰은 {targets}·{period} 둘뿐이고,
 * {period}가 없으면 폼이 기간 셀렉트를 숨긴다 — 별도 플래그를 두지 않는다.
 */
public record PresetSpec(String label, String promptTemplate, int minTargets, int maxTargets) {}
