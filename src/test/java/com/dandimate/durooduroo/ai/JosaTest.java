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

    @Test
    void 인용_뒤_라고를_인용_끝_받침에_맞춘다() {
        assertThat(Josa.fixQuoteParticles("서연은 '뒤로 넘어졌거든'라고 했다.")).isEqualTo("서연은 '뒤로 넘어졌거든'이라고 했다.");
        assertThat(Josa.fixQuoteParticles("지수는 '다 젖었어'이라고 했다.")).isEqualTo("지수는 '다 젖었어'라고 했다.");
        assertThat(Josa.fixQuoteParticles("민호는 ‘첫날 숙소’라고, 서연은 ‘마지막 사진’라고 했다.")).isEqualTo("민호는 ‘첫날 숙소’라고, 서연은 ‘마지막 사진’이라고 했다.");
        assertThat(Josa.fixQuoteParticles("'ok'라고 했다.")).isEqualTo("'ok'라고 했다.");
    }
}
