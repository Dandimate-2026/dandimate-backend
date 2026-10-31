package com.dandimate.durooduroo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 응답이 계약서(static/openapi/public-api.yaml)의 JSON Schema(2020-12)를 따르는지 검사한다 (명세 0장 완료 조건).
 * format(date-time·date·uri)도 검사하므로 오프셋 없는 시각이나 상대 URL은 실패한다. 계약서가 바뀌면 이 파일을 다시 복사해야 한다.
 */
@org.springframework.context.annotation.Import(com.dandimate.durooduroo.support.LegacyWorkflowSecurity.class)
@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=50"})
@AutoConfigureMockMvc
class ContractSchemaTest {

    private static final String CONTRACT = "classpath:static/openapi/public-api.yaml";
    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 주요_API_응답이_계약서_스키마를_따른다() throws Exception {
        String created = matches("CreateRoomResponse", json(post("/api/v1/rooms"), null,
                "{\"name\":\"경주\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}"));
        String roomId = JsonPath.read(created, "$.data.room.id");
        String host = JsonPath.read(created, "$.data.memberAccessToken");
        String invite = JsonPath.read(created, "$.data.inviteToken");

        matches("InvitePreviewResponse", mockMvc.perform(get("/api/v1/invites/" + invite)));
        String joined = matches("JoinRoomResponse", json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}"));
        String member = JsonPath.read(joined, "$.data.memberAccessToken");

        String requested = matches("UploadRequestsResponse", json(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), host,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mockMvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(new byte[]{1, 2, 3})).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/photos/" + photoId + "/complete").header("Authorization", "Bearer " + host)).andExpect(status().isOk());

        matches("EntryResponse", json(post("/api/v1/photos/" + photoId + "/entries"), host,
                "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"다들 갑자기 웃은 사진\"}"));
        matches("RoomHomeResponse", authed(get("/api/v1/rooms/" + roomId), host));
        matches("CirculationResponse", authed(get("/api/v1/rooms/" + roomId + "/circulation"), member));

        await().atMost(Duration.ofSeconds(5)).until(() ->
                JsonPath.<Integer>read(body(authed(get("/api/v1/rooms/" + roomId + "/askbacks/me"), host)), "$.data.askbacks.length()") == 1);
        String askbacks = matches("AskbackListResponse", authed(get("/api/v1/rooms/" + roomId + "/askbacks/me"), host));
        String askbackId = JsonPath.read(askbacks, "$.data.askbacks[0].id");
        matches("AskbackDetailResponse", authed(get("/api/v1/askbacks/" + askbackId), host));
        matches("AskbackAnswerResponse", json(post("/api/v1/askbacks/" + askbackId + "/answer"), host, "{\"inputMode\":\"TEXT\",\"transcript\":\"지수가 넘어졌거든\"}"));

        matchesPath("/rooms/{roomId}/notifications", "get", "200", authed(get("/api/v1/rooms/" + roomId + "/notifications"), host));
        matchesPath("/rooms/{roomId}/event-token", "post", "201", authed(post("/api/v1/rooms/" + roomId + "/event-token"), host));

        String closed = matches("JobAcceptedResponse", json(post("/api/v1/rooms/" + roomId + "/close"), host, "{\"mode\":\"DEMO\"}"));
        String jobId = JsonPath.read(closed, "$.data.job.id");
        await().atMost(Duration.ofSeconds(5)).until(() ->
                "SUCCEEDED".equals(JsonPath.read(body(authed(get("/api/v1/jobs/" + jobId), host)), "$.data.status")));
        matches("JobResponse", authed(get("/api/v1/jobs/" + jobId), host));
        matches("ScrollResponse", authed(get("/api/v1/rooms/" + roomId + "/scroll"), member));

        matches("ErrorResponse", mockMvc.perform(get("/api/v1/invites/does-not-exist-token-000000")));
    }

    private String matches(String component, ResultActions response) throws Exception {
        return validate(CONTRACT + "#/components/schemas/" + component, component, response);
    }

    private String matchesPath(String path, String method, String status, ResultActions response) throws Exception {
        String pointer = "/paths/" + path.replace("~", "~0").replace("/", "~1").replace("{", "%7B").replace("}", "%7D")
                + "/" + method + "/responses/" + status + "/content/application~1json/schema";
        return validate(CONTRACT + "#" + pointer, method.toUpperCase() + " " + path, response);
    }

    private String validate(String location, String label, ResultActions response) throws Exception {
        String body = body(response);
        JsonNode node = JSON.readTree(body);
        var errors = FACTORY.getSchema(SchemaLocation.of(location), CONFIG).validate(node);
        assertThat(errors).as("%s 응답이 계약서와 다름: %s%n%s", label, errors, body).isEmpty();
        return body;
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
