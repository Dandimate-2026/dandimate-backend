/**
 * 돌려보기 카드와 서버 측 잠금을 계산한다.
 * - 내 사진: 모든 이야기를 보여 준다(OWN_PHOTO).
 * - 타인 사진에 내 덧붙임·패스가 없으면 첫 한마디만 보여 주고 otherEntries는 응답에 넣지 않는다.
 * - 덧붙임·패스를 남기면 ADDON·ASKBACK_ANSWER를 모두 보여 준다.
 * 노출되지 않은 사진(업로드만 하고 첫 한마디·"나중에"가 없는 사진)은 카드에 넣지 않는다.
 */
package com.dandimate.durooduroo.memory.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.domain.PhotoPass;
import com.dandimate.durooduroo.memory.dto.EntryView;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.memory.repository.PhotoPassRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.PhotoView;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CirculationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final MemberRepository members;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final PhotoPassRepository passes;

    public CirculationService(MemberRepository members, PhotoRepository photos, MemoryEntryRepository entries, PhotoPassRepository passes) {
        this.members = members;
        this.photos = photos;
        this.entries = entries;
        this.passes = passes;
    }

    public record Card(PhotoView photo, EntryView firstEntry, MyResponse myResponse, boolean locked, String lockReason, List<EntryView> otherEntries) {
    }

    public record MyResponse(String status) {
    }

    public record Page(List<Card> cards, String nextCursor) {
    }

    /** cursor는 이전 페이지가 돌려준 다음 시작 위치(불투명 문자열)다. */
    @Transactional(readOnly = true)
    public Page cards(String roomId, String authorization, String cursor, Integer size) {
        Member me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 조회할 수 있습니다."));
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size는 1~50이어야 합니다.");
        int offset = parseCursor(cursor);

        Map<String, Member> roomMembers = members.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<String, List<MemoryEntry>> entriesByPhoto = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId));
        Set<String> passedPhotoIds = passes.findAllByRoomIdAndMemberId(roomId, me.getId()).stream()
                .map(PhotoPass::getPhotoId).collect(Collectors.toSet());

        List<Photo> published = photos.findAllByRoomId(roomId).stream()
                .filter(Photo::isPublished)
                .sorted(Comparator.comparing(Photo::getPublishedAt).thenComparing(Photo::getId))
                .toList();
        List<Card> cards = published.stream().skip(offset).limit(pageSize)
                .map(photo -> card(photo, me, roomMembers, entriesByPhoto.getOrDefault(photo.getId(), List.of()), passedPhotoIds))
                .toList();
        String nextCursor = offset + pageSize < published.size() ? String.valueOf(offset + pageSize) : null;
        return new Page(cards, nextCursor);
    }

    private Card card(Photo photo, Member me, Map<String, Member> roomMembers, List<MemoryEntry> photoEntries, Set<String> passedPhotoIds) {
        EntryView first = photoEntries.stream().filter(e -> e.getKind() == EntryKind.FIRST).findFirst()
                .map(e -> EntryView.of(e, roomMembers.get(e.getMemberId()))).orElse(null);
        String status = responseStatus(photo, me, photoEntries, passedPhotoIds);
        boolean locked = status.equals("NONE");
        List<EntryView> others = locked ? List.of() : photoEntries.stream()
                .filter(e -> e.getKind() != EntryKind.FIRST)
                .map(e -> EntryView.of(e, roomMembers.get(e.getMemberId())))
                .toList();
        return new Card(PhotoView.of(photo, roomMembers.get(photo.getMemberId())), first, new MyResponse(status),
                locked, locked ? "RESPOND_FIRST" : null, others);
    }

    private String responseStatus(Photo photo, Member me, List<MemoryEntry> photoEntries, Set<String> passedPhotoIds) {
        if (photo.getMemberId().equals(me.getId())) return "OWN_PHOTO";
        boolean addon = photoEntries.stream().anyMatch(e -> e.getKind() == EntryKind.ADDON && e.getMemberId().equals(me.getId()));
        if (addon) return "ADDON";
        return passedPhotoIds.contains(photo.getId()) ? "PASSED" : "NONE";
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            int offset = Integer.parseInt(cursor);
            if (offset < 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "cursor가 올바르지 않습니다.");
        }
    }
}
