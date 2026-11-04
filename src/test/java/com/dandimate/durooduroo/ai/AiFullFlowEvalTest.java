package com.dandimate.durooduroo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.job.service.JobWorker;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 OpenAI로 사진 → 한마디·덧붙임 → 되묻기 → 엮기 → 답가 → 재엮기를 API 순서대로 돌린다.
 * AI_PROVIDER=openai일 때만 돈다(기본 CI는 건너뜀). 결과는 build/reports/ai-flow/report.md.
 */
@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-full-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads", "app.jobs.retry-delay-ms=0",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AI_PROVIDER", matches = "openai")
class AiFullFlowEvalTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;

    @Test
    void 사진부터_재엮기까지_실제_AI로_끝까지_돈다() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, "{\"name\":\"경주 여행\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String member = JsonPath.read(body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"민호\"," + CONSENT + "}")), "$.data.memberAccessToken");

        String photo = uploadPhoto(roomId, host);
        entry(photo, host, "FIRST", "황리단길에서 비 쏟아져서 우산 하나로 셋이 걸었어. 저녁은 칼국수 먹었고 민호가 제일 많이 젖었지.");
        entry(photo, member, "ADDON", "나는 저녁을 비빔밥 먹은 걸로 기억해. 서연이가 먼저 뛰자고 해서 다 같이 뛰었잖아.");
        drain();

        String hostAskbacks = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/askbacks/me").header("Authorization", "Bearer " + host)).andExpect(status().isOk()));
        String memberAskbacks = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/askbacks/me").header("Authorization", "Bearer " + member)).andExpect(status().isOk()));
        List<String> questions = new java.util.ArrayList<>(JsonPath.<List<String>>read(hostAskbacks, "$.data.askbacks[*].question"));
        questions.addAll(JsonPath.<List<String>>read(memberAskbacks, "$.data.askbacks[*].question"));

        json(post("/api/v1/rooms/" + roomId + "/close"), host, "{\"mode\":\"EARLY\"}").andExpect(status().isAccepted());
        drain();
        String scroll = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/scroll").header("Authorization", "Bearer " + host)).andExpect(status().isOk()));
        String paragraphId = JsonPath.read(scroll, "$.data.paragraphs[0].id");
        String firstBody = JsonPath.read(scroll, "$.data.paragraphs[0].body");
        List<String> quotes = JsonPath.read(scroll, "$.data.paragraphs[0].sources[*].quote");

        json(post("/api/v1/paragraphs/" + paragraphId + "/replies"), member, "{\"inputMode\":\"TEXT\",\"transcript\":\"그때 편의점에서 우비를 샀는데 바람에 바로 찢어졌어\"}")
                .andExpect(status().isAccepted());
        drain();
        String reweaved = body(mockMvc.perform(get("/api/v1/rooms/" + roomId + "/scroll").header("Authorization", "Bearer " + host)).andExpect(status().isOk()));
        String secondBody = JsonPath.read(reweaved, "$.data.paragraphs[0].body");
        int version = JsonPath.read(reweaved, "$.data.paragraphs[0].version");

        Path report = Path.of("build/reports/ai-flow/report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "# 전체 흐름\n\n## 되묻기\n" + questions + "\n\n## 첫 문단\n" + firstBody + "\n\n인용: " + quotes
                + "\n\n## 답가 반영 후 (v" + version + ")\n" + secondBody + "\n");

        assertThat(questions).as("엇갈린 기억이 있는 사진에는 되묻기가 나온다").isNotEmpty();
        assertThat(firstBody).isNotBlank();
        assertThat(quotes).as("인용은 본문에 있다").isNotEmpty().allSatisfy(q -> assertThat(firstBody).contains(q));
        assertThat(version).as("새 사실이 담긴 답가는 문단을 다시 엮는다").isGreaterThan(1);
        assertThat(secondBody).isNotEqualTo(firstBody).contains("우비");
    }

    private String uploadPhoto(String roomId, String token) throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(90, 110, 130));
        graphics.fillRect(0, 0, 640, 480);
        graphics.setColor(new Color(30, 40, 80));
        graphics.fillArc(170, 120, 300, 200, 0, 180);
        graphics.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);

        String requested = body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":" + jpeg.size() + ",\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/jpeg").content(jpeg.toByteArray())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return photoId;
    }

    private void entry(String photoId, String token, String kind, String transcript) throws Exception {
        json(post("/api/v1/photos/" + photoId + "/entries"), token,
                "{\"kind\":\"" + kind + "\",\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}").andExpect(status().isCreated());
    }

    private void drain() {
        while (worker.runNext()) {
            // 대기 중인 작업을 모두 실행한다.
        }
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
