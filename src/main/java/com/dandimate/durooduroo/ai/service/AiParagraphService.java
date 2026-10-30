/**
 * MVP 두루마리 생성 서비스입니다. ScrollWeaveAiService(DEV-D)로 바꾸기 전까지 원문 나열형 문단을 만든다.
 * - 돌려보기에 노출된 사진 1장당 문단 1개, 노출 순서대로.
 * - 문단은 그 사진의 원문을 그대로 인용하고, 인용마다 출처(entryId·작성자·quote)를 저장한다.
 * - 원문이 없는 사진은 올린 사람의 기본 문구로 채운다 (FR-E5).
 */
package com.dandimate.durooduroo.ai.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource;
import com.dandimate.durooduroo.scroll.domain.Scroll;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiParagraphService {

    private final MemoryEntryRepository entries;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final PhotoRepository photos;
    private final MemberRepository members;

    public AiParagraphService(MemoryEntryRepository entries, ScrollRepository scrolls, ParagraphRepository paragraphs,
                              ParagraphSourceRepository sources, PhotoRepository photos, MemberRepository members) {
        this.entries = entries;
        this.scrolls = scrolls;
        this.paragraphs = paragraphs;
        this.sources = sources;
        this.photos = photos;
        this.members = members;
    }

    public void requireHost(String roomId, String authorization) {
        String token = MemberTokenResolver.resolve(authorization);
        var me = members.findByRoomIdAndMemberToken(roomId, token).orElseThrow(() -> new BusinessException("MEMBER_FORBIDDEN", "방 참여자만 실행할 수 있습니다."));
        if (me.getRole() != MemberRole.HOST) throw new BusinessException("HOST_ONLY", "방장만 실행할 수 있습니다.");
    }

    @Transactional
    public Scroll generate(String roomId) {
        Map<String, Member> roomMembers = members.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<String, List<MemoryEntry>> entriesByPhoto = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId));
        List<Photo> published = photos.findAllByRoomId(roomId).stream().filter(Photo::isPublished)
                .sorted(Comparator.comparing(Photo::getPublishedAt).thenComparing(Photo::getId)).toList();
        List<String> bodies = published.stream()
                .map(photo -> body(photo, entriesByPhoto.getOrDefault(photo.getId(), List.of()), roomMembers))
                .toList();
        int version = scrolls.findTopByRoomIdOrderByVersionDesc(roomId).map(s -> s.getVersion() + 1).orElse(1);

        Scroll scroll = scrolls.save(Scroll.create(roomId, version, String.join("\n\n", bodies)));
        for (int i = 0; i < published.size(); i++) {
            Photo photo = published.get(i);
            Paragraph paragraph = paragraphs.save(Paragraph.create(roomId, scroll.getId(), photo.getId(), i + 1, bodies.get(i)));
            List<MemoryEntry> photoEntries = entriesByPhoto.getOrDefault(photo.getId(), List.of());
            for (int order = 0; order < photoEntries.size(); order++) {
                MemoryEntry entry = photoEntries.get(order);
                sources.save(ParagraphSource.of(paragraph.getId(), entry.getId(), entry.getMemberId(), entry.getTranscript(), order));
            }
        }
        return scroll;
    }

    private String body(Photo photo, List<MemoryEntry> photoEntries, Map<String, Member> roomMembers) {
        if (photoEntries.isEmpty()) return roomMembers.get(photo.getMemberId()).getNickname() + "님이 올린 사진이에요.";
        return photoEntries.stream()
                .map(e -> roomMembers.get(e.getMemberId()).getNickname() + "은(는) ‘" + e.getTranscript() + "’라고 기억했다.")
                .collect(Collectors.joining(" "));
    }
}
