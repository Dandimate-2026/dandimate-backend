/**
 * JPEG에서 메타데이터를 지운다. 이미지 표시에 필요한 APP0(JFIF), APP2 ICC 색 프로필, APP14(Adobe)와 이미지 데이터만 남긴다.
 * EXIF·XMP(GPS 위치 포함), IPTC, 주석과 파일 끝에 덧붙은 데이터(다중 사진, 모션 포토)는 지운다 (명세 2장 완료 조건).
 */
package com.dandimate.durooduroo.photo.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class JpegMetadata {

    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final int ORIENTATION_TAG = 0x0112;

    private JpegMetadata() {
    }

    public static byte[] strip(byte[] jpeg) {
        if (!isJpeg(jpeg)) throw new IllegalArgumentException("not a JPEG");
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xff) != 0xFF) throw new IllegalArgumentException("broken JPEG marker");
            int marker = jpeg[pos + 1] & 0xff;
            if (marker == 0xFF) {
                // 마커 앞의 채움 바이트
                pos++;
                continue;
            }
            if (marker == 0xDA) {
                // 이미지 데이터(SOS)는 끝 표시(EOI)까지만 복사한다.
                int end = endOfImage(jpeg, pos);
                out.write(jpeg, pos, end - pos);
                return out.toByteArray();
            }
            int length = ((jpeg[pos + 2] & 0xff) << 8) | (jpeg[pos + 3] & 0xff);
            if (length < 2 || pos + 2 + length > jpeg.length) throw new IllegalArgumentException("broken JPEG segment");
            if (keeps(jpeg, pos, marker)) out.write(jpeg, pos, 2 + length);
            pos += 2 + length;
        }
        return out.toByteArray();
    }

    /** 휴대폰 사진은 회전 정보가 EXIF에만 있어서, 브라우저에 그대로 보여 줄 사진은 이 값만 다시 넣는다. */
    public static byte[] stripKeepingOrientation(byte[] jpeg) {
        int orientation = orientation(jpeg);
        byte[] stripped = strip(jpeg);
        if (orientation < 2 || orientation > 8) return stripped;
        byte[] app1 = orientationSegment(orientation);
        // APP0(JFIF)가 있으면 맨 앞 세그먼트로 두어야 한다.
        int insertAt = 2;
        if (stripped.length > 5 && (stripped[2] & 0xff) == 0xFF && (stripped[3] & 0xff) == 0xE0) {
            insertAt = 4 + (((stripped[4] & 0xff) << 8) | (stripped[5] & 0xff));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(stripped.length + app1.length);
        out.write(stripped, 0, insertAt);
        out.writeBytes(app1);
        out.write(stripped, insertAt, stripped.length - insertAt);
        return out.toByteArray();
    }

    public static boolean isJpeg(byte[] data) {
        return data.length >= 4 && (data[0] & 0xff) == 0xFF && (data[1] & 0xff) == 0xD8;
    }

    private static boolean keeps(byte[] jpeg, int pos, int marker) {
        if (marker == 0xFE) return false;
        if (marker < 0xE0 || marker > 0xEF) return true;
        if (marker == 0xE0 || marker == 0xEE) return true;
        return marker == 0xE2 && startsWith(jpeg, pos + 4, ICC_HEADER);
    }

    private static int endOfImage(byte[] jpeg, int from) {
        for (int i = from; i + 1 < jpeg.length; i++) {
            if ((jpeg[i] & 0xff) == 0xFF && (jpeg[i + 1] & 0xff) == 0xD9) return i + 2;
        }
        return jpeg.length;
    }

    /** EXIF 회전 값. 없거나 읽을 수 없으면 1(회전 없음). */
    static int orientation(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xFF && (jpeg[pos + 1] & 0xff) != 0xDA) {
            int length = ((jpeg[pos + 2] & 0xff) << 8) | (jpeg[pos + 3] & 0xff);
            if ((jpeg[pos + 1] & 0xff) == 0xE1 && startsWith(jpeg, pos + 4, EXIF_HEADER)) {
                return exifOrientation(jpeg, pos + 4 + EXIF_HEADER.length, pos + 2 + length);
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int exifOrientation(byte[] jpeg, int tiff, int end) {
        if (end > jpeg.length || tiff + 8 > end) return 1;
        ByteOrder order = jpeg[tiff] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(jpeg).order(order);
        long ifd = tiff + (buffer.getInt(tiff + 4) & 0xffffffffL);
        if (ifd < tiff || ifd + 2 > end) return 1;
        int count = buffer.getShort((int) ifd) & 0xffff;
        for (int i = 0; i < count; i++) {
            long entry = ifd + 2 + i * 12L;
            if (entry + 12 > end) return 1;
            if ((buffer.getShort((int) entry) & 0xffff) == ORIENTATION_TAG) return buffer.getShort((int) entry + 8) & 0xffff;
        }
        return 1;
    }

    private static byte[] orientationSegment(int orientation) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + EXIF_HEADER.length + 26).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (2 + EXIF_HEADER.length + 26)).put(EXIF_HEADER);
        // TIFF 머리글(MM, 42, 첫 IFD 위치 8)과 항목 하나짜리 IFD
        buffer.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        buffer.putShort((short) 1).putShort((short) ORIENTATION_TAG).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        buffer.putInt(0);
        return buffer.array();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset + prefix.length > data.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }
}
