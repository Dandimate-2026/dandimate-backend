package com.dandimate.durooduroo.scroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

/**
 * MySQL에서 @Lob String은 TINYTEXT(255바이트, 한글 약 85자)가 된다. H2 테스트로는 드러나지 않아 실제 MySQL에서 발견했다.
 * 긴 본문 컬럼은 @Lob 없이 length로 크기를 지정해 MEDIUMTEXT가 되게 한다.
 */
class LongTextColumnTest {

    @Test
    void 두루마리_문단_버전_본문은_MEDIUMTEXT_크기다() throws Exception {
        for (Class<?> entity : new Class<?>[]{Paragraph.class, Scroll.class, ParagraphVersion.class}) {
            var body = entity.getDeclaredField("body");
            assertThat(body.isAnnotationPresent(Lob.class)).as(entity.getSimpleName() + ".body must not be @Lob").isFalse();
            assertThat(body.getAnnotation(Column.class).length()).as(entity.getSimpleName() + ".body length").isGreaterThan(65_535);
        }
    }
}
