/** 사진 업로드, 해시 계산, 파일 저장 규칙을 처리합니다. */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.PhotoResponse;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.io.*; import java.nio.file.*; import java.security.*; import java.util.UUID;
import org.springframework.stereotype.Service; import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoService {
    private final PhotoRepository photos; private final MemberRepository members; private final RoomRepository rooms;
    public PhotoService(PhotoRepository photos, MemberRepository members, RoomRepository rooms){this.photos=photos;this.members=members;this.rooms=rooms;}
    public PhotoResponse upload(String roomId, String token, MultipartFile file) throws Exception {
        var member=members.findByRoomIdAndMemberToken(roomId, token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 업로드할 수 있습니다."));
        if(!rooms.existsById(roomId)) throw new BusinessException("ROOM_NOT_FOUND","존재하지 않는 방입니다.");
        if(file.isEmpty() || file.getContentType()==null || !file.getContentType().startsWith("image/")) throw new BusinessException("INVALID_FILE","이미지 파일만 업로드할 수 있습니다.");
        if(photos.existsByRoomIdAndMemberId(roomId,member.getId())) throw new BusinessException("PHOTO_LIMIT","참여자당 사진 1장만 업로드할 수 있습니다.");
        String hash=sha256(file.getBytes()); var duplicate=photos.findByRoomIdAndFileHash(roomId,hash);
        if(duplicate.isPresent()) return PhotoResponse.from(duplicate.get(),true);
        Path dir=Paths.get("uploads",roomId); Files.createDirectories(dir); Path path=dir.resolve(UUID.randomUUID()+extension(file.getOriginalFilename())); Files.write(path,file.getBytes());
        return PhotoResponse.from(photos.save(Photo.create(roomId,member.getId(),path.toString(),hash)),false);
    }
    private String extension(String name) { return name!=null && name.matches(".*\\.[A-Za-z0-9]{1,5}") ? name.substring(name.lastIndexOf('.')).toLowerCase() : ""; }
    private String sha256(byte[] bytes) throws Exception { var md=MessageDigest.getInstance("SHA-256"); var b=md.digest(bytes); var s=new StringBuilder(); for(byte x:b)s.append(String.format("%02x",x)); return s.toString(); }
}
