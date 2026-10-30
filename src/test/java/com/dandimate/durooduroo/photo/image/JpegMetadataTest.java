package com.dandimate.durooduroo.photo.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JpegMetadataTest {

    @Test
    void EXIF_위치정보와_주석을_지우고_이미지_데이터는_남긴다() {
        byte[] stripped = JpegMetadata.strip(JpegSamples.withExif());
        String text = new String(stripped, StandardCharsets.ISO_8859_1);

        assertThat(text).doesNotContain("Exif").doesNotContain("GPS").doesNotContain("secret comment").contains("JFIF");
        assertThat(stripped[0] & 0xff).isEqualTo(0xFF);
        assertThat(stripped[1] & 0xff).isEqualTo(0xD8);
        assertThat(stripped[stripped.length - 2] & 0xff).isEqualTo(0xFF);
        assertThat(stripped[stripped.length - 1] & 0xff).isEqualTo(0xD9);
    }

    @Test
    void JPEG가_아니면_거절한다() {
        assertThatThrownBy(() -> JpegMetadata.strip(new byte[]{1, 2, 3, 4})).isInstanceOf(IllegalArgumentException.class);
    }
}
