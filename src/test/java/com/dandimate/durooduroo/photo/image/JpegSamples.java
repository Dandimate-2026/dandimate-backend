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

    private static void segment(ByteArrayOutputStream out, int marker, byte[] payload) {
        int length = payload.length + 2;
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length});
        out.writeBytes(payload);
    }
}
