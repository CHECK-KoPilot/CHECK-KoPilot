package com.koscom.kopilot.guide;

import java.util.*;

/**
 * 가이드(레시피) 모드의 검색 담당.
 * LLM에게 776개 API를 보여주지 않는다 — 여기서 3~5개로 좁혀 넘기고, LLM은 레시피 문장만 쓴다.
 */
public class GuideService {

    private static final int TOP_N = 5;
    private static final int RUNNER_UP_N = 10;

    public record CatalogLine(String apiId, String name, String summary) {}
    public record GuideResult(String topic, List<ApiSpecEntry> matched,
                              List<CatalogLine> catalog, List<String> usedKeywords) {}

    private final ApiSpecIndex index;
    private final FieldDictionary dict;

    public GuideService(ApiSpecIndex index, FieldDictionary dict) {
        this.index = index;
        this.dict = dict;
    }

    public GuideResult recipeContext(String topic, List<String> keywords) {
        List<String> terms = dict.expand(keywords == null ? List.of() : keywords);
        Set<String> codes = dict.search(terms);
        if (codes.isEmpty()) return new GuideResult(topic, List.of(), List.of(), terms);

        record Scored(ApiSpecIndex.Raw raw, List<String> hits, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (ApiSpecIndex.Raw r : index.raws()) {
            List<String> hits = r.codes().stream().filter(codes::contains).toList();
            if (hits.isEmpty()) continue;
            scored.add(new Scored(r, hits, score(r, hits.size(), topic)));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        List<ApiSpecEntry> matched = scored.stream().limit(TOP_N)
                .map(s -> index.toEntry(s.raw(), s.hits(), dict)).toList();
        List<CatalogLine> runnerUps = scored.stream().skip(TOP_N).limit(RUNNER_UP_N)
                .map(s -> index.toEntry(s.raw(), List.of(), dict))
                .map(e -> new CatalogLine(e.apiId(), e.name(), e.summary())).toList();
        return new GuideResult(topic, matched, runnerUps, terms);
    }

    /** get_api_spec — 지정한 API의 전체 필드를 채워 반환한다 */
    public List<ApiSpecEntry> specs(List<String> apiIds) {
        return apiIds.stream()
                .map(index::rawById)
                .flatMap(Optional::stream)
                .map(r -> index.toEntry(r, r.codes(), dict))
                .toList();
    }

    /**
     * 랭킹. 같은 필드 패턴이 여러 모듈에 중복되므로(실측: "외국인 순매수" 104개 매칭) 정본을 앞세운다.
     *  - 주식 질문인데 파생/채권 모듈이 1위로 오는 것을 막는다
     *  - NXT/통합(m222~m225)은 거래소 정본(m001/m003)과 필드가 같으므로 뒤로 민다
     *  - 기간 질문이면 hist_info를, 현재 스냅샷 질문이면 basic_info를 우선
     */
    private int score(ApiSpecIndex.Raw r, int hitCount, String topic) {
        String p = r.path();
        int s = hitCount * 10;

        if (p.startsWith("/stock/")) s += 40;
        else if (p.startsWith("/etc/")) s += 10;
        else s -= 20;                                   // future / bond / ext

        if (p.matches("^/stock/m(001|002|003|004)/.*")) s += 25;   // 거래소·코스닥 정본
        if (p.matches("^/stock/m22[2-5]/.*")) s -= 30;             // NXT/통합 중복
        if (p.endsWith("_port")) s -= 15;                          // 복수종목 변형은 기본형 뒤로

        boolean periodQuestion = topic != null && topic.matches(".*(기간|일별|추이|동향|최근|이력|변화).*");
        if (p.contains("hist")) s += periodQuestion ? 20 : 5;
        if (p.contains("basic")) s += periodQuestion ? 0 : 10;
        if (p.contains("intra") || p.contains("tick") || p.contains("hoga")) s -= 10;

        return s;
    }
}
