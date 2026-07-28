package com.koscom.kopilot.shortcut;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class JdbcShortcutStoreTest {

    // 개발 DB에는 데모로 쌓인 행이 있을 수 있다 — 테스트 전용 기기 id로 격리한다
    private static final String DEVICE = "test-device-A";
    private static final String OTHER_DEVICE = "test-device-B";

    @Autowired JdbcShortcutStore store;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM shortcut WHERE device_id IN (?, ?)", DEVICE, OTHER_DEVICE);
    }

    private Shortcut sample(String device, String combo) {
        return new Shortcut(UUID.randomUUID().toString(), device, combo, "return_gap",
                "삼성전자(005930),SK하이닉스(000660)", "3M",
                "삼성전자와 SK하이닉스의 최근 3개월 수익률 갭을 비교해줘");
    }

    @Test
    void insertedShortcut_isFoundByItsDevice() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        List<Shortcut> found = store.findByDevice(DEVICE);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).keyCombo()).isEqualTo("ctrl+shift+1");
        assertThat(found.get(0).prompt()).contains("수익률 갭");
    }

    /** 기기별로 갈리지 않으면 남의 단축키가 내 화면에 뜬다 */
    @Test
    void otherDeviceShortcuts_areNotVisible() {
        store.insert(sample(OTHER_DEVICE, "ctrl+shift+1"));

        assertThat(store.findByDevice(DEVICE)).isEmpty();
    }

    /** 같은 기기에서 같은 키를 두 번 쓰면 어느 쪽이 발사될지 정해지지 않는다 — DB가 막는다 */
    @Test
    void sameDeviceSameCombo_isRejected() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        assertThatThrownBy(() -> store.insert(sample(DEVICE, "ctrl+shift+1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void differentDevicesMayShareCombo() {
        store.insert(sample(DEVICE, "ctrl+shift+1"));

        assertThatCode(() -> store.insert(sample(OTHER_DEVICE, "ctrl+shift+1")))
                .doesNotThrowAnyException();
    }

    @Test
    void update_changesPromptOfOwnShortcut() {
        Shortcut saved = sample(DEVICE, "ctrl+shift+2");
        store.insert(saved);

        int changed = store.update(new Shortcut(saved.id(), DEVICE, "ctrl+shift+3", "volatility",
                "삼성전자(005930)", "1M", "삼성전자의 최근 1개월 변동성을 계산해줘"));

        assertThat(changed).isEqualTo(1);
        assertThat(store.findByDevice(DEVICE).get(0).toolName()).isEqualTo("volatility");
        assertThat(store.findByDevice(DEVICE).get(0).keyCombo()).isEqualTo("ctrl+shift+3");
    }

    @Test
    void update_ofAnotherDeviceShortcut_changesNothing() {
        Shortcut saved = sample(OTHER_DEVICE, "ctrl+shift+4");
        store.insert(saved);

        int changed = store.update(new Shortcut(saved.id(), DEVICE, "ctrl+shift+4", "volatility",
                "삼성전자(005930)", "1M", "삼성전자의 최근 1개월 변동성을 계산해줘"));

        assertThat(changed).isZero();
    }

    @Test
    void delete_removesOnlyOwnShortcut() {
        Shortcut mine = sample(DEVICE, "ctrl+shift+5");
        store.insert(mine);

        assertThat(store.delete(mine.id(), OTHER_DEVICE)).isZero();
        assertThat(store.delete(mine.id(), DEVICE)).isEqualTo(1);
        assertThat(store.findByDevice(DEVICE)).isEmpty();
    }

    @Test
    void periodMayBeNull() {
        store.insert(new Shortcut(UUID.randomUUID().toString(), DEVICE, "ctrl+shift+6",
                "nav_disparity", "KODEX 200(069500)", null, "KODEX 200의 괴리율을 알려줘"));

        assertThat(store.findByDevice(DEVICE).get(0).period()).isNull();
    }
}
