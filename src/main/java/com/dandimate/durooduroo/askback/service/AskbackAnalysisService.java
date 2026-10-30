/**
 * 원문이 바뀔 때 되묻기 분석 작업을 만들고, Worker가 넘긴 작업을 실행한다 (명세 6장·6-1장).
 * - 같은 사진에 QUEUED 작업이 있으면 새로 만들지 않는다. Worker는 실행 시점의 최신 원문을 읽는다.
 * - 대상 후보(FR-D2): 사진을 올린 사람, 그 사진에 원문을 남긴 사람, 원문에 이름이 나왔지만 원문이 없는 사람.
 *   FORGOT 응답자·이미 미응답 질문이 있는 사람·수신 한도(방 전체 2개) 도달자는 뺀다.
 * - 사진당 1회, 이전과 다른 구멍 유형이면 1회 추가. NO_ADDON은 덧붙임 0개이고 첫 한마디 20자 미만일 때만 허용한다.
 * - AI 결과는 신뢰도 0.6 이상, 허용 목록의 대상, 해당 사진의 원문 근거, 60자 이내, 금칙 표현 없음일 때만 저장한다.
 * - 마감 24시간 전부터는 새 질문을 만들지 않고, 분석 중 원문이 바뀌었으면(sourceRevision) 결과를 버린다.
 * 검증·한도에 걸리면 저장하지 않고 askback_skipped 이벤트에 사유를 남긴다.
 */
package com.dandimate.durooduroo.askback.service;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.ai.askback.AskbackCommand;
import com.dandimate.durooduroo.ai.askback.AskbackResult;
import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.dandimate.durooduroo.ai.domain.AiJob;
import com.dandimate.durooduroo.ai.repository.AiJobRepository;
import com.dandimate.durooduroo.askback.domain.Askback;
import com.dandimate.durooduroo.askback.repository.AskbackRepository;
import com.dandimate.durooduroo.event.EventLogger;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AskbackAnalysisService {

    public static final int RECEIVE_LIMIT_PER_MEMBER = 2;
    public static final int MAX_QUESTION_LENGTH = 60;
    public static final double MIN_CONFIDENCE = 0.6;
    private static final List<String> GAP_TYPES = List.of("CONFLICT", "MISSING_REASON", "NO_ADDON", "MISSING_CONTEXT");
    private static final List<String> FORBIDDEN_PHRASES = List.of("왜 기억 못", "기억 안 나", "기억도 못");

    private final AiJobRepository jobs;
    private final PhotoRepository photos;
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final MemoryEntryRepository entries;
    private final AskbackRepository askbacks;
    private final NotificationRepository notifications;
    private final AskbackAiService ai;
    private final EventLogger events;
    private final TransactionTemplate tx;

    public AskbackAnalysisService(AiJobRepository jobs, PhotoRepository photos, RoomRepository rooms, MemberRepository members,
                                  MemoryEntryRepository entries, AskbackRepository askbacks, NotificationRepository notifications,
                                  AskbackAiService ai, EventLogger events, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.photos = photos;
        this.rooms = rooms;
        this.members = members;
        this.entries = entries;
        this.askbacks = askbacks;
        this.notifications = notifications;
        this.ai = ai;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 원문 확정·수정·삭제 후 호출한다. 질문을 만들 수 없는 상태면 null. */
    @Transactional
    public String enqueue(String photoId) {
        Photo photo = photos.findById(photoId).orElse(null);
        if (photo == null || !photo.isPublished()) return null;
        Room room = rooms.findById(photo.getRoomId()).orElseThrow();
        if (room.getStatus() != RoomStatus.CIRCULATING || pastQuestionCutoff(room)) return null;
        return jobs.findFirstByTypeAndTargetIdAndStatus(AiJob.ASKBACK_ANALYZE, photoId, "QUEUED")
                .orElseGet(() -> jobs.save(AiJob.queued(room.getId(), AiJob.ASKBACK_ANALYZE, photoId))).getId();
    }

    /** Worker가 선점(RUNNING)한 작업을 실행한다. AI 호출은 트랜잭션 밖에서 한다. */
    public void execute(String jobId) {
        Prepared prepared = tx.execute(status -> prepare(jobId));
        if (prepared == null) {
            finish(jobId);
            return;
        }
        AskbackResult result = ai.analyze(prepared.command);
        tx.executeWithoutResult(status -> {
            save(prepared, result);
            jobs.findById(jobId).orElseThrow().succeed();
        });
    }

    private record Prepared(String roomId, String photoId, int revision, AskbackCommand command) {}

    private Prepared prepare(String jobId) {
        AiJob job = jobs.findById(jobId).orElseThrow();
        Photo photo = photos.findById(job.getTargetId()).orElse(null);
        if (photo == null) return null;
        Room room = rooms.findById(photo.getRoomId()).orElseThrow();
        if (room.getStatus() != RoomStatus.CIRCULATING || pastQuestionCutoff(room)) return null;

        List<Member> roomMembers = members.findAllByRoomId(room.getId());
        Map<String, Member> byId = new java.util.HashMap<>();
        roomMembers.forEach(m -> byId.put(m.getId(), m));
        List<MemoryEntry> photoEntries = entries.findAllByPhotoId(photo.getId()).stream()
                .sorted(java.util.Comparator.comparing(MemoryEntry::getCreatedAt)).toList();
        if (photoEntries.isEmpty()) return null;
        List<Askback> previous = askbacks.findAllByPhotoId(photo.getId());

        List<String> allowedGapTypes = allowedGapTypes(photoEntries, previous);
        if (allowedGapTypes.isEmpty()) {
            events.log(room.getId(), null, "askback_skipped", Map.of("reason", "photo_limit"));
            return null;
        }
        List<String> eligible = eligibleMembers(room, photo, roomMembers, photoEntries, previous);
        if (eligible.isEmpty()) {
            events.log(room.getId(), null, "askback_skipped", Map.of("reason", "no_eligible_member"));
            return null;
        }
        AskbackCommand command = new AskbackCommand(
                new PhotoInput(photo.getId(), photo.getMemberId(), null),
                roomMembers.stream().map(m -> new MemberInput(m.getId(), m.getNickname(), List.of())).toList(),
                photoEntries.stream().map(e -> new EntryInput(e.getId(), e.getMemberId(), byId.get(e.getMemberId()).getNickname(), e.getKind().name(), e.getTranscript())).toList(),
                previous.stream().map(a -> new AskbackCommand.PreviousAskback(a.getGapType(), a.getTargetMemberId(), a.getQuestion(), a.getStatus())).toList(),
                new AskbackCommand.Limits(eligible, allowedGapTypes));
        return new Prepared(room.getId(), photo.getId(), photo.getSourceRevision(), command);
    }

    private void save(Prepared prepared, AskbackResult result) {
        if (!result.shouldAsk()) return;
        Photo photo = photos.findById(prepared.photoId).orElse(null);
        String reason = rejectReason(prepared, result, photo);
        if (reason != null) {
            events.log(prepared.roomId, result.targetMemberId(), "askback_skipped", Map.of("reason", reason));
            return;
        }
        Room room = rooms.findById(prepared.roomId).orElseThrow();
        Askback askback = askbacks.save(Askback.create(prepared.roomId, prepared.photoId, result.targetMemberId(), result.gapType(),
                result.question().strip(), result.evidenceEntryIds(), room.getDeadlineAt()));
        notifications.save(Notification.create(result.targetMemberId(), "ASKBACK_RECEIVED", "ASKBACK", askback.getId()));
        events.log(prepared.roomId, result.targetMemberId(), "askback_sent", Map.of("type", result.gapType(), "target", result.targetMemberId()));
    }

    /** Spring이 다시 검증하는 일 (명세 6장). 저장해도 되면 null. */
    private String rejectReason(Prepared prepared, AskbackResult result, Photo photo) {
        if (photo == null) return "photo_deleted";
        if (photo.getSourceRevision() != prepared.revision) return "stale_revision";
        Room room = rooms.findById(prepared.roomId).orElseThrow();
        if (room.getStatus() != RoomStatus.CIRCULATING || pastQuestionCutoff(room)) return "room_state";
        if (result.confidence() < MIN_CONFIDENCE) return "low_confidence";
        var limits = prepared.command.limits();
        if (!limits.eligibleMemberIds().contains(result.targetMemberId())) return "target_not_eligible";
        if (!limits.allowedGapTypes().contains(result.gapType())) return "gap_type_not_allowed";
        String question = result.question() == null ? "" : result.question().strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) return "question_length";
        if (FORBIDDEN_PHRASES.stream().anyMatch(question::contains)) return "forbidden_phrase";
        Set<String> photoEntryIds = new java.util.HashSet<>(prepared.command.entries().stream().map(EntryInput::id).toList());
        if (result.evidenceEntryIds().isEmpty() || !photoEntryIds.containsAll(result.evidenceEntryIds())) return "invalid_evidence";
        // 분석하는 사이 다른 작업이 같은 대상에게 질문을 만들었을 수 있다.
        if (askbacks.findAllByPhotoId(prepared.photoId).stream().anyMatch(a -> a.getTargetMemberId().equals(result.targetMemberId()) && Askback.PENDING.equals(a.getStatus()))) return "already_pending";
        if (askbacks.countByRoomIdAndTargetMemberId(prepared.roomId, result.targetMemberId()) >= RECEIVE_LIMIT_PER_MEMBER) return "receive_limit";
        return null;
    }

    /** 사진당 1회, 다른 구멍 유형이 생기면 1회 추가. NO_ADDON은 기계적으로 판정한다. */
    private List<String> allowedGapTypes(List<MemoryEntry> photoEntries, List<Askback> previous) {
        if (previous.size() >= 2) return List.of();
        Set<String> used = new java.util.HashSet<>(previous.stream().map(Askback::getGapType).toList());
        boolean noAddon = photoEntries.stream().noneMatch(e -> e.getKind() == EntryKind.ADDON);
        boolean shortFirst = photoEntries.stream().filter(e -> e.getKind() == EntryKind.FIRST).allMatch(e -> e.getTranscript().strip().length() < 20);
        return GAP_TYPES.stream()
                .filter(type -> !used.contains(type))
                .filter(type -> !type.equals("NO_ADDON") || (noAddon && shortFirst))
                .toList();
    }

    private List<String> eligibleMembers(Room room, Photo photo, List<Member> roomMembers, List<MemoryEntry> photoEntries, List<Askback> previous) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(photo.getMemberId());
        photoEntries.forEach(e -> candidates.add(e.getMemberId()));
        String allText = String.join(" ", photoEntries.stream().map(MemoryEntry::getTranscript).toList());
        roomMembers.stream().filter(m -> allText.contains(m.getNickname())).forEach(m -> candidates.add(m.getId()));

        List<String> eligible = new ArrayList<>();
        for (String memberId : candidates) {
            boolean forgot = previous.stream().anyMatch(a -> a.getTargetMemberId().equals(memberId) && "FORGOT".equals(a.getStatus()));
            boolean pending = previous.stream().anyMatch(a -> a.getTargetMemberId().equals(memberId) && Askback.PENDING.equals(a.getStatus()));
            boolean limited = askbacks.countByRoomIdAndTargetMemberId(room.getId(), memberId) >= RECEIVE_LIMIT_PER_MEMBER;
            if (!forgot && !pending && !limited) eligible.add(memberId);
        }
        return eligible;
    }

    /** 마감 24시간 전부터는 새 질문을 만들지 않는다. */
    private boolean pastQuestionCutoff(Room room) {
        return !LocalDateTime.now().isBefore(room.getDeadlineAt().minusHours(24));
    }

    private void finish(String jobId) {
        tx.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
            if ("RUNNING".equals(job.getStatus())) job.succeed();
        }));
    }
}
