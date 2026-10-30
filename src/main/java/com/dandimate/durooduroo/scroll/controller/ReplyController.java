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
    private final ParagraphRepository paragraphs; private final com.dandimate.durooduroo.scroll.service.ReplyService replyService; private final MemberRepository members; private final com.dandimate.durooduroo.room.service.RoomStateService roomStates; private final com.dandimate.durooduroo.scroll.repository.ParagraphReactionRepository reactions; private final com.dandimate.durooduroo.scroll.repository.ParagraphRatingRepository ratings;
    public ReplyController(ParagraphRepository paragraphs,com.dandimate.durooduroo.scroll.service.ReplyService replyService,MemberRepository members,com.dandimate.durooduroo.room.service.RoomStateService roomStates,com.dandimate.durooduroo.scroll.repository.ParagraphReactionRepository reactions,com.dandimate.durooduroo.scroll.repository.ParagraphRatingRepository ratings){this.paragraphs=paragraphs;this.replyService=replyService;this.members=members;this.roomStates=roomStates;this.reactions=reactions;this.ratings=ratings;}
    /** 답가 확정: 202와 REPLY_REWEAVE 작업. 반영 결과(APPLIED/REACTION/REJECTED)는 작업이 끝난 뒤 정해진다. */
    @PostMapping("/api/v1/paragraphs/{paragraphId}/replies")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestBody Map<String,String> body){var accepted=replyService.create(paragraphId,authorization,body.getOrDefault("inputMode","TEXT"),body.get("transcriptionId"),body.get("transcript"));return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("reply",Map.of("id",accepted.reply().getId(),"status",accepted.reply().getStatus()),"job",com.dandimate.durooduroo.job.dto.JobResponse.from(accepted.job()))));}
    /** 본인 답가 삭제: 반영된 답가면 문단 복구 작업을 202로 돌려준다. */
    @DeleteMapping("/api/v1/replies/{replyId}") public ResponseEntity<ApiResponse<?>> delete(@PathVariable String replyId,@RequestHeader(required=false) String authorization){var accepted=replyService.delete(replyId,authorization);return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("job",com.dandimate.durooduroo.job.dto.JobResponse.from(accepted.job()))));}
    /** 말투 보존 평가를 저장한다. 다시 매기면 덮어쓴다. (본인 원문이 인용된 문단만 허용하는 규칙은 문단 출처를 저장한 뒤 적용한다.) */
    @PutMapping("/api/v1/paragraphs/{paragraphId}/fidelity-rating") @org.springframework.transaction.annotation.Transactional public ApiResponse<?> rating(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization,@RequestBody Map<String,Integer> body){var me=requireMemberOf(paragraphId,authorization);Integer score=body.get("score");if(score==null||score<1||score>5)throw new BusinessException("INVALID_SCORE","평점은 1~5점이어야 합니다.");ratings.findByParagraphIdAndMemberId(paragraphId,me.getId()).ifPresentOrElse(r->r.rate(score),()->ratings.save(com.dandimate.durooduroo.scroll.domain.ParagraphRating.of(paragraphId,me.getId(),score)));return ApiResponse.success(Map.of("score",score));}
    /** 아 맞다 반응은 참여자당 한 번. 여러 번 눌러도 수는 그대로다. */
    @PutMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda") @org.springframework.transaction.annotation.Transactional public ApiResponse<?> react(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization){var me=requireMemberOf(paragraphId,authorization);var p=paragraphs.findById(paragraphId).orElseThrow();if(reactions.findByParagraphIdAndMemberId(paragraphId,me.getId()).isEmpty())reactions.saveAndFlush(com.dandimate.durooduroo.scroll.domain.ParagraphReaction.ahMatda(paragraphId,me.getId()));p.updateAhMatdaCount(reactions.countByParagraphId(paragraphId));return ApiResponse.success(Map.of("ahMatdaCount",p.getAhMatdaCount()));}
    @DeleteMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda") @org.springframework.transaction.annotation.Transactional public ResponseEntity<Void> unreact(@PathVariable String paragraphId,@RequestHeader(required=false) String authorization){var me=requireMemberOf(paragraphId,authorization);var p=paragraphs.findById(paragraphId).orElseThrow();reactions.findByParagraphIdAndMemberId(paragraphId,me.getId()).ifPresent(r->{reactions.delete(r);reactions.flush();});p.updateAhMatdaCount(reactions.countByParagraphId(paragraphId));return ResponseEntity.noContent().build();}
    private com.dandimate.durooduroo.member.domain.Member requireMemberOf(String paragraphId,String authorization){var p=paragraphs.findById(paragraphId).orElseThrow(()->new BusinessException("PARAGRAPH_NOT_FOUND","문단이 없습니다."));return members.findByRoomIdAndMemberToken(p.getRoomId(),MemberTokenResolver.resolve(authorization)).orElseThrow(()->new BusinessException("MEMBER_FORBIDDEN","방 참여자만 사용할 수 있습니다."));}
    private String roomIdFor(com.dandimate.durooduroo.scroll.domain.Paragraph p){return p.getRoomId();}
}
