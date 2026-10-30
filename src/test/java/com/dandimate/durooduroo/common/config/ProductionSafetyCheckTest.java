package com.dandimate.durooduroo.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionSafetyCheckTest {

    @Test
    void 위험한_운영_설정을_모두_경고한다() {
        var check = new ProductionSafetyCheck(true, "local", "", "fake", "rule", "update", "jdbc:mysql://db/x?useSSL=false");
        assertThat(check.warnings()).hasSize(7);
    }

    @Test
    void 운영다운_설정이면_경고가_없다() {
        var check = new ProductionSafetyCheck(false, "s3", "secret", "openai", "openai", "validate", "jdbc:mysql://db/x?sslMode=REQUIRED");
        assertThat(check.warnings()).isEmpty();
    }
}
