/**
 * 사진·음성 파일 저장소 추상화입니다. key는 서버가 만들고(roomId/... 형식), locationFor(key)를 DB에 저장한다.
 * 클라이언트가 key나 위치를 지정할 수 없다.
 */
package com.dandimate.durooduroo.storage;
import java.time.Duration; import java.util.Optional; import org.springframework.core.io.Resource;
public interface ObjectStorage {
    /** key를 DB에 저장할 위치 문자열로 바꾼다. */
    String locationFor(String key);
    void putBytes(String key, byte[] bytes, String contentType);
    void delete(String location);
    /** key가 prefix로 시작하는 객체를 모두 지운다 (방 삭제 시 roomId/). */
    void deletePrefix(String keyPrefix);
    /** 클라이언트가 직접 올릴 수 있는 presigned PUT URL. 지원하지 않으면 비어 있고 앱의 서명된 업로드 API를 쓴다. */
    Optional<String> presignedPutUrl(String key, String contentType, Duration ttl);
    /** 업로드 완료 검증용 메타데이터(HEAD). 객체가 없으면 비어 있다. */
    Optional<StoredObject> head(String location);
    /** 브라우저가 직접 받을 수 있는 서명 URL. 저장소가 지원하지 않으면 비어 있고, 앱이 서명한 URL로 대신 내려준다. */
    Optional<String> presignedGetUrl(String location, Duration ttl);
    /** 서버가 직접 처리할 때(HEIC 변환 등) 객체 바이트를 읽는다. 없으면 비어 있다. */
    Optional<byte[]> read(String location);
    /** 앱이 직접 내려줄 파일. 저장소 밖 위치거나 없으면 비어 있다. */
    Optional<Resource> open(String location);

    record StoredObject(long size, String contentType) {}
}
