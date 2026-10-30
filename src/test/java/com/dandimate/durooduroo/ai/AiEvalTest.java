package com.dandimate.durooduroo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.ai.askback.AskbackCommand;
import com.dandimate.durooduroo.ai.common.AiInputs.Citation;
import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.dandimate.durooduroo.ai.reply.ParagraphReweaveAiService;
import com.dandimate.durooduroo.ai.reply.ReplyAiService;
import com.dandimate.durooduroo.ai.reply.ReplyClassificationCommand;
import com.dandimate.durooduroo.ai.reply.ReweaveCommand;
import com.dandimate.durooduroo.ai.scene.SceneCardAiService;
import com.dandimate.durooduroo.ai.scene.SceneCardCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 명세 11장 AI 회귀 평가. src/test/resources/ai-eval/*.jsonl의 고정 입력을 현재 설정된 AI 구현(app.ai.provider)에 넣고
 * 자동 판정 기준을 확인한다: JSON 계약(schemaVersion 등), 존재하지 않는 member/entry ID 0건, 인용 정확도 100%,
 * 되묻기 60자 이하·허용 목록 밖 대상 0건, 재엮기에서 기존 인용 삭제 0건, 단순 반응·욕설 분류.
 * 프롬프트·모델을 바꾸면 이 테스트를 그 구현으로 다시 돌려 결과를 PR에 첨부한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AiEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private AskbackAiService askbackAi;
    @Autowired private SceneCardAiService sceneAi;
    @Autowired private ScrollWeaveAiService weaveAi;
    @Autowired private ReplyAiService replyAi;
    @Autowired private ParagraphReweaveAiService reweaveAi;

    static Stream<Named<JsonNode>> askbackCases() {
        return fixture("askback.jsonl");
    }

    static Stream<Named<JsonNode>> weaveCases() {
        return fixture("weave.jsonl");
    }

    static Stream<Named<JsonNode>> replyCases() {
        return fixture("reply.jsonl");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("askbackCases")
    void 되묻기는_허용_목록_안의_대상에게_근거_있는_60자_이하_질문만_한다(JsonNode c) {
        List<EntryInput> entries = entries(c.get("entries"), names(c.get("members")));
        List<String> eligible = strings(c.get("eligible"));
        List<String> allowed = strings(c.get("allowed"));
        var previous = StreamSupport.stream(c.get("previous").spliterator(), false)
                .map(p -> new AskbackCommand.PreviousAskback(p.get(0).asText(), p.get(1).asText(), p.get(2).asText(), p.get(3).asText())).toList();
        var command = new AskbackCommand(new PhotoInput("photo", c.get("uploader").asText(), null), members(c.get("members")), entries, previous,
                new AskbackCommand.Limits(eligible, allowed));

        var result = askbackAi.analyze(command);

        assertThat(result.schemaVersion()).isNotBlank();
        assertThat(result.promptVersion()).isNotBlank();
        assertThat(result.model()).isNotBlank();
        if (eligible.isEmpty() || allowed.isEmpty()) assertThat(result.shouldAsk()).isFalse();
        if (!result.shouldAsk()) return;
        assertThat(eligible).contains(result.targetMemberId());
        assertThat(allowed).contains(result.gapType());
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.question()).isNotBlank().hasSizeLessThanOrEqualTo(60).doesNotContain("왜 기억 못");
        assertThat(entries.stream().map(EntryInput::id).toList()).containsAll(result.evidenceEntryIds());
        assertThat(result.evidenceEntryIds()).isNotEmpty();
        previous.forEach(p -> assertThat(result.question()).isNotEqualTo(p.question()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("weaveCases")
    void 문단은_사진마다_하나고_모든_인용이_실제_원문의_부분_문자열이다(JsonNode c) {
        Map<String, String> names = names(c.get("members"));
        Map<String, ScrollWeaveCommand.SourceEntry> sources = new HashMap<>();
        Map<String, EntryInput> entriesById = new HashMap<>();
        List<ScrollWeaveCommand.OrderedSceneCard> cards = new ArrayList<>();
        int order = 0;
        List<String> photosWithEntries = new ArrayList<>();
        for (JsonNode photo : c.get("photos")) {
            order++;
            List<EntryInput> entries = entries(photo.get("entries"), names);
            if (entries.isEmpty()) continue;
            String photoId = photo.get("id").asText();
            photosWithEntries.add(photoId);
            entries.forEach(e -> {
                entriesById.put(e.id(), e);
                sources.put(e.id(), new ScrollWeaveCommand.SourceEntry(photoId, e.memberId(), e.nickname(), e.transcript()));
            });
            var card = sceneAi.extract(new SceneCardCommand(new PhotoInput(photoId, photo.get("uploader").asText(), null), entries));
            assertThat(card.schemaVersion()).isNotBlank();
            assertThat(names.keySet()).containsAll(card.whoMemberIds());
            card.voices().forEach(v -> assertThat(entriesById.get(v.entryId()).transcript()).contains(v.exactQuote()));
            cards.add(new ScrollWeaveCommand.OrderedSceneCard(order, card));
        }
        if (cards.isEmpty()) return;

        var result = weaveAi.weave(new ScrollWeaveCommand(new ScrollWeaveCommand.RoomInput("room", "경주"), cards, sources));

        assertThat(result.schemaVersion()).isNotBlank();
        assertThat(result.paragraphs()).extracting(p -> p.photoId()).containsExactlyInAnyOrderElementsOf(photosWithEntries);
        result.paragraphs().forEach(p -> {
            assertThat(p.citations()).isNotEmpty();
            for (Citation citation : p.citations()) {
                EntryInput entry = entriesById.get(citation.entryId());
                assertThat(entry).as("존재하지 않는 entry %s", citation.entryId()).isNotNull();
                assertThat(sources.get(citation.entryId()).photoId()).isEqualTo(p.photoId());
                assertThat(citation.memberId()).isEqualTo(entry.memberId());
                assertThat(citation.exactQuote()).isNotBlank();
                assertThat(entry.transcript()).contains(citation.exactQuote());
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("replyCases")
    void 답가는_반응과_정정을_구분하고_재엮기는_기존_인용을_지우지_않는다(JsonNode c) {
        String transcript = c.get("transcript").asText();
        String base = "막차를 놓친 친구들은 역까지 뛰었다. 서연은 ‘막차 놓쳐서 뛰었잖아’라고 기억했고 지수는 ‘다리가 풀리는 줄 알았어’라고 했다.";
        List<Citation> citations = List.of(new Citation("e1", "m1", "막차 놓쳐서 뛰었잖아"), new Citation("e2", "m2", "다리가 풀리는 줄 알았어"));

        var classified = replyAi.classify(new ReplyClassificationCommand(base, "r1", "민호", transcript));

        assertThat(classified.schemaVersion()).isNotBlank();
        assertThat(classified.classification()).isEqualTo(c.get("expected").asText());
        if (!List.of("NEW_FACT", "CORRECTION").contains(classified.classification())) return;
        var woven = reweaveAi.reweave(new ReweaveCommand("p1", "photo", 1, base, citations,
                new ReweaveCommand.ReplyInput("r1", "m3", "민호", transcript), classified.classification())).paragraph();
        assertThat(woven.citations()).extracting(Citation::entryId).contains("e1", "e2", "r1");
        assertThat(woven.citations()).filteredOn(cit -> cit.entryId().equals("r1")).allSatisfy(cit -> assertThat(transcript).contains(cit.exactQuote()));
        assertThat(woven.body().length()).isLessThanOrEqualTo((int) (base.length() * 1.5));
    }

    private static Stream<Named<JsonNode>> fixture(String name) {
        try (var in = AiEvalTest.class.getResourceAsStream("/ai-eval/" + name);
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<Named<JsonNode>> cases = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank()) continue;
                JsonNode node = JSON.readTree(line);
                cases.add(Named.of(node.get("id").asText() + " (" + node.get("category").asText() + ")", node));
            }
            return cases.stream();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> names(JsonNode members) {
        Map<String, String> names = new HashMap<>();
        members.forEach(m -> names.put(m.get(0).asText(), m.get(1).asText()));
        return names;
    }

    private static List<MemberInput> members(JsonNode members) {
        return StreamSupport.stream(members.spliterator(), false).map(m -> new MemberInput(m.get(0).asText(), m.get(1).asText(), List.of())).toList();
    }

    private static List<EntryInput> entries(JsonNode entries, Map<String, String> names) {
        return StreamSupport.stream(entries.spliterator(), false)
                .map(e -> new EntryInput(e.get(0).asText(), e.get(1).asText(), names.getOrDefault(e.get(1).asText(), e.get(1).asText()), e.get(2).asText(), e.get(3).asText()))
                .toList();
    }

    private static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
