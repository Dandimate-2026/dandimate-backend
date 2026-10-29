/** 명세서 기준 본인 사진 삭제 API입니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/photos")
public class PhotoDeleteController {
    private final PhotoRepository photos; private final MemberRepository members;
    public PhotoDeleteController(PhotoRepository photos,MemberRepository members){this.photos=photos;this.members=members;}
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable String photoId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var photo=photos.findById(photoId).orElseThrow(()->new BusinessException("PHOTO_NOT_FOUND","사진이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(photo.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 삭제할 수 있습니다.")); if(!member.getId().equals(photo.getMemberId()))throw new BusinessException("FORBIDDEN","본인 사진만 삭제할 수 있습니다."); photos.delete(photo); return ResponseEntity.noContent().build();}
}
