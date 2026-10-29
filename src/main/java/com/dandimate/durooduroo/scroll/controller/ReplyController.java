/** 명세서 기준 답가·문단 반응 API입니다. */
package com.dandimate.durooduroo.scroll.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.scroll.domain.Reply;
import com.dandimate.durooduroo.scroll.repository.*;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ReplyController {
    private final ParagraphRepository paragraphs; private final ReplyRepository replies; private final MemberRepository members; private final com.dandimate.durooduroo.room.service.RoomStateService roomStates;
    public ReplyController(ParagraphRepository paragraphs,ReplyRepository replies,MemberRepository members,com.dandimate.durooduroo.room.service.RoomStateService roomStates){this.paragraphs=paragraphs;this.replies=replies;this.members=members;this.roomStates=roomStates;}
    @PostMapping("/api/v1/paragraphs/{paragraphId}/replies")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken,@RequestBody Map<String,String> body){var p=paragraphs.findById(paragraphId).orElseThrow(()->new BusinessException("PARAGRAPH_NOT_FOUND","문단이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); var member=members.findByRoomIdAndMemberToken(roomIdFor(p),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","참여자만 답가를 작성할 수 있습니다.")); roomStates.require(p.getRoomId(),com.dandimate.durooduroo.room.domain.RoomAction.REPLY); String text=body.get("transcript"); if(text==null||text.isBlank())throw new BusinessException("EMPTY_CONTENT","답가 내용은 필수입니다."); var reply=replies.save(Reply.create(paragraphId,member.getId(),text)); return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("replyId",reply.getId(),"status","QUEUED")));}
    @DeleteMapping("/api/v1/replies/{replyId}") public ResponseEntity<Void> delete(@PathVariable String replyId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var reply=replies.findById(replyId).orElseThrow(()->new BusinessException("REPLY_NOT_FOUND","답가가 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); if(!members.authenticate(token).getId().equals(reply.getMemberId()))throw new BusinessException("FORBIDDEN","본인 답가만 삭제할 수 있습니다."); replies.delete(reply);return ResponseEntity.accepted().build();}
    @PutMapping("/api/v1/paragraphs/{paragraphId}/fidelity-rating") public ApiResponse<?> rating(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken,@RequestBody Map<String,Integer> body){requireMember(paragraphId,authorization,memberToken);int score=body.getOrDefault("score",0);if(score<1||score>5)throw new BusinessException("INVALID_SCORE","평점은 1~5점이어야 합니다.");return ApiResponse.success(Map.of("score",score));}
    @PutMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda") public ApiResponse<?> react(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){var p=requireMember(paragraphId,authorization,memberToken);p.ahMatda();paragraphs.save(p);return ApiResponse.success(Map.of("ahMatdaCount",p.getAhMatdaCount()));}
    @DeleteMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda") public ResponseEntity<Void> unreact(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestParam(required=false) String memberToken){requireMember(paragraphId,authorization,memberToken);return ResponseEntity.noContent().build();}
    private com.dandimate.durooduroo.scroll.domain.Paragraph requireMember(String paragraphId,String authorization,String memberToken){var p=paragraphs.findById(paragraphId).orElseThrow(()->new BusinessException("PARAGRAPH_NOT_FOUND","문단이 없습니다.")); String token=MemberTokenResolver.resolve(authorization,memberToken); members.findByRoomIdAndMemberToken(p.getRoomId(),token).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다.")); return p;}
    private String roomIdFor(com.dandimate.durooduroo.scroll.domain.Paragraph p){return p.getRoomId();}
}
