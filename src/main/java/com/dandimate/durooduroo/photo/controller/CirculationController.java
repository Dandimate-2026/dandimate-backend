/** MVP 돌려보기 목록 API입니다. 참여자가 제출한 응답 상태와 사진별 첫 한마디를 반환합니다. */
package com.dandimate.durooduroo.photo.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryStatus;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/circulation")
public class CirculationController {
    private final MemberRepository members; private final PhotoRepository photos; private final MemoryEntryRepository entries;
    public CirculationController(MemberRepository members,PhotoRepository photos,MemoryEntryRepository entries){this.members=members;this.photos=photos;this.entries=entries;}
    @GetMapping
    public ApiResponse<?> get(@PathVariable String roomId,@RequestHeader(required=false) String authorization){
        String token=MemberTokenResolver.resolve(authorization); var me=members.findByRoomIdAndMemberToken(roomId,token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 조회할 수 있습니다."));
        List<Map<String,Object>> cards=new ArrayList<>();
        for(var photo:photos.findAllByRoomId(roomId)){
            var my=entries.findByPhotoIdAndMemberId(photo.getId(),me.getId());
            var first=entries.findFirstByPhotoIdAndStatusOrderByCreatedAtAsc(photo.getId(),EntryStatus.ANSWERED);
            Map<String,Object> card=new LinkedHashMap<>(); card.put("photo",Map.of("id",photo.getId(),"imageUrl",photo.getFileUrl()));
            card.put("firstEntry", first.map(e->Map.of("id",e.getId(),"transcript",e.getContent())).orElse(null));
            card.put("myResponse",Map.of("status",my.map(e->e.getStatus().name()).orElse("NONE"))); cards.add(card);
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("cards", cards); result.put("nextCursor", null);
        return ApiResponse.success(result);
    }
}
