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
    void 회전_값만_남기고_위치정보와_파일_끝_데이터를_지운다() {
        byte[] stripped = JpegMetadata.stripKeepingOrientation(JpegSamples.withOrientationAndTrailer(6));
        String text = new String(stripped, StandardCharsets.ISO_8859_1);

        assertThat(JpegMetadata.orientation(stripped)).isEqualTo(6);
        assertThat(text).doesNotContain("GPS").doesNotContain("MOTION").contains("JFIF");
        // APP0(JFIF)가 첫 세그먼트로 남는다
        assertThat(stripped[3] & 0xff).isEqualTo(0xE0);
        assertThat(stripped[stripped.length - 1] & 0xff).isEqualTo(0xD9);
    }

    @Test
    void 실제_JPEG는_지운_뒤에도_같은_크기로_열린다() throws Exception {
        var image = new java.awt.image.BufferedImage(32, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var encoded = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", encoded);

        var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(JpegMetadata.stripKeepingOrientation(encoded.toByteArray())));

        assertThat(decoded.getWidth()).isEqualTo(32);
        assertThat(decoded.getHeight()).isEqualTo(16);
    }

    @Test
    void 회전이_없으면_EXIF를_다시_넣지_않는다() {
        String text = new String(JpegMetadata.stripKeepingOrientation(JpegSamples.withExif()), StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("Exif");
    }

    @Test
    void 잘린_세그먼트는_거절한다() {
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F, 0x7F, 1, 2};
        assertThatThrownBy(() -> JpegMetadata.strip(broken)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void JPEG가_아니면_거절한다() {
        assertThatThrownBy(() -> JpegMetadata.strip(new byte[]{1, 2, 3, 4})).isInstanceOf(IllegalArgumentException.class);
    }
}
