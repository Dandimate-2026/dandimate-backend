package com.dandimate.durooduroo.scroll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.Safety;
import com.dandimate.durooduroo.ai.scene.QuoteSceneCardAiService;
import com.dandimate.durooduroo.ai.weave.QuoteScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveResult;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.photo.domain.Photo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScrollGenerationServiceTest {

    private final Member host = Member.host("room-1", "서연");
    private final Photo photo = publishedPhoto();
    private final MemoryEntry entry = MemoryEntry.confirm("room-1", photo.getId(), host.getId(), EntryKind.FIRST, "TEXT", null, null, "막차 놓쳐서 뛰었잖아");
    private final ScrollGenerationService.Plan plan = new ScrollGenerationService.Plan("room-1", "경주",
            List.of(new ScrollGenerationService.PhotoPlan(photo, 1, List.of(entry))), Map.of(host.getId(), host));

    @Test
    void 인용이_원문에_없으면_두_번_다시_엮고_그래도_틀리면_원문_나열로_대체한다() {
        ScrollWeaveAiService inventing = mock(ScrollWeaveAiService.class);
        when(inventing.weave(any())).thenReturn(new ScrollWeaveResult("1.0", List.of(new ScrollWeaveResult.WovenParagraph(photo.getId(),
                "서연은 '택시를 탔다'고 했다.", List.of(new Citation(entry.getId(), host.getId(), "택시를 탔다")), Safety.allow())), "m", "p", null));
        var service = new ScrollGenerationService(null, null, null, null, null, null, null, new QuoteSceneCardAiService(), inventing);

        var woven = service.weave(plan).get(photo.getId());

        verify(inventing, times(3)).weave(any());
        assertThat(woven.aiGenerated()).isFalse();
        assertThat(woven.citations()).extracting(Citation::exactQuote).containsExactly("막차 놓쳐서 뛰었잖아");
        assertThat(woven.body()).contains("막차 놓쳐서 뛰었잖아");
    }

    @Test
    void 정확한_인용은_그대로_쓴다() {
        var service = new ScrollGenerationService(null, null, null, null, null, null, null, new QuoteSceneCardAiService(), new QuoteScrollWeaveAiService());

        var woven = service.weave(plan).get(photo.getId());

        assertThat(woven.aiGenerated()).isTrue();
        assertThat(woven.citations()).singleElement().satisfies(c -> {
            assertThat(c.entryId()).isEqualTo(entry.getId());
            assertThat(entry.getTranscript()).contains(c.exactQuote());
        });
    }

    @Test
    void 다른_사람이나_다른_사진의_원문을_인용하면_무효다() {
        var wrongAuthor = new ScrollWeaveResult.WovenParagraph(photo.getId(), "본문", List.of(new Citation(entry.getId(), "someone-else", "막차")), Safety.allow());
        var unknownEntry = new ScrollWeaveResult.WovenParagraph(photo.getId(), "본문", List.of(new Citation("other-entry", host.getId(), "막차")), Safety.allow());
        var noCitation = new ScrollWeaveResult.WovenParagraph(photo.getId(), "본문", List.of(), Safety.allow());
        var photoPlan = plan.photos().get(0);

        assertThat(ScrollGenerationService.isValid(photoPlan, wrongAuthor)).isFalse();
        assertThat(ScrollGenerationService.isValid(photoPlan, unknownEntry)).isFalse();
        assertThat(ScrollGenerationService.isValid(photoPlan, noCitation)).isFalse();
    }

    private static Photo publishedPhoto() {
        Photo photo = Photo.pending("photo-1", "room-1", "member-1", "room-1/photos/photo-1.png", "uploads/room-1/photos/photo-1.png", "image/png", 1);
        photo.complete(null);
        photo.publish();
        return photo;
    }
}
