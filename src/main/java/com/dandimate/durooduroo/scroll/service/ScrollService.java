/**
 * 현재(최신 version) 두루마리를 조회한다 (명세 10장).
 * - 엮는 중이면 409 ROOM_WEAVING과 함께 error.details.job으로 진행 중인 작업을 알려 준다.
 * - 마감 전이면 409 INVALID_ROOM_STATE, 원문 없이 끝난 방이면 404 RESOURCE_NOT_FOUND(details.reason=EMPTY_ROOM).
 * - 삭제된 사진의 문단은 내보내지 않는다.
 */
package com.dandimate.durooduroo.scroll.service;

import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.dto.JobResponse;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.dto.MemberView;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.PhotoView;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.storage.MediaUrls;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource;
import com.dandimate.durooduroo.scroll.domain.Scroll;
import com.dandimate.durooduroo.scroll.dto.ScrollView;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ReplyRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScrollService {

    private final RoomRepository rooms;
    private final MemberRepository members;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final ReplyRepository replies;
    private final PhotoRepository photos;
    private final AiJobRepository jobs;
    private final MediaUrls mediaUrls;

    public ScrollService(RoomRepository rooms, MemberRepository members, ScrollRepository scrolls, ParagraphRepository paragraphs,
                         ParagraphSourceRepository sources, ReplyRepository replies, PhotoRepository photos, AiJobRepository jobs,
                         MediaUrls mediaUrls) {
        this.rooms = rooms;
        this.members = members;
        this.scrolls = scrolls;
        this.paragraphs = paragraphs;
        this.sources = sources;
        this.replies = replies;
        this.photos = photos;
        this.jobs = jobs;
        this.mediaUrls = mediaUrls;
    }

    @Transactional(readOnly = true)
    public ScrollView current(String roomId, String authorization) {
        Room room = rooms.findById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방입니다."));
        members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 조회할 수 있습니다."));
        if (room.getStatus() == RoomStatus.WEAVING) {
            Map<String, Object> details = new HashMap<>();
            details.put("job", jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.WEAVE_SCROLL,
                    List.of("QUEUED", "RUNNING", "FAILED")).map(JobResponse::from).orElse(null));
            throw new BusinessException(ErrorCode.ROOM_WEAVING, "두루마리를 엮는 중이에요.", details);
        }
        if (room.getStatus() != RoomStatus.GROWING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "아직 마감 전이에요.");
        }
        Scroll scroll = scrolls.findTopByRoomIdOrderByVersionDesc(roomId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "남겨진 이야기가 없어 두루마리가 비어 있어요.", Map.of("reason", "EMPTY_ROOM")));

        Map<String, Member> roomMembers = members.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<String, Photo> roomPhotos = photos.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Photo::getId, Function.identity()));
        List<Paragraph> scrollParagraphs = paragraphs.findAllByScrollIdOrderByDisplayOrderAsc(scroll.getId());
        Map<String, List<ParagraphSource>> sourcesByParagraph = sources.findAllByParagraphIdInOrderBySortOrderAsc(
                scrollParagraphs.stream().map(Paragraph::getId).toList()).stream().collect(Collectors.groupingBy(ParagraphSource::getParagraphId));

        List<ScrollView.ParagraphView> views = scrollParagraphs.stream()
                .filter(p -> roomPhotos.containsKey(p.getPhotoId()))
                .map(p -> {
                    Photo photo = roomPhotos.get(p.getPhotoId());
                    List<ScrollView.SourceView> paragraphSources = sourcesByParagraph.getOrDefault(p.getId(), List.of()).stream()
                            .map(s -> new ScrollView.SourceView(s.getEntryId(), MemberView.from(roomMembers.get(s.getMemberId())), s.getQuote()))
                            .toList();
                    return new ScrollView.ParagraphView(p.getId(), PhotoView.of(photo, roomMembers.get(photo.getMemberId()), mediaUrls.imageUrl(photo)),
                            p.getBody(), p.getVersion(), paragraphSources, replies.countByParagraphIdAndStatusIn(p.getId(), List.of("PENDING", "APPLIED", "REACTION")), p.getAhMatdaCount(), true);
                })
                .toList();
        int participantCount = (int) sourcesByParagraph.values().stream().flatMap(List::stream).map(ParagraphSource::getMemberId)
                .filter(Objects::nonNull).distinct().count();
        return new ScrollView(scroll.getId(), scroll.getVersion(), participantCount, roomMembers.size(), scroll.getCreatedAt(), views);
    }
}
