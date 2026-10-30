package com.dandimate.durooduroo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dandimate.durooduroo.ai.common.Josa;
import org.junit.jupiter.api.Test;

class JosaTest {

    @Test
    void 받침에_맞춰_은_는을_붙인다() {
        assertThat(Josa.topic("서연")).isEqualTo("서연은");
        assertThat(Josa.topic("지수")).isEqualTo("지수는");
        assertThat(Josa.topic("민호")).isEqualTo("민호는");
        assertThat(Josa.topic("Alex")).isEqualTo("Alex은(는)");
    }
}
