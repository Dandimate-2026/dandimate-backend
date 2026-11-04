/** PNG에서 EXIF와 글자 메타데이터 청크를 지운다. 이미지 데이터와 색 정보 청크는 그대로 둔다. */
package com.dandimate.durooduroo.photo.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

public final class PngMetadata {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> METADATA_CHUNKS = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private PngMetadata() {
    }

    public static boolean isPng(byte[] data) {
        return data.length >= SIGNATURE.length && Arrays.equals(data, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length);
    }

    public static byte[] strip(byte[] png) {
        if (!isPng(png)) throw new IllegalArgumentException("not a PNG");
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length);
        out.write(png, 0, SIGNATURE.length);
        int pos = SIGNATURE.length;
        while (pos + 12 <= png.length) {
            long length = ByteBuffer.wrap(png, pos, 4).getInt() & 0xffffffffL;
            if (pos + 12 + length > png.length) throw new IllegalArgumentException("broken PNG chunk");
            String type = new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int size = (int) (12 + length);
            if (!METADATA_CHUNKS.contains(type)) out.write(png, pos, size);
            pos += size;
            if ("IEND".equals(type)) return out.toByteArray();
        }
        throw new IllegalArgumentException("PNG without IEND");
    }
}
