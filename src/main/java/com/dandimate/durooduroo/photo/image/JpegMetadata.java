/**
 * JPEG에서 메타데이터 세그먼트를 지운다: APP1(EXIF·XMP, GPS 위치 포함), APP13(IPTC), COM(주석).
 * 색 프로필(APP2 ICC)과 이미지 데이터는 그대로 둔다. 변환 도구와 관계없이 위치정보가 남지 않게 한다 (명세 2장 완료 조건).
 */
package com.dandimate.durooduroo.photo.image;

import java.io.ByteArrayOutputStream;

public final class JpegMetadata {

    private JpegMetadata() {
    }

    public static byte[] strip(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xff) != 0xFF || (jpeg[1] & 0xff) != 0xD8) throw new IllegalArgumentException("not a JPEG");
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xff) != 0xFF) throw new IllegalArgumentException("broken JPEG marker");
            int marker = jpeg[pos + 1] & 0xff;
            if (marker == 0xDA) {
                // 이미지 데이터(SOS) 이후는 그대로 복사한다.
                out.write(jpeg, pos, jpeg.length - pos);
                return out.toByteArray();
            }
            int length = ((jpeg[pos + 2] & 0xff) << 8) | (jpeg[pos + 3] & 0xff);
            boolean metadata = marker == 0xE1 || marker == 0xED || marker == 0xFE;
            if (!metadata) out.write(jpeg, pos, 2 + length);
            pos += 2 + length;
        }
        return out.toByteArray();
    }
}
