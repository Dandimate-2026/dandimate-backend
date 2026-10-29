/** 명세서 기준 본인 한마디 수정·삭제 API입니다. */
package com.dandimate.durooduroo.memory.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/entries")
public class EntryLifecycleController {
    private final MemoryEntryRepository entries; private final MemberRepository members;
    public EntryLifecycleController(MemoryEntryRepository entries,MemberRepository members){this.entries=entries;this.members=members;}
    @PatchMapping("/{entryId}")
    public ApiResponse<?> update(@PathVariable String entryId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken,@RequestBody java.util.Map<String,String> body){
        var entry=entries.findById(entryId).orElseThrow(()->new BusinessException("ENTRY_NOT_FOUND","한마디가 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(entry.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 수정할 수 있습니다.")); if(!member.getId().equals(entry.getMemberId()))throw new BusinessException("FORBIDDEN","본인 한마디만 수정할 수 있습니다."); String text=body.getOrDefault("transcript",body.get("content")); if(text==null||text.isBlank())throw new BusinessException("EMPTY_CONTENT","내용은 필수입니다."); entry.updateContent(text); return ApiResponse.success(entries.save(entry));
    }
    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable String entryId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){
        var entry=entries.findById(entryId).orElseThrow(()->new BusinessException("ENTRY_NOT_FOUND","한마디가 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(entry.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 삭제할 수 있습니다.")); if(!member.getId().equals(entry.getMemberId()))throw new BusinessException("FORBIDDEN","본인 한마디만 삭제할 수 있습니다."); entries.delete(entry); return ResponseEntity.noContent().build();
    }
}
