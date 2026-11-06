package com.dandimate.durooduroo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.memory.audio.VoiceSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 계약서(static/openapi/public-api.yaml)의 모든 operation을 실제로 호출해
 * 1) 응답 HTTP 상태가 그 operation에 선언된 응답 코드인지, 2) 본문이 그 응답의 스키마를 따르는지(204는 본문 없음) 검사한다.
 * 계약서에 선언된 오류 응답(400·403·404·409·422)도 일부러 일으켜 같은 방식으로 검사하고, 마지막에 모든 operation을 호출했는지 확인한다.
 */
@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {"storage.local-root=build/test-uploads",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class ContractCoverageTest {

    private static final String CONTRACT_PATH = "static/openapi/public-api.yaml";
    private static final String CONSENT = "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JobWorker worker;

    private final JsonNode contract = readContract();
    private final Set<String> called = new TreeSet<>();
    private final List<String> problems = new ArrayList<>();

    @Test
    void 모든_operation의_상태_코드와_응답_본문이_계약서와_맞는다() throws Exception {
        check("GET", "/plans", mockMvc.perform(get("/api/v1/plans")), 200);
        // 방·초대
        check("POST", "/rooms", json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\",\"consent\":{\"version\":\"v1\",\"terms\":false,\"mediaProcessing\":true,\"externalAiProcessing\":true}}"), 400);
        String room = check("POST", "/rooms", json(post("/api/v1/rooms"), null, "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}"), 201);
        String roomId = JsonPath.read(room, "$.data.room.id");
        String a = JsonPath.read(room, "$.data.memberAccessToken");
        String invite = JsonPath.read(room, "$.data.inviteToken");
        check("GET", "/invites/{inviteToken}", mockMvc.perform(get("/api/v1/invites/" + invite)), 200);
        check("GET", "/invites/{inviteToken}", mockMvc.perform(get("/api/v1/invites/no-such-invite-token-0000000")), 404);
        String joined = check("POST", "/invites/{inviteToken}/join", json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}"), 201);
        String b = JsonPath.read(joined, "$.data.memberAccessToken");
        String taken = check("POST", "/invites/{inviteToken}/join", json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}"), 409);
        assertThat(JsonPath.<String>read(taken, "$.error.code")).isEqualTo("NICKNAME_TAKEN");
        assertThat(JsonPath.<List<String>>read(taken, "$.error.details.suggestions")).as("명세 1장: 숫자를 붙인 추천 이름").isNotEmpty().allSatisfy(s -> assertThat(s).startsWith("지수"));

        String other = JsonPath.read(body(json(post("/api/v1/rooms"), null, "{\"name\":\"부산\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"민호\"," + CONSENT + "}")), "$.data.memberAccessToken");
        check("GET", "/rooms/{roomId}", authed(get("/api/v1/rooms/" + roomId), b), 200);
        check("GET", "/rooms/{roomId}", authed(get("/api/v1/rooms/" + roomId), other), 403);
        check("PATCH", "/rooms/{roomId}", json(patch("/api/v1/rooms/" + roomId), b, "{\"name\":\"경주 여행\"}"), 403);
        check("PATCH", "/rooms/{roomId}", json(patch("/api/v1/rooms/" + roomId), a, "{\"name\":\"경주 여행\"}"), 200);
        check("POST", "/rooms/{roomId}/invite-token/rotate", authed(post("/api/v1/rooms/" + roomId + "/invite-token/rotate"), a), 200);

        // 사진·원문
        String uploads = check("POST", "/rooms/{roomId}/photos/upload-requests", json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), a, files(3)), 201);
        check("POST", "/rooms/{roomId}/photos/upload-requests", json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), a, files(1)), 422);
        String photoA = putAndComplete(uploads, a);
        check("POST", "/photos/{photoId}/transcriptions", mockMvc.perform(multipart("/api/v1/photos/" + photoA + "/transcriptions")
                .file(new MockMultipartFile("file", "v.wav", "audio/wav", VoiceSamples.wav(6))).param("kind", "FIRST").header("Authorization", "Bearer " + a)), 201);
        check("POST", "/photos/{photoId}/entries", json(post("/api/v1/photos/" + photoA + "/entries"), a, entry("FIRST", "다들 갑자기 웃은 사진")), 201);
        check("POST", "/photos/{photoId}/entries", json(post("/api/v1/photos/" + photoA + "/entries"), a, entry("FIRST", "두 번째 첫 한마디")), 422);
        drain();

        check("GET", "/rooms/{roomId}/circulation", authed(get("/api/v1/rooms/" + roomId + "/circulation"), b), 200);
        check("PUT", "/photos/{photoId}/passes/me", authed(put("/api/v1/photos/" + photoA + "/passes/me"), b), 200);
        check("DELETE", "/photos/{photoId}/passes/me", authed(delete("/api/v1/photos/" + photoA + "/passes/me"), b), 204);

        String photoB = uploadOne(roomId, b);
        check("POST", "/photos/{photoId}/first-entry/skip", authed(post("/api/v1/photos/" + photoB + "/first-entry/skip"), b), 200);
        String addon = check("POST", "/photos/{photoId}/entries", json(post("/api/v1/photos/" + photoB + "/entries"), a, entry("ADDON", "나도 그날 기억나")), 201);
        String addonId = JsonPath.read(addon, "$.data.entry.id");
        check("PATCH", "/entries/{entryId}", json(patch("/api/v1/entries/" + addonId), a, "{\"transcript\":\"나도 그날 똑똑히 기억나\"}"), 200);

        // 방장이 다른 멤버의 사진을 패스하면 방장에게도 질문이 온다.
        String questionPhoto = uploadOne(roomId, b);
        json(post("/api/v1/photos/" + questionPhoto + "/entries"), b, entry("FIRST", "함께 웃은 순간"));
        authed(put("/api/v1/photos/" + questionPhoto + "/passes/me"), a);
        drain();
        // 되묻기
        String askbacks = check("GET", "/rooms/{roomId}/askbacks/me", authed(get("/api/v1/rooms/" + roomId + "/askbacks/me"), a), 200);
        String askbackId = JsonPath.read(askbacks, "$.data.askbacks[0].id");
        check("GET", "/askbacks/{askbackId}", authed(get("/api/v1/askbacks/" + askbackId), a), 200);
        check("GET", "/rooms/{roomId}/askbacks/next", authed(get("/api/v1/rooms/" + roomId + "/askbacks/next"), a), 200);
        check("GET", "/askbacks/{askbackId}", authed(get("/api/v1/askbacks/" + askbackId), b), 404);
        check("POST", "/askbacks/{askbackId}/transcriptions", mockMvc.perform(multipart("/api/v1/askbacks/" + askbackId + "/transcriptions")
                .file(new MockMultipartFile("file", "v.wav", "audio/wav", VoiceSamples.wav(3))).header("Authorization", "Bearer " + a)), 201);
        check("POST", "/askbacks/{askbackId}/postpone", authed(post("/api/v1/askbacks/" + askbackId + "/postpone"), a), 200);
        check("POST", "/askbacks/{askbackId}/answer", json(post("/api/v1/askbacks/" + askbackId + "/answer"), a, "{\"inputMode\":\"TEXT\",\"transcript\":\"지수가 넘어졌거든\"}"), 201);
        check("POST", "/askbacks/{askbackId}/answer", json(post("/api/v1/askbacks/" + askbackId + "/answer"), a, "{\"inputMode\":\"TEXT\",\"transcript\":\"한 번 더\"}"), 409);
        check("POST", "/askbacks/{askbackId}/forgot", authed(post("/api/v1/askbacks/" + askbackId + "/forgot"), a), 409);
        check("POST", "/askbacks/{askbackId}/postpone", authed(post("/api/v1/askbacks/" + askbackId + "/postpone"), a), 409);
        check("POST", "/rooms/{roomId}/askbacks/run", authed(post("/api/v1/rooms/" + roomId + "/askbacks/run"), b), 403);
        check("POST", "/rooms/{roomId}/askbacks/run", authed(post("/api/v1/rooms/" + roomId + "/askbacks/run"), a), 202);
        drain();
        askForgotFlow(roomId, b);

        check("POST", "/askbacks/{askbackId}/pass", authed(post("/api/v1/askbacks/" + askbackId + "/pass"), b), 404);
        // 알림·SSE
        String notifications = check("GET", "/rooms/{roomId}/notifications", authed(get("/api/v1/rooms/" + roomId + "/notifications"), a), 200);
        check("POST", "/notifications/{notificationId}/read", authed(post("/api/v1/notifications/" + JsonPath.read(notifications, "$.data[0].id") + "/read"), a), 200);
        String token = check("POST", "/rooms/{roomId}/event-token", authed(post("/api/v1/rooms/" + roomId + "/event-token"), a), 201);
        String eventsUrl = JsonPath.read(token, "$.data.eventsUrl");
        check("GET", "/rooms/{roomId}/events", mockMvc.perform(get(eventsUrl.substring("http://localhost".length()))), 200);

        // 마감·두루마리·답가
        check("GET", "/rooms/{roomId}/scroll", authed(get("/api/v1/rooms/" + roomId + "/scroll"), b), 409);
        String collecting = body(json(post("/api/v1/rooms"), null, "{\"name\":\"빈방\",\"meetingDate\":\"2026-10-24\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String collectingRoom = JsonPath.read(collecting, "$.data.room.id");
        String collectingHost = JsonPath.read(collecting, "$.data.memberAccessToken");
        check("POST", "/rooms/{roomId}/close", json(post("/api/v1/rooms/" + collectingRoom + "/close"), collectingHost, "{\"mode\":\"DEMO\"}"), 409);
        String closed = check("POST", "/rooms/{roomId}/close", json(post("/api/v1/rooms/" + roomId + "/close"), a, "{\"mode\":\"DEMO\"}"), 202);
        drain();
        check("GET", "/jobs/{jobId}", authed(get("/api/v1/jobs/" + JsonPath.read(closed, "$.data.job.id")), b), 200);
        check("POST", "/rooms/{roomId}/weave-jobs", authed(post("/api/v1/rooms/" + roomId + "/weave-jobs"), a), 202);
        drain();
        String scroll = check("GET", "/rooms/{roomId}/scroll", authed(get("/api/v1/rooms/" + roomId + "/scroll"), b), 200);
        String paragraphId = JsonPath.read(scroll, "$.data.paragraphs[0].id");
        check("POST", "/paragraphs/{paragraphId}/transcriptions", mockMvc.perform(multipart("/api/v1/paragraphs/" + paragraphId + "/transcriptions")
                .file(new MockMultipartFile("file", "v.wav", "audio/wav", VoiceSamples.wav(3))).header("Authorization", "Bearer " + b)), 201);
        String reply = check("POST", "/paragraphs/{paragraphId}/replies", json(post("/api/v1/paragraphs/" + paragraphId + "/replies"), b, "{\"inputMode\":\"TEXT\",\"transcript\":\"그날 밤 치킨 시켰잖아\"}"), 202);
        drain();
        check("DELETE", "/replies/{replyId}", authed(delete("/api/v1/replies/" + JsonPath.read(reply, "$.data.reply.id")), b), 202);
        drain();
        check("PUT", "/paragraphs/{paragraphId}/fidelity-rating", json(put("/api/v1/paragraphs/" + paragraphId + "/fidelity-rating"), a, "{\"score\":4}"), 200);
        check("PUT", "/paragraphs/{paragraphId}/reactions/ah-matda", authed(put("/api/v1/paragraphs/" + paragraphId + "/reactions/ah-matda"), b), 200);
        check("DELETE", "/paragraphs/{paragraphId}/reactions/ah-matda", authed(delete("/api/v1/paragraphs/" + paragraphId + "/reactions/ah-matda"), b), 204);

        // 삭제
        check("DELETE", "/entries/{entryId}", authed(delete("/api/v1/entries/" + addonId), a), 204);
        check("GET", "/places/search", authed(get("/api/v1/places/search?query=황리단길"), b), 503);
        check("GET", "/places/search", authed(get("/api/v1/places/search").param("query", " "), b), 400);
        String location = "{\"placeName\":\"황리단길\",\"address\":\"경북 경주시 포석로\",\"latitude\":35.8,\"longitude\":129.2}";
        check("PUT", "/photos/{photoId}/location", json(put("/api/v1/photos/" + photoB + "/location"), a, location), 403);
        check("PUT", "/photos/{photoId}/location", json(put("/api/v1/photos/" + photoB + "/location"), b, "{\"placeName\":\"\"}"), 400);
        check("PUT", "/photos/{photoId}/location", json(put("/api/v1/photos/" + photoB + "/location"), b, location), 200);
        check("GET", "/rooms/{roomId}/scroll", authed(get("/api/v1/rooms/" + roomId + "/scroll"), b), 200);
        check("DELETE", "/photos/{photoId}/location", authed(delete("/api/v1/photos/" + photoB + "/location"), a), 403);
        check("DELETE", "/photos/{photoId}/location", authed(delete("/api/v1/photos/" + photoB + "/location"), b), 204);
        check("DELETE", "/photos/{photoId}", authed(delete("/api/v1/photos/" + photoB), a), 403);
        check("DELETE", "/photos/{photoId}", authed(delete("/api/v1/photos/" + photoB), b), 204);
        check("DELETE", "/rooms/{roomId}", authed(delete("/api/v1/rooms/" + roomId), b), 403);
        check("DELETE", "/rooms/{roomId}", authed(delete("/api/v1/rooms/" + roomId), a), 204);

        Set<String> all = allOperations();
        all.removeAll(called);
        // 로그인·세션 API는 이 테스트(참가자 토큰 시나리오, LegacyWorkflowSecurity)에서 부를 수 없다.
        // 실제 보안 설정으로 user/AccountSecurityTest·OAuthCallbackTest, chat/ChatFlowTest가 검사한다.
        // 대표 사진은 로그인 계정이 필요한 room/RoomDetailsFlowTest에서 업로드·권한·이미지 응답을 검사한다.
        all.removeAll(Set.of("POST /chat/messages", "GET /auth/csrf", "POST /auth/logout", "GET /users/me", "GET /users/me/rooms", "GET /users/me/map",
                "GET /oauth2/authorization/kakao", "GET /login/oauth2/code/kakao",
                "PATCH /rooms/{roomId}/members/me", "DELETE /rooms/{roomId}/members/me", "POST /rooms/{roomId}/plan-requests", "POST /rooms/{roomId}/host-transfer",
                "POST /payments/room-orders", "POST /payments/confirm", "GET /payments/orders/{orderId}",
                "POST /rooms/{roomId}/plan-orders", "POST /room-covers", "GET /room-covers/{coverId}/image"));
        assertThat(all).as("호출하지 않은 계약서 operation").isEmpty();
        assertThat(problems).as("계약서와 다른 응답").isEmpty();
    }

    /** "기억 안 나요"는 PENDING 질문에서만 200이다: 덧붙인 지수에게 질문이 생기면 forgot을 확인한다. */
    private void askForgotFlow(String roomId, String b) throws Exception {
        String mine = body(authed(get("/api/v1/rooms/" + roomId + "/askbacks/me"), b));
        List<String> pending = JsonPath.read(mine, "$.data.askbacks[?(@.status == 'PENDING')].id");
        if (!pending.isEmpty()) check("POST", "/askbacks/{askbackId}/forgot", authed(post("/api/v1/askbacks/" + pending.get(0) + "/forgot"), b), 200);
    }

    // ---- 계약서 대조 ----

    private String check(String method, String template, ResultActions response, int expectedStatus) throws Exception {
        var result = response.andReturn().getResponse();
        int status = result.getStatus();
        String body = result.getContentAsString();
        String label = method + " " + template + " → " + status;
        called.add(method + " " + template);
        if (status != expectedStatus) problems.add(label + " (테스트가 기대한 상태 " + expectedStatus + "): " + body);
        String opPointer = "/paths/" + template.replace("~", "~0").replace("/", "~1") + "/" + method.toLowerCase();
        JsonNode responseNode = contract.at(opPointer + "/responses/" + status);
        if (responseNode.isMissingNode()) {
            problems.add(label + ": 계약서에 선언되지 않은 응답 코드 (선언: " + contract.at(opPointer + "/responses").properties().stream().map(e -> e.getKey()).toList() + ")");
            return body;
        }
        String responsePointer = responseNode.has("$ref") ? responseNode.get("$ref").asText().substring(1) : opPointer + "/responses/" + status;
        JsonNode content = contract.at(responsePointer + "/content");
        if (content.isMissingNode()) {
            if (!body.isEmpty()) problems.add(label + ": 계약서는 본문 없음인데 본문이 있음: " + body);
        } else if (content.has("application/json")) {
            String schemaPointer = responsePointer + "/content/application~1json/schema";
            var errors = FACTORY.getSchema(SchemaLocation.of("classpath:" + CONTRACT_PATH + "#" + schemaPointer.replace("{", "%7B").replace("}", "%7D")), CONFIG)
                    .validate(JSON.readTree(body));
            if (!errors.isEmpty()) problems.add(label + ": " + errors + " / " + body);
        }
        return body;
    }

    private Set<String> allOperations() {
        Set<String> operations = new LinkedHashSet<>();
        contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(op -> {
            if (Set.of("get", "post", "put", "patch", "delete").contains(op.getKey())) operations.add(op.getKey().toUpperCase() + " " + path.getKey());
        }));
        return new TreeSet<>(operations);
    }

    private static JsonNode readContract() {
        try (var in = new ClassPathResource(CONTRACT_PATH).getInputStream()) {
            return new ObjectMapper(new YAMLFactory()).readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 요청 도우미 ----

    private String files(int count) {
        StringBuilder files = new StringBuilder("{\"files\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) files.append(',');
            files.append("{\"clientFileId\":\"c").append(i).append("\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":").append(i).append('}');
        }
        return files.append("]}").toString();
    }

    private String entry(String kind, String transcript) {
        return "{\"kind\":\"" + kind + "\",\"inputMode\":\"TEXT\",\"transcript\":\"" + transcript + "\"}";
    }

    /** upload-requests 응답의 첫 사진을 uploadUrl로 올리고 complete한다. */
    private String putAndComplete(String uploadRequestsResponse, String token) throws Exception {
        String photoId = JsonPath.read(uploadRequestsResponse, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(uploadRequestsResponse, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3}));
        check("POST", "/photos/{photoId}/complete", authed(post("/api/v1/photos/" + photoId + "/complete"), token), 200);
        return photoId;
    }

    private String uploadOne(String roomId, String token) throws Exception {
        return putAndComplete(body(json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), token, files(1))), token);
    }

    private void drain() {
        while (worker.runNext()) {
            // 대기 중인 작업을 모두 실행한다.
        }
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
