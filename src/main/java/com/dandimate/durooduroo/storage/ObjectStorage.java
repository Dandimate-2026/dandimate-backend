/** 사진·음성 파일 저장소 추상화입니다. */
package com.dandimate.durooduroo.storage;
import org.springframework.web.multipart.MultipartFile;
public interface ObjectStorage { String put(String key, MultipartFile file) throws Exception; void delete(String key); }
