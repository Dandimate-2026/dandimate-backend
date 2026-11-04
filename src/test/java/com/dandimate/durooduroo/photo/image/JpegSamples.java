package com.dandimate.durooduroo.photo.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** 테스트용 JPEG 바이트: APP0(JFIF) + APP1(EXIF, GPS 문자열 포함) + COM + DQT + SOS + 데이터 + EOI. */
public final class JpegSamples {

    private JpegSamples() {
    }

    public static byte[] withExif() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8});
        segment(out, 0xE0, "JFIF\0".getBytes(StandardCharsets.ISO_8859_1));
        segment(out, 0xE1, "Exif\0\0GPSLatitude=35.8;GPSLongitude=129.2".getBytes(StandardCharsets.ISO_8859_1));
        segment(out, 0xFE, "secret comment".getBytes(StandardCharsets.ISO_8859_1));
        segment(out, 0xDB, new byte[]{0, 1, 2, 3});
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xDA, 0, 2, 9, 9, 9, 9, (byte) 0xFF, (byte) 0xD9});
        return out.toByteArray();
    }

    /** 리틀 엔디언 EXIF에 회전 값과 GPS 문자열이 있고, 파일 끝에 다른 데이터가 덧붙은 JPEG. */
    public static byte[] withOrientationAndTrailer(int orientation) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8});
        segment(out, 0xE0, "JFIF\0".getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.writeBytes("Exif\0\0II*\0".getBytes(StandardCharsets.ISO_8859_1));
        exif.writeBytes(new byte[]{8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0, 0, 0, 0, 0});
        exif.writeBytes("GPSLatitude=35.8".getBytes(StandardCharsets.ISO_8859_1));
        segment(out, 0xE1, exif.toByteArray());
        segment(out, 0xDB, new byte[]{0, 1, 2, 3});
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xDA, 0, 2, 9, 9, 9, 9, (byte) 0xFF, (byte) 0xD9});
        out.writeBytes("MOTION_PHOTO_GPS".getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static void segment(ByteArrayOutputStream out, int marker, byte[] payload) {
        int length = payload.length + 2;
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length});
        out.writeBytes(payload);
    }
}
