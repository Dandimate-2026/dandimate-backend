/** HEIC/HEIF 원본을 JPEG로 바꾼다. 실패하면 예외를 던지고, 작업 재시도 후 사진은 FAILED가 된다. */
package com.dandimate.durooduroo.photo.image;

public interface HeicConverter {
    byte[] toJpeg(byte[] heic);
}
