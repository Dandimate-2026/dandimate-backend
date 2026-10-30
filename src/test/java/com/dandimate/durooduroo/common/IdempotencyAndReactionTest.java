package com.dandimate.durooduroo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.scroll.repository.ParagraphRepository;
import com.dandimate.durooduroo.scroll.repository.ScrollRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"storage.local-root=build/test-uploads", "app.jobs.poll-interval-ms=50"})
@AutoConfigureMockMvc
class IdempotencyAndReactionTest {

    private static final String CONSENT =
            "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository rooms;
    @Autowired private MemoryEntryRepository entries;
    @Autowired private ScrollRepository scrolls;
    @Autowired private ParagraphRepository paragraphs;

    @Test
    void 필수_API는_Idempotency_Key가_없으면_400() throws Exception {
        json(post("/api/v1/rooms").header("Idempotency-Key", ""), null, roomBody("키없음"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        json(post("/api/v1/rooms").header("Idempotency-Key", "too-short"), null, roomBody("짧은키"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_키로_다시_보내면_최초_응답을_돌려주고_한_번만_처리한다() throws Exception {
        String key = UUID.randomUUID().toString();
        long roomsBefore = rooms.count();

        String first = body(json(post("/api/v1/rooms").header("Idempotency-Key", key), null, roomBody("재시도방")).andExpect(status().isCreated()));
        String second = body(json(post("/api/v1/rooms").header("Idempotency-Key", key), null, roomBody("재시도방"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")));

        assertThat(second).isEqualTo(first);
        assertThat(rooms.count()).isEqualTo(roomsBefore + 1);
    }

    @Test
    void 같은_키에_다른_본문이면_409_IDEMPOTENCY_KEY_REUSED() throws Exception {
        Room room = createRoom();
        String photoId = uploadAndPublish(room);
        String key = UUID.randomUUID().toString();

        String firstEntry = body(json(post("/api/v1/photos/" + photoId + "/entries").header("Idempotency-Key", key), room.member,
                "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"나도 기억나\"}").andExpect(status().isCreated()));
        json(post("/api/v1/photos/" + photoId + "/entries").header("Idempotency-Key", key), room.member,
                "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"나도 기억나\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        json(post("/api/v1/photos/" + photoId + "/entries").header("Idempotency-Key", key), room.member,
                "{\"kind\":\"ADDON\",\"inputMode\":\"TEXT\",\"transcript\":\"다른 내용\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

        String entryId = JsonPath.read(firstEntry, "$.data.entry.id");
        assertThat(entries.findAllByPhotoId(photoId)).extracting(e -> e.getId()).containsOnlyOnce(entryId).hasSize(2);
    }

    @Test
    void 아_맞다는_참여자당_한_번이고_취소하면_줄어든다_평가는_덮어쓴다() throws Exception {
        Room room = createRoom();
        uploadAndPublish(room);
        String closed = body(json(post("/api/v1/rooms/" + room.id + "/close"), room.host, "{\"mode\":\"DEMO\"}").andExpect(status().isAccepted()));
        String jobId = JsonPath.read(closed, "$.data.job.id");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", "Bearer " + room.host))
                        .andExpect(jsonPath("$.data.status").value("SUCCEEDED")));
        String scrollId = scrolls.findTopByRoomIdOrderByCreatedAtDesc(room.id).orElseThrow().getId();
        String paragraphId = paragraphs.findAllByScrollId(scrollId).get(0).getId();
        String reaction = "/api/v1/paragraphs/" + paragraphId + "/reactions/ah-matda";

        mockMvc.perform(put(reaction)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(reaction).header("Authorization", "Bearer " + room.host)).andExpect(jsonPath("$.data.ahMatdaCount").value(1));
        mockMvc.perform(put(reaction).header("Authorization", "Bearer " + room.host)).andExpect(jsonPath("$.data.ahMatdaCount").value(1));
        mockMvc.perform(put(reaction).header("Authorization", "Bearer " + room.member)).andExpect(jsonPath("$.data.ahMatdaCount").value(2));
        mockMvc.perform(delete(reaction).header("Authorization", "Bearer " + room.host)).andExpect(status().isNoContent());
        assertThat(paragraphs.findById(paragraphId).orElseThrow().getAhMatdaCount()).isEqualTo(1);

        String rating = "/api/v1/paragraphs/" + paragraphId + "/fidelity-rating";
        json(put(rating), room.member, "{\"score\":3}").andExpect(jsonPath("$.data.score").value(3));
        json(put(rating), room.member, "{\"score\":5}").andExpect(jsonPath("$.data.score").value(5));
        json(put(rating), room.member, "{\"score\":6}").andExpect(status().isBadRequest());
    }

    private record Room(String id, String host, String member) {
    }

    private Room createRoom() throws Exception {
        String created = body(json(post("/api/v1/rooms"), null, roomBody("경주")).andExpect(status().isCreated()));
        String invite = JsonPath.read(created, "$.data.inviteToken");
        String joined = body(json(post("/api/v1/invites/" + invite + "/join"), null, "{\"nickname\":\"지수\"," + CONSENT + "}"));
        return new Room(JsonPath.read(created, "$.data.room.id"), JsonPath.read(created, "$.data.memberAccessToken"),
                JsonPath.read(joined, "$.data.memberAccessToken"));
    }

    /** 방장이 사진을 올리고 첫 한마디를 확정해 돌려보기에 노출한다. */
    private String uploadAndPublish(Room room) throws Exception {
        String requested = body(json(post("/api/v1/rooms/" + room.id + "/photos/upload-requests"), room.host,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}"));
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        mockMvc.perform(multipart("/api/v1/photos/" + photoId + "/complete")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + room.host))
                .andExpect(status().isOk());
        json(post("/api/v1/photos/" + photoId + "/entries"), room.host,
                "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"막차 놓쳐서 뛰었잖아\"}").andExpect(status().isCreated());
        return photoId;
    }

    private String roomBody(String name) {
        return "{\"name\":\"" + name + "\",\"meetingDate\":\"2026-10-24\",\"hostNickname\":\"서연\"," + CONSENT + "}";
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
