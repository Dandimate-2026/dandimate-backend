/** 명세서 기준 되묻기 목록·답변·상태 변경 API입니다. */
package com.dandimate.durooduroo.askback.controller;

import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class AskbackController {
    private final AskbackRepository askbacks; private final MemberRepository members;
    public AskbackController(AskbackRepository askbacks,MemberRepository members){this.askbacks=askbacks;this.members=members;}

    @GetMapping("/api/v1/rooms/{roomId}/askbacks/me")
    public ApiResponse<?> list(@PathVariable String roomId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken,@RequestParam(required=false) String status){String token=MemberTokenResolver.resolve(authorization,memberToken);var me=members.findByRoomIdAndMemberToken(roomId,token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 조회할 수 있습니다."));var list=askbacks.findAllByRoomIdAndTargetMemberId(roomId,me.getId()).stream().filter(a->status==null||status.equals(a.getStatus())).map(a->Map.of("id",a.getId(),"question",a.getQuestion(),"status",a.getStatus(),"createdAt",a.getCreatedAt())).toList();return ApiResponse.success(Map.of("askbacks",list));}

    @GetMapping("/api/v1/askbacks/{askbackId}")
    public ApiResponse<?> detail(@PathVariable String askbackId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var a=askbacks.findById(askbackId).orElseThrow(()->new BusinessException("ASKBACK_NOT_FOUND","되묻기가 없습니다.")); authorize(a,authorization,memberToken);return ApiResponse.success(Map.of("askback",Map.of("id",a.getId(),"question",a.getQuestion(),"status",a.getStatus(),"createdAt",a.getCreatedAt())));}

    @PostMapping("/api/v1/askbacks/{askbackId}/answer")
    public ApiResponse<?> answer(@PathVariable String askbackId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken,@RequestBody Map<String,String> body){var a=askbacks.findById(askbackId).orElseThrow(()->new BusinessException("ASKBACK_NOT_FOUND","되묻기가 없습니다.")); authorize(a,authorization,memberToken); String text=body.get("transcript"); if(text==null||text.isBlank())throw new BusinessException("EMPTY_CONTENT","답변은 필수입니다."); a.answer(); askbacks.save(a); return ApiResponse.success(Map.of("askback",Map.of("id",a.getId(),"status",a.getStatus()),"entry",Map.of("transcript",text)));}
    @PostMapping("/api/v1/askbacks/{askbackId}/forgot") public ApiResponse<?> forgot(@PathVariable String askbackId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var a=askbacks.findById(askbackId).orElseThrow(()->new BusinessException("ASKBACK_NOT_FOUND","되묻기가 없습니다."));authorize(a,authorization,memberToken);a.forgot();askbacks.save(a);return ApiResponse.success(Map.of("status",a.getStatus()));}
    @PostMapping("/api/v1/askbacks/{askbackId}/postpone") public ApiResponse<?> postpone(@PathVariable String askbackId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var a=askbacks.findById(askbackId).orElseThrow(()->new BusinessException("ASKBACK_NOT_FOUND","되묻기가 없습니다."));authorize(a,authorization,memberToken);a.postpone();askbacks.save(a);return ApiResponse.success(Map.of("status",a.getStatus()));}
    private void authorize(Askback a,String authorization,String memberToken){String token=MemberTokenResolver.resolve(authorization,memberToken);var me=members.findByRoomIdAndMemberToken(a.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 사용할 수 있습니다."));if(!me.getId().equals(a.getTargetMemberId()))throw new BusinessException("FORBIDDEN","대상 참여자만 조회할 수 있습니다.");}
}
