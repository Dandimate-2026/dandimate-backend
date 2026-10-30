/**
 * 두루마리를 만든다 (명세 7·8장). AI 호출이 DB 트랜잭션을 붙잡지 않도록 세 단계로 나눈다.
 * 1. prepare: 노출된 사진·원문·참여자를 읽는다(읽기 전용 트랜잭션).
 * 2. weave: 원문이 있는 사진마다 장면 카드를 만들고 한 번에 엮은 뒤, 문단마다 인용을 검증한다(트랜잭션 없음).
 *    검증 실패한 사진은 그 사진만 최대 2회 다시 엮고, 그래도 실패하면 원문 나열형 기본 문단으로 대체한다.
 *    원문이 없는 사진은 AI를 거치지 않고 올린 사람의 기본 문구로 채운다 (FR-E5).
 * 3. save: 다음 version의 두루마리와 문단·인용 출처를 저장한다(호출한 쪽 트랜잭션).
 */
package com.dandimate.durooduroo.scroll.service;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.dandimate.durooduroo.ai.scene.SceneCardAiService;
import com.dandimate.durooduroo.ai.scene.SceneCardCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult.WovenParagraph;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.domain.Paragraph;
import com.dandimate.durooduroo.scroll.domain.ParagraphSource;
import com.dandimate.durooduroo.scroll.domain.ParagraphVersion;
import com.dandimate.durooduroo.scroll.domain.Scroll;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphSourceRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphVersionRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScrollGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ScrollGenerationService.class);
    private static final int MAX_PARAGRAPH_RETRIES = 2;

    private final RoomRepository rooms;
    private final MemoryEntryRepository entries;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final ParagraphSourceRepository sources;
    private final PhotoRepository photos;
    private final MemberRepository members;
    private final SceneCardAiService sceneCards;
    private final ScrollWeaveAiService weaver;
    private final ParagraphVersionRepository versions;

    public ScrollGenerationService(RoomRepository rooms, MemoryEntryRepository entries, ScrollRepository scrolls, ParagraphRepository paragraphs,
                                   ParagraphSourceRepository sources, PhotoRepository photos, MemberRepository members,
                                   SceneCardAiService sceneCards, ScrollWeaveAiService weaver, ParagraphVersionRepository versions) {
        this.rooms = rooms;
        this.entries = entries;
        this.scrolls = scrolls;
        this.paragraphs = paragraphs;
        this.sources = sources;
        this.photos = photos;
        this.members = members;
        this.sceneCards = sceneCards;
        this.weaver = weaver;
        this.versions = versions;
    }

    public record Plan(String roomId, String roomName, List<PhotoPlan> photos, Map<String, Member> members) {}

    public record PhotoPlan(Photo photo, int displayOrder, List<MemoryEntry> entries) {}

    /** 사진 ID별 최종 문단. aiGenerated=false는 기본 문단(원문 없음·검증 실패 대체). */
    public record Woven(String body, List<Citation> citations, boolean aiGenerated) {}

    @Transactional(readOnly = true)
    public Plan prepare(String roomId) {
        Room room = rooms.findById(roomId).orElseThrow();
        Map<String, Member> roomMembers = members.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<String, List<MemoryEntry>> entriesByPhoto = entries.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .collect(Collectors.groupingBy(MemoryEntry::getPhotoId));
        List<Photo> published = photos.findAllByRoomId(roomId).stream().filter(Photo::isPublished)
                .sorted(Comparator.comparing(Photo::getPublishedAt).thenComparing(Photo::getId)).toList();
        List<PhotoPlan> plans = new ArrayList<>();
        for (int i = 0; i < published.size(); i++) {
            plans.add(new PhotoPlan(published.get(i), i + 1, entriesByPhoto.getOrDefault(published.get(i).getId(), List.of())));
        }
        return new Plan(roomId, room.getTitle(), plans, roomMembers);
    }

    /** AI를 호출한다. AiFailure는 그대로 던져 Job 재시도로 넘긴다. */
    public Map<String, Woven> weave(Plan plan) {
        return weavePhotos(plan.roomId(), plan.roomName(), plan.photos(), plan.members());
    }

    /** 사진 한 장의 문단만 다시 만든다 (답가 삭제·원문 삭제 후 복구). */
    public Woven weavePhoto(String roomId, String roomName, PhotoPlan photo, Map<String, Member> roomMembers) {
        return weavePhotos(roomId, roomName, List.of(photo), roomMembers).get(photo.photo().getId());
    }

    private Map<String, Woven> weavePhotos(String roomId, String roomName, List<PhotoPlan> photoPlans, Map<String, Member> roomMembers) {
        List<PhotoPlan> withEntries = photoPlans.stream().filter(p -> !p.entries().isEmpty()).toList();
        Map<String, ScrollWeaveCommand.SourceEntry> sourceEntries = new HashMap<>();
        Map<String, ScrollWeaveCommand.OrderedSceneCard> cards = new LinkedHashMap<>();
        for (PhotoPlan photo : withEntries) {
            List<EntryInput> inputs = photo.entries().stream().map(e -> new EntryInput(e.getId(), e.getMemberId(),
                    roomMembers.get(e.getMemberId()).getNickname(), e.getKind().name(), e.getTranscript())).toList();
            photo.entries().forEach(e -> sourceEntries.put(e.getId(), new ScrollWeaveCommand.SourceEntry(photo.photo().getId(), e.getMemberId(),
                    roomMembers.get(e.getMemberId()).getNickname(), e.getTranscript())));
            var card = sceneCards.extract(new SceneCardCommand(new PhotoInput(photo.photo().getId(), photo.photo().getMemberId(), null), inputs));
            cards.put(photo.photo().getId(), new ScrollWeaveCommand.OrderedSceneCard(photo.displayOrder(), card));
        }
        ScrollWeaveCommand.RoomInput room = new ScrollWeaveCommand.RoomInput(roomId, roomName);
        Map<String, WovenParagraph> woven = withEntries.isEmpty() ? Map.of()
                : weaver.weave(new ScrollWeaveCommand(room, List.copyOf(cards.values()), sourceEntries)).paragraphs().stream()
                        .collect(Collectors.toMap(WovenParagraph::photoId, Function.identity(), (first, duplicate) -> first));

        Map<String, Woven> result = new LinkedHashMap<>();
        for (PhotoPlan photo : photoPlans) {
            String photoId = photo.photo().getId();
            if (photo.entries().isEmpty()) {
                result.put(photoId, new Woven(roomMembers.get(photo.photo().getMemberId()).getNickname() + "님이 올린 사진이에요.", List.of(), true));
                continue;
            }
            Optional<WovenParagraph> valid = Optional.ofNullable(woven.get(photoId)).filter(p -> isValid(photo, p));
            for (int retry = 0; valid.isEmpty() && retry < MAX_PARAGRAPH_RETRIES; retry++) {
                valid = weaver.weave(new ScrollWeaveCommand(room, List.of(cards.get(photoId)), sourceEntries)).paragraphs().stream()
                        .filter(p -> photoId.equals(p.photoId())).findFirst().filter(p -> isValid(photo, p));
            }
            result.put(photoId, valid.map(p -> new Woven(p.body(), p.citations(), true)).orElseGet(() -> {
                log.warn("Paragraph for photo {} failed citation validation; using fallback", photoId);
                return fallback(photo, roomMembers);
            }));
        }
        return result;
    }

    /** 호출한 쪽 트랜잭션 안에서 다음 version의 두루마리를 저장한다. */
    public Scroll save(Plan plan, Map<String, Woven> woven) {
        int version = scrolls.findTopByRoomIdOrderByVersionDesc(plan.roomId()).map(s -> s.getVersion() + 1).orElse(1);
        Scroll scroll = scrolls.save(Scroll.create(plan.roomId(), version,
                plan.photos().stream().map(p -> woven.get(p.photo().getId()).body()).collect(Collectors.joining("\n\n"))));
        for (PhotoPlan photo : plan.photos()) {
            Woven paragraph = woven.get(photo.photo().getId());
            Paragraph saved = paragraphs.save(Paragraph.create(plan.roomId(), scroll.getId(), photo.photo().getId(), photo.displayOrder(), paragraph.body()));
            saveSources(saved.getId(), paragraph.citations());
            versions.save(ParagraphVersion.of(saved.getId(), saved.getVersion(), saved.getBody(), sourcesSummary(paragraph.citations()), "INITIAL"));
        }
        return scroll;
    }

    /** 문단 인용 출처를 통째로 바꾼다. */
    public void saveSources(String paragraphId, List<Citation> citations) {
        sources.deleteAll(sources.findAllByParagraphIdInOrderBySortOrderAsc(List.of(paragraphId)));
        for (int order = 0; order < citations.size(); order++) {
            Citation citation = citations.get(order);
            sources.save(ParagraphSource.of(paragraphId, citation.entryId(), citation.memberId(), citation.exactQuote(), order));
        }
    }

    public static String sourcesSummary(List<Citation> citations) {
        return citations.stream().map(c -> c.entryId() + ":" + c.memberId()).collect(Collectors.joining(","));
    }

    /** 명세 8장 결과 검증: 인용 원문이 실제로 있고, 같은 사진이고, 작성자가 맞고, 인용이 원문의 부분 문자열인가. */
    static boolean isValid(PhotoPlan photo, WovenParagraph paragraph) {
        if (paragraph.body() == null || paragraph.body().isBlank() || paragraph.citations() == null || paragraph.citations().isEmpty()) return false;
        Map<String, MemoryEntry> byId = photo.entries().stream().collect(Collectors.toMap(MemoryEntry::getId, Function.identity()));
        return paragraph.citations().stream().allMatch(c -> {
            MemoryEntry entry = byId.get(c.entryId());
            return entry != null && entry.getMemberId().equals(c.memberId())
                    && c.exactQuote() != null && !c.exactQuote().isBlank() && entry.getTranscript().contains(c.exactQuote());
        });
    }

    /** AI 출력이 끝내 검증을 통과하지 못하면 원문을 그대로 나열한다. */
    private Woven fallback(PhotoPlan photo, Map<String, Member> roomMembers) {
        String body = photo.entries().stream()
                .map(e -> roomMembers.get(e.getMemberId()).getNickname() + "은(는) ‘" + e.getTranscript() + "’라고 기억했다.")
                .collect(Collectors.joining(" "));
        List<Citation> citations = photo.entries().stream().map(e -> new Citation(e.getId(), e.getMemberId(), e.getTranscript())).toList();
        return new Woven(body, citations, false);
    }
}
