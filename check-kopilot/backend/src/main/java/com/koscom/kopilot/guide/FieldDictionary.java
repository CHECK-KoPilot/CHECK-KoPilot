package com.koscom.kopilot.guide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * F코드 전역 사전. CHECK API의 F코드는 API와 무관하게 의미가 동일하므로(2026-07-22 실측: 24,648 슬롯 → 1,841 코드)
 * API별로 필드 설명을 중복 저장하지 않고 여기 하나만 둔다.
 * label에는 명세의 desc와 detail이 병합돼 있다 — detail 없이는 "순매수거래량11"이 외국인인지 알 수 없다.
 */
public class FieldDictionary {

    private final Map<String, String> labels;
    private final Map<String, List<String>> synonyms;

    public FieldDictionary(Map<String, String> labels, Map<String, List<String>> synonyms) {
        this.labels = labels;
        this.synonyms = synonyms;
    }

    @SuppressWarnings("unchecked")
    public static FieldDictionary loadFromClasspath() {
        try (var codes = new ClassPathResource("check-api/fcodes.json").getInputStream();
             var syn = new ClassPathResource("check-api/synonyms.yaml").getInputStream()) {
            Map<String, String> labels = new ObjectMapper()
                    .readValue(codes, new TypeReference<Map<String, String>>() {});
            Map<String, Object> root = new Yaml().load(syn);
            Map<String, List<String>> synonyms =
                    (Map<String, List<String>>) root.getOrDefault("synonyms", Map.of());
            return new FieldDictionary(labels, synonyms);
        } catch (Exception e) {
            throw new IllegalStateException("F코드 사전 로드 실패", e);
        }
    }

    public int size() { return labels.size(); }

    public String label(String code) { return labels.getOrDefault(code, code); }

    /** 사용자 표현을 명세 용어로 확장한다. LLM이 넘긴 keywords의 어휘 불일치를 보정하는 안전망. */
    public List<String> expand(List<String> keywords) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String k : keywords) {
            String t = k == null ? "" : k.trim();
            if (t.isEmpty()) continue;
            out.add(t);
            out.addAll(synonyms.getOrDefault(t, List.of()));
        }
        return new ArrayList<>(out);
    }

    /**
     * 키워드를 모두 포함하는 코드를 우선하고, 없으면 하나라도 포함하는 코드를 반환한다.
     * (AND 우선 → OR 폴백. "외국인 순매수"가 "외국인" 전부를 끌고 오는 것을 막는다)
     */
    public Set<String> search(List<String> keywords) {
        List<String> terms = expand(keywords);
        if (terms.isEmpty()) return Set.of();

        Set<String> all = new LinkedHashSet<>();
        Set<String> any = new LinkedHashSet<>();
        for (var e : labels.entrySet()) {
            String label = e.getValue();
            int hits = 0;
            for (String t : terms) if (label.contains(t)) hits++;
            if (hits == 0) continue;
            any.add(e.getKey());
            if (hits >= Math.min(terms.size(), 2)) all.add(e.getKey());
        }
        return all.isEmpty() ? any : all;
    }
}
