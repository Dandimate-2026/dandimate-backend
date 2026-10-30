/** 사진·음성 파일 저장소 추상화입니다. put이 돌려준 위치 문자열을 DB에 저장하고, 읽을 때 같은 위치를 넘긴다. */
package com.dandimate.durooduroo.storage;
import java.time.Duration; import java.util.Optional; import org.springframework.core.io.Resource; import org.springframework.web.multipart.MultipartFile;
public interface ObjectStorage {
    String put(String key, MultipartFile file) throws Exception;
    void delete(String key);
    /** 브라우저가 직접 받을 수 있는 서명 URL. 저장소가 지원하지 않으면 비어 있고, 앱이 서명한 URL로 대신 내려준다. */
    Optional<String> presignedGetUrl(String location, Duration ttl);
    /** 앱이 직접 내려줄 파일. 저장소 밖 위치거나 없으면 비어 있다. */
    Optional<Resource> open(String location);
}
