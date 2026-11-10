/** 명세서 기준 답가·문단 반응 API입니다. */
package com.dandimate.durooduroo.scroll.controller;

import com.dandimate.durooduroo.common.api.ApiResponse;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.job.dto.JobResponse;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.scroll.domain.ParagraphRating;
import com.dandimate.durooduroo.scroll.domain.ParagraphReaction;
import com.dandimate.durooduroo.scroll.domain.Reply;
import com.dandimate.durooduroo.scroll.repository.ParagraphRatingRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphReactionRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ReplyRepository;
import com.dandimate.durooduroo.scroll.service.ReplyService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReplyController {

    private final ParagraphRepository paragraphs;
    private final ReplyService replyService;
    private final MemberRepository members;
    private final ParagraphReactionRepository reactions;
    private final ParagraphRatingRepository ratings;
    private final ReplyRepository replies;

    public ReplyController(ParagraphRepository paragraphs, ReplyService replyService, MemberRepository members,
                           ParagraphReactionRepository reactions, ParagraphRatingRepository ratings,
                           ReplyRepository replies) {
        this.paragraphs = paragraphs;
        this.replyService = replyService;
        this.members = members;
        this.reactions = reactions;
        this.ratings = ratings;
        this.replies = replies;
    }

    /** 답가 확정: 202와 REPLY_REWEAVE 작업. 반영 결과(APPLIED/REACTION/REJECTED)는 작업이 끝난 뒤 정해진다. */
    @PostMapping("/api/v1/paragraphs/{paragraphId}/replies")
    public ResponseEntity<ApiResponse<?>> create(@PathVariable String paragraphId,
                                                 @RequestHeader(required = false) String authorization,
                                                 @RequestBody Map<String, String> body) {
        var accepted = replyService.create(paragraphId, authorization,
                body.getOrDefault("inputMode", "TEXT"), body.get("transcriptionId"), body.get("transcript"));
        var reply = Map.of("id", accepted.reply().getId(), "status", accepted.reply().getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(Map.of("reply", reply, "job", JobResponse.from(accepted.job()))));
    }

    /** 본인 답가 삭제: 반영된 답가면 문단 복구 작업을 202로 돌려준다. */
    @DeleteMapping("/api/v1/replies/{replyId}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable String replyId,
                                                 @RequestHeader(required = false) String authorization) {
        var accepted = replyService.delete(replyId, authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(Map.of("job", JobResponse.from(accepted.job()))));
    }

    /** 문단에 남은 답가를 오래된 순서로 조회한다. 삭제된 답가는 노출하지 않는다. */
    @GetMapping("/api/v1/paragraphs/{paragraphId}/replies")
    public ApiResponse<?> list(@PathVariable String paragraphId,
                               @RequestHeader(required = false) String authorization,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        Member me = requireMemberOf(paragraphId, authorization);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page와 size 범위가 올바르지 않습니다.");
        }
        var all = replies.findAllByParagraphIdAndStatusNotOrderByCreatedAtAsc(paragraphId, Reply.DELETED);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        var items = all.subList(from, to).stream().map(reply -> toItem(reply, me)).toList();
        return ApiResponse.success(Map.of("items", items, "page", page, "size", size,
                "totalCount", all.size(), "hasNext", to < all.size()));
    }

    /** 말투 보존 평가를 저장한다. 다시 매기면 덮어쓴다. (본인 원문이 인용된 문단만 허용하는 규칙은 문단 출처를 저장한 뒤 적용한다.) */
    @PutMapping("/api/v1/paragraphs/{paragraphId}/fidelity-rating")
    @Transactional
    public ApiResponse<?> rating(@PathVariable String paragraphId,
                                 @RequestHeader(required = false) String authorization,
                                 @RequestBody Map<String, Integer> body) {
        Member me = requireMemberOf(paragraphId, authorization);
        Integer score = body.get("score");
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "평점은 1~5점이어야 합니다.");
        }
        var paragraph = paragraphs.findById(paragraphId).orElseThrow();
        ratings.findByParagraphIdAndMemberId(paragraphId, me.getId()).ifPresentOrElse(
                rating -> rating.rate(score, paragraph.getVersion()),
                () -> ratings.save(ParagraphRating.of(paragraphId, me.getId(), score, paragraph.getVersion())));
        return ApiResponse.success(Map.of("score", score, "paragraphVersion", paragraph.getVersion()));
    }

    /** 아 맞다 반응은 참여자당 한 번. 여러 번 눌러도 수는 그대로다. */
    @PutMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda")
    @Transactional
    public ApiResponse<?> react(@PathVariable String paragraphId,
                                @RequestHeader(required = false) String authorization) {
        Member me = requireMemberOf(paragraphId, authorization);
        var paragraph = paragraphs.findById(paragraphId).orElseThrow();
        if (reactions.findByParagraphIdAndMemberId(paragraphId, me.getId()).isEmpty()) {
            reactions.saveAndFlush(ParagraphReaction.ahMatda(paragraphId, me.getId()));
        }
        paragraph.updateAhMatdaCount(reactions.countByParagraphId(paragraphId));
        return ApiResponse.success(Map.of("ahMatdaCount", paragraph.getAhMatdaCount()));
    }

    @DeleteMapping("/api/v1/paragraphs/{paragraphId}/reactions/ah-matda")
    @Transactional
    public ResponseEntity<Void> unreact(@PathVariable String paragraphId,
                                        @RequestHeader(required = false) String authorization) {
        Member me = requireMemberOf(paragraphId, authorization);
        var paragraph = paragraphs.findById(paragraphId).orElseThrow();
        reactions.findByParagraphIdAndMemberId(paragraphId, me.getId()).ifPresent(reaction -> {
            reactions.delete(reaction);
            reactions.flush();
        });
        paragraph.updateAhMatdaCount(reactions.countByParagraphId(paragraphId));
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toItem(Reply reply, Member me) {
        var author = members.findById(reply.getMemberId()).orElse(null);
        return Map.of(
                "id", reply.getId(),
                "memberId", reply.getMemberId(),
                "nickname", author == null ? "알 수 없음" : author.getNickname(),
                "transcript", reply.getTranscript(),
                "status", reply.getStatus(),
                "createdAt", reply.getCreatedAt(),
                "mine", reply.getMemberId().equals(me.getId()));
    }

    private Member requireMemberOf(String paragraphId, String authorization) {
        var paragraph = paragraphs.findById(paragraphId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "문단이 없습니다."));
        return members.findByRoomIdAndMemberToken(paragraph.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
    }
}
