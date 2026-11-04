package com.dandimate.durooduroo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.ai.askback.AskbackCommand;
import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.dandimate.durooduroo.ai.openai.OpenAiResponsesClient;
import com.dandimate.durooduroo.ai.scene.SceneCardAiService;
import com.dandimate.durooduroo.ai.scene.SceneCardCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveCommand;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 명세 6장 품질 평가(LLM 채점). 형식만 보는 AiEvalTest와 달리, 다른 모델 호출이 정해진 기준으로 실제 품질을 채점한다.
 * 실제 OpenAI가 필요해 AI_PROVIDER=openai일 때만 돈다(기본 CI는 건너뜀). 결과는 build/reports/ai-judge/report.md.
 * 기준: 되묻기는 "답할 가치가 있는 질문" 80% 이상, 문단은 지어낸 사실 없음(faithfulness 3점 이상)과 평균 overall 3.5 이상.
 *   AI_PROVIDER=openai OPENAI_API_KEY=... OPENAI_TEXT_MODEL=... ./gradlew test --tests '*AiJudgeEvalTest'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AI_PROVIDER", matches = "openai")
class AiJudgeEvalTest {

    private static final Path REPORT = Path.of("build/reports/ai-judge/report.md");

    @Autowired private AskbackAiService askbackAi;
    @Autowired private SceneCardAiService sceneAi;
    @Autowired private ScrollWeaveAiService weaveAi;
    @Autowired private OpenAiResponsesClient client;

    record AskbackJudgeInput(List<Map<String, String>> entries, String target, String question) {}

    record WeaveJudgeInput(List<Map<String, String>> entries, String paragraph) {}

    @Test
    void 되묻기_질문의_80퍼센트_이상이_답할_가치가_있고_문단은_지어낸_사실이_없다() throws IOException {
        StringBuilder report = new StringBuilder("# AI 품질 채점 (LLM judge)\n\n");

        // 되묻기
        report.append("## 되묻기\n\n| 사례 | 질문 | 답할 가치 | 구체적 | 근거 | 자연스러움 | 이유 |\n|---|---|---|---|---|---|---|\n");
        int asked = 0, worth = 0, specific = 0;
        for (var named : AiEvalTest.askbackCases().toList()) {
            JsonNode c = named.getPayload();
            Map<String, String> names = AiEvalTest.names(c.get("members"));
            List<EntryInput> entries = AiEvalTest.entries(c.get("entries"), names);
            var previous = StreamSupport.stream(c.get("previous").spliterator(), false)
                    .map(p -> new AskbackCommand.PreviousAskback(p.get(0).asText(), p.get(1).asText(), p.get(2).asText(), p.get(3).asText())).toList();
            var result = askbackAi.analyze(new AskbackCommand(new PhotoInput("photo", c.get("uploader").asText(), null), AiEvalTest.members(c.get("members")),
                    entries, previous, new AskbackCommand.Limits(AiEvalTest.strings(c.get("eligible")), AiEvalTest.strings(c.get("allowed")))));
            if (!result.shouldAsk()) continue;
            asked++;
            JsonNode judged = client.call("judge-askback-v2", "askback_judgement", ASKBACK_SCHEMA,
                    new AskbackJudgeInput(plain(entries), names.get(result.targetMemberId()), result.question())).output();
            if (judged.path("worthAnswering").asBoolean()) worth++;
            if (judged.path("specific").asBoolean()) specific++;
            report.append("| ").append(named.getName()).append(" | ").append(cell(result.question())).append(" | ")
                    .append(mark(judged, "worthAnswering")).append(" | ").append(mark(judged, "specific")).append(" | ")
                    .append(mark(judged, "grounded")).append(" | ").append(mark(judged, "natural")).append(" | ")
                    .append(cell(judged.path("reason").asText())).append(" |\n");
        }
        double worthRate = asked == 0 ? 1 : (double) worth / asked;
        report.append(String.format("%n질문 %d개 · 답할 가치 %d (%.0f%%) · 구체적 %d%n%n", asked, worth, worthRate * 100, specific));

        // 문단
        report.append("## 두루마리 문단\n\n| 사례 | 사실 | 말투 | 화자 | 읽힘 | 종합 | 문제점 | 문단 |\n|---|---|---|---|---|---|---|---|\n");
        List<Integer> overall = new ArrayList<>();
        List<String> invented = new ArrayList<>();
        for (var named : AiEvalTest.weaveCases().toList()) {
            JsonNode c = named.getPayload();
            Map<String, String> names = AiEvalTest.names(c.get("members"));
            Map<String, List<EntryInput>> entriesByPhoto = new LinkedHashMap<>();
            Map<String, ScrollWeaveCommand.SourceEntry> sources = new HashMap<>();
            List<ScrollWeaveCommand.OrderedSceneCard> cards = new ArrayList<>();
            int order = 0;
            for (JsonNode photo : c.get("photos")) {
                order++;
                List<EntryInput> entries = AiEvalTest.entries(photo.get("entries"), names);
                if (entries.isEmpty()) continue;
                String photoId = photo.get("id").asText();
                entriesByPhoto.put(photoId, entries);
                entries.forEach(e -> sources.put(e.id(), new ScrollWeaveCommand.SourceEntry(photoId, e.memberId(), e.nickname(), e.transcript())));
                cards.add(new ScrollWeaveCommand.OrderedSceneCard(order,
                        sceneAi.extract(new SceneCardCommand(new PhotoInput(photoId, photo.get("uploader").asText(), null), entries))));
            }
            if (cards.isEmpty()) continue;
            var woven = weaveAi.weave(new ScrollWeaveCommand(new ScrollWeaveCommand.RoomInput("room", "경주"), cards, sources));
            for (var paragraph : woven.paragraphs()) {
                JsonNode judged = client.call("judge-weave-v1", "weave_judgement", WEAVE_SCHEMA,
                        new WeaveJudgeInput(plain(entriesByPhoto.getOrDefault(paragraph.photoId(), List.of())), paragraph.body())).output();
                overall.add(judged.path("overall").asInt());
                if (judged.path("faithfulness").asInt() < 3) invented.add(named.getName());
                report.append("| ").append(named.getName()).append(" | ").append(judged.path("faithfulness").asInt()).append(" | ")
                        .append(judged.path("voice").asInt()).append(" | ").append(judged.path("attribution").asInt()).append(" | ")
                        .append(judged.path("readability").asInt()).append(" | ").append(judged.path("overall").asInt()).append(" | ")
                        .append(cell(String.join("; ", AiEvalTest.strings(judged.path("issues"))))).append(" | ").append(cell(paragraph.body())).append(" |\n");
            }
        }
        double averageOverall = overall.stream().mapToInt(Integer::intValue).average().orElse(5);
        report.append(String.format("%n문단 %d개 · 종합 평균 %.2f · 지어낸 사실 의심 %d건 %s%n", overall.size(), averageOverall, invented.size(), invented));

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        System.out.println("AI judge report: " + REPORT.toAbsolutePath());

        assertThat(worthRate).as("답할 가치가 있는 질문 비율 (기준 80%%), 리포트: %s", REPORT).isGreaterThanOrEqualTo(0.8);
        assertThat(invented).as("지어낸 사실이 의심되는 문단, 리포트: %s", REPORT).isEmpty();
        assertThat(averageOverall).as("문단 종합 평균 (기준 3.5), 리포트: %s", REPORT).isGreaterThanOrEqualTo(3.5);
    }

    private static List<Map<String, String>> plain(List<EntryInput> entries) {
        return entries.stream().map(e -> Map.of("nickname", e.nickname(), "transcript", e.transcript())).toList();
    }

    private static String mark(JsonNode node, String field) {
        return node.path(field).asBoolean() ? "✅" : "❌";
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static Map<String, Object> schema(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false, "properties", properties, "required", List.copyOf(properties.keySet()));
    }

    private static final Map<String, Object> BOOL = Map.of("type", "boolean");
    private static final Map<String, Object> SCORE = Map.of("type", "integer");
    private static final Map<String, Object> TEXT = Map.of("type", "string");

    private static final Map<String, Object> ASKBACK_SCHEMA = schema(ordered(
            "worthAnswering", BOOL, "specific", BOOL, "grounded", BOOL, "natural", BOOL, "reason", TEXT));
    private static final Map<String, Object> WEAVE_SCHEMA = schema(ordered(
            "faithfulness", SCORE, "voice", SCORE, "attribution", SCORE, "readability", SCORE, "overall", SCORE,
            "issues", Map.of("type", "array", "items", TEXT)));

    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }
}
