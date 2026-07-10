package com.smartcs.agent.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LogFieldsTest {

    @Test
    void chatBuildsStableKeyValueFields() {
        assertThat(LogFields.chat(" trace_1 ", "s_1001", "u1001"))
                .isEqualTo("traceId=trace_1 sessionId=s_1001 userId=u1001");
    }

    @Test
    void valueNormalizesBlankAndNull() {
        assertThat(LogFields.value(null)).isEqualTo("-");
        assertThat(LogFields.value("   ")).isEqualTo("-");
    }

    @Test
    void keyValuesRejectsUnalignedPairs() {
        assertThatThrownBy(() -> LogFields.keyValues(LogFields.TRACE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
