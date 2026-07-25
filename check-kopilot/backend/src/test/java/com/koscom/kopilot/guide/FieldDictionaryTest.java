package com.koscom.kopilot.guide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FieldDictionaryTest {

    private final FieldDictionary dict = FieldDictionary.loadFromClasspath();

    @Test
    void loadsGlobalCodeDictionary() {
        assertThat(dict.size()).isGreaterThan(1500);
        assertThat(dict.label("F15001")).isEqualTo("현재가");
        // detail이 병합돼 있어야 실행 가능한 레시피가 나온다
        assertThat(dict.label("F06508_11")).contains("외국인");
    }

    @Test
    void searchesByKeyword() {
        Set<String> codes = dict.search(List.of("외국인", "순매수"));
        assertThat(codes).contains("F06508_11", "F06511_11");
        // 하나만 걸린 코드는 제외 — 두 키워드를 모두 담은 것이 우선
        assertThat(dict.label(codes.iterator().next())).isNotBlank();
    }

    @Test
    void expandsSynonymsSoUserVocabularyStillHits() {
        // "수급"은 F코드 설명에 없는 단어 — 동의어 사전이 없으면 0건이 된다
        assertThat(dict.search(List.of("수급"))).isNotEmpty();
        assertThat(dict.search(List.of("외인"))).isNotEmpty();
    }

    @Test
    void unknownKeywordReturnsEmpty() {
        assertThat(dict.search(List.of("존재하지않는지표명xyz"))).isEmpty();
    }
}
