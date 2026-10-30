/**
 * 답가 반영과 문단 복구를 실행한다 (명세 9장). Worker가 REPLY_REWEAVE 작업을 넘긴다.
 * - 반영(targetId=답가): 분류 → REACTION_ONLY는 반응으로 저장, UNSAFE는 거절, NEW_FACT·CORRECTION(또는 신뢰도 0.6 미만)은 부분 재엮기.
 *   재엮기 결과는 8장 인용 검증과 "기존 인용 유지"를 통과해야 하고, 실패하면 원문 그대로 한 문장을 덧붙이는 기본 방식으로 대체한다.
 *   기준 문단 version이 그사이 바뀌었으면 최신 문단으로 다시 실행한다(최대 3회).
 * - 복구(targetId=문단, detail=REPLY_DELETED/ENTRY_DELETED): 현재 원문으로 문단을 다시 엮고, 남은 APPLIED 답가를 순서대로 다시 반영한다.
 * AI 호출은 트랜잭션 밖에서 하고, 결과·문단 버전·답가 상태는 한 트랜잭션으로 저장한다.
 */
package com.dandimate.durooduroo.scroll.service;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.reply.ParagraphReweaveAiService;
import com.dandimate.durooduroo.ai.reply.ReplyAiService;
import com.dandimate.durooduroo.ai.reply.ReplyClassificationCommand;
import com.dandimate.durooduroo.ai.reply.ReweaveCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult.WovenParagraph;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphVersion;
import com.dandimate.durooduroo.scroll.domain.Reply;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphVersionRepository;
import com.dandimate.durooduroo.scroll.repository.ReplyRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReplyReweaveService {

    private static final Logger log = LoggerFactory.getLogger(ReplyReweaveService.class);
    private static final double MIN_CONFIDENCE = 0.6;
    private static final int MAX_VERSION_RETRIES = 3;

    private final AiJobRepository jobs;
    private final ReplyRepository replies;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final ParagraphVersionRepository versions;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final NotificationRepository notifications;
    private final ScrollGenerationService generation;
    private final ReplyAiService classifier;
    private final ParagraphReweaveAiService reweaver;
    private final TransactionTemplate tx;

    public ReplyReweaveService(AiJobRepository jobs, ReplyRepository replies, ParagraphRepository paragraphs, ParagraphSourceRepository sources,
                               ParagraphVersionRepository versions, PhotoRepository photos, MemoryEntryRepository entries, MemberRepository members,
                               RoomRepository rooms, NotificationRepository notifications, ScrollGenerationService generation,
                               ReplyAiService classifier, ParagraphReweaveAiService reweaver, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.replies = replies;
        this.paragraphs = paragraphs;
        this.sources = sources;
        this.versions = versions;
        this.photos = photos;
        this.entries = entries;
        this.members = members;
        this.rooms = rooms;
        this.notifications = notifications;
        this.generation = generation;
        this.classifier = classifier;
        this.reweaver = reweaver;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void execute(String jobId) {
        AiJob job = tx.execute(status -> jobs.findById(jobId).orElseThrow());
        if (replies.existsById(job.getTargetId())) apply(job);
        else rebuild(job);
    }

    /** 문단 현재 상태 스냅샷 (AI 호출 전 읽기). */
    private record Snapshot(Paragraph paragraph, List<Citation> citations, Map<String, Member> members, List<MemoryEntry> photoEntries) {}

    private void apply(AiJob job) {
        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            Reply reply = tx.execute(status -> replies.findById(job.getTargetId()).orElseThrow());
            if (!Reply.PENDING.equals(reply.getStatus())) {
                succeed(job.getId());
                return;
            }
            Snapshot snapshot = tx.execute(status -> snapshot(reply.getParagraphId()));
            Member author = snapshot.members.get(reply.getMemberId());
            var classified = classifier.classify(new ReplyClassificationCommand(snapshot.paragraph.getBody(), reply.getId(), author.getNickname(), reply.getTranscript()));
            String classification = classified.confidence() < MIN_CONFIDENCE ? "NEW_FACT" : classified.classification();

            if (classification.equals("REACTION_ONLY") || classification.equals("UNSAFE")) {
                String status = classification.equals("UNSAFE") ? Reply.REJECTED : Reply.REACTION;
                tx.executeWithoutResult(s -> {
                    replies.findById(reply.getId()).orElseThrow().resolve(status, classification);
                    jobs.findById(job.getId()).orElseThrow().succeed();
                });
                return;
            }
            WovenParagraph woven = reweaveOrFallback(snapshot, reply, author, classification);
            Boolean saved = tx.execute(status -> {
                Paragraph paragraph = paragraphs.findById(reply.getParagraphId()).orElseThrow();
                if (paragraph.getVersion() != snapshot.paragraph.getVersion()) return false;
                revise(paragraph, woven, "REPLY_ADDED");
                replies.findById(reply.getId()).orElseThrow().resolve(Reply.APPLIED, classification);
                notifications.save(Notification.create(reply.getMemberId(), "REPLY_APPLIED", "PARAGRAPH", paragraph.getId()));
                jobs.findById(job.getId()).orElseThrow().succeed();
                return true;
            });
            if (Boolean.TRUE.equals(saved)) return;
            log.info("Paragraph {} changed while reweaving reply {}; retrying on the latest version", reply.getParagraphId(), reply.getId());
        }
        throw new IllegalStateException("paragraph version kept changing");
    }

    private void rebuild(AiJob job) {
        String paragraphId = job.getTargetId();
        record Rebuild(Paragraph paragraph, ScrollGenerationService.PhotoPlan photo, Map<String, Member> members, String roomName, List<Reply> applied) {}
        Rebuild plan = tx.execute(status -> {
            Paragraph paragraph = paragraphs.findById(paragraphId).orElse(null);
            Photo photo = paragraph == null ? null : photos.findById(paragraph.getPhotoId()).orElse(null);
            if (photo == null) return null;
            Map<String, Member> roomMembers = members.findAllByRoomId(paragraph.getRoomId()).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
            List<MemoryEntry> photoEntries = entries.findAllByPhotoId(photo.getId()).stream().sorted(Comparator.comparing(MemoryEntry::getCreatedAt)).toList();
            return new Rebuild(paragraph, new ScrollGenerationService.PhotoPlan(photo, paragraph.getDisplayOrder(), photoEntries), roomMembers,
                    rooms.findById(paragraph.getRoomId()).orElseThrow().getTitle(), replies.findAllByParagraphIdAndStatusOrderByCreatedAtAsc(paragraphId, Reply.APPLIED));
        });
        if (plan == null) {
            succeed(job.getId());
            return;
        }
        var base = generation.weavePhoto(plan.paragraph.getRoomId(), plan.roomName, plan.photo, plan.members);
        WovenParagraph current = new WovenParagraph(plan.photo.photo().getId(), base.body(), base.citations(), null);
        for (Reply reply : plan.applied) {
            Snapshot snapshot = new Snapshot(plan.paragraph, current.citations(), plan.members, plan.photo.entries());
            current = reweaveOrFallback(snapshot, current.body(), reply, plan.members.get(reply.getMemberId()), reply.getClassification());
        }
        WovenParagraph rebuilt = current;
        tx.executeWithoutResult(status -> {
            revise(paragraphs.findById(paragraphId).orElseThrow(), rebuilt, job.getDetail() == null ? "REPLY_DELETED" : job.getDetail());
            jobs.findById(job.getId()).orElseThrow().succeed();
        });
    }

    private Snapshot snapshot(String paragraphId) {
        Paragraph paragraph = paragraphs.findById(paragraphId).orElseThrow();
        List<Citation> citations = sources.findAllByParagraphIdInOrderBySortOrderAsc(List.of(paragraphId)).stream()
                .map(s -> new Citation(s.getEntryId(), s.getMemberId(), s.getQuote())).toList();
        Map<String, Member> roomMembers = members.findAllByRoomId(paragraph.getRoomId()).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return new Snapshot(paragraph, citations, roomMembers, entries.findAllByPhotoId(paragraph.getPhotoId()));
    }

    private WovenParagraph reweaveOrFallback(Snapshot snapshot, Reply reply, Member author, String classification) {
        return reweaveOrFallback(snapshot, snapshot.paragraph.getBody(), reply, author, classification);
    }

    private WovenParagraph reweaveOrFallback(Snapshot snapshot, String body, Reply reply, Member author, String classification) {
        var command = new ReweaveCommand(snapshot.paragraph.getId(), snapshot.paragraph.getPhotoId(), snapshot.paragraph.getVersion(), body,
                snapshot.citations, new ReweaveCommand.ReplyInput(reply.getId(), reply.getMemberId(), author.getNickname(), reply.getTranscript()), classification);
        WovenParagraph result = reweaver.reweave(command).paragraph();
        if (isValid(snapshot, reply, result)) return result;
        log.warn("Reweave of paragraph {} for reply {} failed validation; appending verbatim", snapshot.paragraph.getId(), reply.getId());
        String verb = "CORRECTION".equals(classification) ? "정정했다" : "덧붙였다";
        List<Citation> citations = new ArrayList<>(snapshot.citations);
        citations.add(new Citation(reply.getId(), reply.getMemberId(), reply.getTranscript().strip()));
        return new WovenParagraph(snapshot.paragraph.getPhotoId(), body + " " + author.getNickname() + "은(는) ‘" + reply.getTranscript().strip() + "’라고 " + verb + ".", citations, null);
    }

    /** 8장 검증 + 기존 인용 유지: 인용은 이 사진의 원문이거나 이 답가(또는 이미 반영된 같은 문단의 답가)여야 하고 원문의 부분 문자열이어야 한다. */
    private boolean isValid(Snapshot snapshot, Reply reply, WovenParagraph result) {
        if (result == null || result.body() == null || result.body().isBlank() || result.citations() == null) return false;
        Set<String> kept = result.citations().stream().map(Citation::entryId).collect(Collectors.toSet());
        if (!kept.containsAll(snapshot.citations.stream().map(Citation::entryId).toList())) return false;
        Map<String, String[]> allowed = new HashMap<>();
        snapshot.photoEntries.forEach(e -> allowed.put(e.getId(), new String[]{e.getMemberId(), e.getTranscript()}));
        replies.findAllByParagraphIdAndStatusOrderByCreatedAtAsc(snapshot.paragraph.getId(), Reply.APPLIED)
                .forEach(r -> allowed.put(r.getId(), new String[]{r.getMemberId(), r.getTranscript()}));
        allowed.put(reply.getId(), new String[]{reply.getMemberId(), reply.getTranscript()});
        return result.citations().stream().allMatch(c -> {
            String[] source = allowed.get(c.entryId());
            return source != null && source[0].equals(c.memberId()) && c.exactQuote() != null && !c.exactQuote().isBlank() && source[1].contains(c.exactQuote());
        });
    }

    private void revise(Paragraph paragraph, WovenParagraph woven, String reason) {
        paragraph.revise(woven.body());
        generation.saveSources(paragraph.getId(), woven.citations());
        versions.save(ParagraphVersion.of(paragraph.getId(), paragraph.getVersion(), woven.body(), ScrollGenerationService.sourcesSummary(woven.citations()), reason));
    }

    private void succeed(String jobId) {
        tx.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
            if ("RUNNING".equals(job.getStatus())) job.succeed();
        }));
    }
}
