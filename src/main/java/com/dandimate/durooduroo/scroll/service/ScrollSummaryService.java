/**
 * 두루마리 한 줄 요약을 만든다. 엮기·문단 변경·사진 삭제 뒤 SCROLL_SUMMARY 작업이 돈다.
 * AI 호출은 트랜잭션 밖에서 한다. 요약이 실패해도 두루마리에는 영향이 없고 지도에는 이전 요약이 남는다.
 */
package com.dandimate.durooduroo.scroll.service;

import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.summary.ScrollSummaryAiService;
import com.dandimate.durooduroo.ai.summary.ScrollSummaryCommand;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScrollSummaryService {
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.memory.repository.MemoryEntryRepository entries;
    private final AiJobRepository jobs;
    private final RoomRepository rooms;
    private final ScrollRepository scrolls;
    private final ParagraphRepository paragraphs;
    private final PhotoRepository photos;
    private final ScrollSummaryAiService ai;
    private final TransactionTemplate tx;

    public ScrollSummaryService(AiJobRepository jobs, RoomRepository rooms, ScrollRepository scrolls, ParagraphRepository paragraphs,
                                PhotoRepository photos, ScrollSummaryAiService ai, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.rooms = rooms;
        this.scrolls = scrolls;
        this.paragraphs = paragraphs;
        this.photos = photos;
        this.ai = ai;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 이미 대기 중인 작업은 실행될 때 최신 문단을 읽으므로 새로 만들지 않는다. */
    @Transactional
    public void enqueue(String roomId) {
        rooms.findById(roomId).ifPresent(room->room.summarizeScroll(null));
        if (jobs.findFirstByRoomIdAndTypeAndStatusInOrderByCreatedAtDesc(roomId, AiJob.SCROLL_SUMMARY, List.of("QUEUED")).isEmpty()) {
            jobs.save(AiJob.queued(roomId, AiJob.SCROLL_SUMMARY));
        }
    }

    public void execute(String jobId) {
        String roomId = tx.execute(status -> jobs.findById(jobId).orElseThrow().getRoomId());
        ScrollSummaryCommand command = tx.execute(status -> command(roomId));
        String summary = command == null || command.paragraphs().isEmpty() ? null : valid(ai.summarize(command));
        tx.executeWithoutResult(status -> {
            if(!java.util.Objects.equals(command,command(roomId))) {
                jobs.findById(jobId).orElseThrow().succeed();
                enqueue(roomId);
                return;
            }
            rooms.findById(roomId).ifPresent(room -> room.summarizeScroll(summary));
            jobs.findById(jobId).orElseThrow().succeed();
        });
    }

    /** 현재 두루마리에서 사진이 남아 있는 문단만 쓴다. 지운 사진의 내용이 요약에 남지 않게 한다. */
    private ScrollSummaryCommand command(String roomId) {
        var room = rooms.findById(roomId).orElse(null);
        var scroll = scrolls.findTopByRoomIdOrderByVersionDesc(roomId).orElse(null);
        if (room == null) return null;
        if (scroll == null) {
            var published=photos.findAllByRoomId(roomId).stream().filter(p->p.isReady()&&p.isPublished()).sorted(java.util.Comparator.comparing(Photo::getId)).toList();
            var inputs=published.stream().map(p-> {
                // Room-level summary is shared: only publicly visible first entries, never locked addons.
                String text=entries.findAllByPhotoId(p.getId()).stream()
                  .filter(e->e.getKind()==com.dandimate.durooduroo.memory.domain.EntryKind.FIRST)
                  .map(e->e.getTranscript()).collect(Collectors.joining(" "));
                return new ScrollSummaryCommand.ParagraphInput(p.getPlaceName(),text);
            }).filter(p->p.body()!=null&&!p.body().isBlank()).toList();
            return new ScrollSummaryCommand(new ScrollSummaryCommand.RoomInput(room.getTitle(),room.getMeetingDate()==null?null:room.getMeetingDate().toString()),inputs);
        }
        Map<String, Photo> photoById = photos.findAllByRoomId(roomId).stream().collect(Collectors.toMap(Photo::getId, Function.identity()));
        var inputs = paragraphs.findAllByScrollIdOrderByDisplayOrderAsc(scroll.getId()).stream()
                .filter(paragraph -> photoById.containsKey(paragraph.getPhotoId()))
                .map(paragraph -> new ScrollSummaryCommand.ParagraphInput(photoById.get(paragraph.getPhotoId()).getPlaceName(), paragraph.getBody()))
                .toList();
        String meetingDate = room.getMeetingDate() == null ? null : room.getMeetingDate().toString();
        return new ScrollSummaryCommand(new ScrollSummaryCommand.RoomInput(room.getTitle(), meetingDate), inputs);
    }

    private String valid(String summary) {
        String text = summary == null ? "" : summary.strip();
        if (text.isEmpty() || text.length() > ScrollSummaryAiService.MAX_LENGTH || text.contains("\n")) {
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, true, "요약 형식이 맞지 않습니다.");
        }
        return text;
    }
}
