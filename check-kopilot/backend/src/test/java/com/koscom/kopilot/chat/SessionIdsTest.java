package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionIdsTest {

    @Test
    void acceptsUuidAndDemoPrefixes() {
        assertThat(SessionIds.requireValid("11111111-1111-4111-8111-111111111111"))
                .isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(SessionIds.requireValid("warmup-abc123")).isEqualTo("warmup-abc123");
    }

    @Test
    void rejectsInjectionShapedIds() {
        assertThatThrownBy(() -> SessionIds.requireValid("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionIds.requireValid("a".repeat(200)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
