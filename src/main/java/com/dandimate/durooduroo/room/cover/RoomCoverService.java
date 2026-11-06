package com.dandimate.durooduroo.room.cover;
import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.dto.CoverPhotoView;
import com.dandimate.durooduroo.photo.image.*;
import com.dandimate.durooduroo.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.UUID;
@Service
public class RoomCoverService {
    private static final int MAX_BYTES=2_000_000;
    private final RoomCoverRepository covers;
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final ObjectStorage storage;
    public RoomCoverService(RoomCoverRepository covers,MemberRepository members,RoomRepository rooms,ObjectStorage storage) {
        this.covers=covers;this.members=members;this.rooms=rooms;this.storage=storage;
    }
    public CoverPhotoView upload(MultipartFile file) throws IOException {
        String user=CurrentUser.requireId();
        if(file.isEmpty()||file.getSize()>MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE,"대표 사진은 2MB 이하이어야 합니다.");
        byte[] bytes;
        try(var stream=file.getInputStream()) { bytes=stream.readNBytes(MAX_BYTES+1); }
        if(bytes.length>MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE,"대표 사진은 2MB 이하이어야 합니다.");
        String type=JpegMetadata.isJpeg(bytes)?"image/jpeg":PngMetadata.isPng(bytes)?"image/png":null;
        if(type==null||!type.equalsIgnoreCase(file.getContentType())) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"대표 사진은 JPEG 또는 PNG로 올려 주세요.");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException();
            var reader=readers.next();
            try {
                reader.setInput(input);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width*height>16_000_000) throw new IllegalArgumentException();
                if(reader.read(0)==null) throw new IllegalArgumentException();
            } finally { reader.dispose(); }
            bytes=type.equals("image/png")?PngMetadata.strip(bytes):JpegMetadata.stripKeepingOrientation(bytes);
        } catch(IOException|RuntimeException e) {
            throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED,"손상된 이미지이거나 1600만 화소를 초과했습니다.");
        }
        String id=UUID.randomUUID().toString();
        String key="room-covers/"+user+"/"+id+(type.equals("image/png")?".png":".jpg");
        String location=storage.locationFor(key);
        storage.putBytes(key,bytes,type);
        try { covers.saveAndFlush(new RoomCover(id,user,location,type)); }
        catch(RuntimeException e) { try{storage.delete(location);}catch(RuntimeException ignored){} throw e; }
        return CoverPhotoView.from(id);
    }
    public void requireOwned(String id,String user) {
        if(user==null||!find(id).getUserId().equals(user)) throw new BusinessException(ErrorCode.FORBIDDEN,"본인이 올린 대표 사진만 선택할 수 있습니다.");
    }
    public RoomCover readable(String id) {
        String user=CurrentUser.requireId();RoomCover cover=find(id);
        if(cover.getUserId().equals(user)) return cover;
        var roomIds=members.findAllByUserIdAndLeftAtIsNull(user).stream().map(m->m.getRoomId()).toList();
        boolean permitted=rooms.findAllById(roomIds).stream().anyMatch(r->id.equals(r.getCoverPhotoId()));
        if(!permitted) throw new BusinessException(ErrorCode.FORBIDDEN,"대표 사진을 볼 권한이 없습니다.");
        return cover;
    }
    private RoomCover find(String id) {return covers.findById(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"대표 사진이 없습니다."));}
}
