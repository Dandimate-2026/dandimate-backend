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

    @Test
    void 같은_서버의_DB는_TLS가_없어도_경고하지_않는다() {
        var local = new ProductionSafetyCheck(false, "s3", "secret", "openai", "openai", "validate", "jdbc:mysql://mysql:3306/durooduroo?useSSL=false");
        assertThat(local.warnings()).isEmpty();
        var remote = new ProductionSafetyCheck(false, "s3", "secret", "openai", "openai", "validate", "jdbc:mysql://my-db.rds.amazonaws.com:3306/x?useSSL=false");
        assertThat(remote.warnings()).hasSize(1);
    }
}
