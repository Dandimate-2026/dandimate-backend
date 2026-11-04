package com.dandimate.durooduroo.photo.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PngMetadataTest {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test
    void 메타데이터_청크만_지운다() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        chunk(out, "IHDR", "header");
        chunk(out, "eXIf", "GPSLatitude=35.8");
        chunk(out, "tEXt", "secret comment");
        chunk(out, "IDAT", "pixels");
        chunk(out, "IEND", "");
        out.writeBytes("trailer".getBytes(StandardCharsets.ISO_8859_1));

        String text = new String(PngMetadata.strip(out.toByteArray()), StandardCharsets.ISO_8859_1);

        assertThat(text).contains("IHDR", "IDAT", "pixels", "IEND").doesNotContain("GPS", "secret", "trailer");
    }

    @Test
    void 끝이_없는_PNG는_거절한다() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        chunk(out, "IHDR", "header");
        assertThatThrownBy(() -> PngMetadata.strip(out.toByteArray())).isInstanceOf(IllegalArgumentException.class);
    }

    private static void chunk(ByteArrayOutputStream out, String type, String data) {
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length()).array());
        out.writeBytes(type.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(data.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(new byte[4]);
    }
}
