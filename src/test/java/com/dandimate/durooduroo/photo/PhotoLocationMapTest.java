package com.dandimate.durooduroo.photo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 실제 보안 설정(세션 쿠키·CSRF)으로 사진 장소 입력과 내 지도를 검사한다. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:photo-location;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads", "KAKAO_CLIENT_ID=test-client", "KAKAO_CLIENT_SECRET=test-secret",
        "KAKAO_REDIRECT_URI=http://localhost/login/oauth2/code/kakao", "app.demo-enabled=true",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
class PhotoLocationMapTest {
    private static final String CONSENT = "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private static final String GYEONGJU = "{\"placeName\":\"황리단길 카페\",\"address\":\"경북 경주시 포석로 1080\",\"latitude\":35.80,\"longitude\":129.20}";

    @Autowired MockMvc mvc;
    @Autowired KakaoAccountService accounts;
    @Autowired JobWorker worker;
    @Autowired com.dandimate.durooduroo.photo.repository.PhotoRepository photos;
    @Autowired com.dandimate.durooduroo.member.repository.MemberRepository members;

    private MockHttpSession login() {
        AccountPrincipal principal = new AccountPrincipal(accounts.findOrCreate(UUID.randomUUID().toString()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        request.session(session).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }

    private String[] createRoom(MockHttpSession host, String meetingDate) throws Exception {
        String created = send(post("/api/v1/rooms"), host, "{\"name\":\"경주 여행\",\"meetingDate\":\"" + meetingDate + "\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new String[]{JsonPath.read(created, "$.data.room.id"), JsonPath.read(created, "$.data.inviteToken")};
    }

    private String upload(String roomId, MockHttpSession session) throws Exception {
        String requested = send(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), session,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        // 파일마다 내용이 달라야 방 안 중복 사진으로 걸리지 않는다.
        mvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(UUID.randomUUID().toString().getBytes()))
                .andExpect(status().isOk());
        send(post("/api/v1/photos/" + photoId + "/complete"), session, null).andExpect(status().isOk());
        return photoId;
    }

    private void firstEntry(String photoId, MockHttpSession session) throws Exception {
        send(post("/api/v1/photos/" + photoId + "/entries"), session, "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"비 오던 날 다 같이 젖었어\"}")
                .andExpect(status().isCreated());
    }

    @Test void 공개된_사진의_장소가_도시_핀이_되고_완성되면_꽃이_핀다() throws Exception {
        MockHttpSession host = login(), guest = login(), stranger = login();
        String[] room = createRoom(host, "2026-10-17");
        send(post("/api/v1/invites/" + room[1] + "/join"), guest, "{\"nickname\":\"민주\"," + CONSENT + "}").andExpect(status().isCreated());
        String published = upload(room[0], host), unpublished = upload(room[0], guest), nameOnly = upload(room[0], host);

        send(put("/api/v1/photos/" + published + "/location"), host, GYEONGJU)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.city").value("경주"));
        send(put("/api/v1/photos/" + unpublished + "/location"), guest, "{\"placeName\":\"첨성대\",\"address\":\"경북 경주시 인왕동\",\"latitude\":35.84,\"longitude\":129.22}")
                .andExpect(status().isOk());
        send(put("/api/v1/photos/" + nameOnly + "/location"), host, "{\"placeName\":\"우리집 앞\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latitude").isEmpty());
        firstEntry(published, host);
        firstEntry(nameOnly, host);

        // 아직 공개 안 된 친구 사진과 좌표 없는 사진은 지도에 없다.
        mvc.perform(get("/api/v1/users/me/map").session(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities", hasSize(1)))
                .andExpect(jsonPath("$.data.cities[0].city").value("경주"))
                .andExpect(jsonPath("$.data.cities[0].photoCount").value(1))
                .andExpect(jsonPath("$.data.cities[0].recordCount").value(1))
                .andExpect(jsonPath("$.data.cities[0].bloomed").value(false))
                .andExpect(jsonPath("$.data.cities[0].summary").isEmpty())
                .andExpect(jsonPath("$.data.cities[0].rooms[0].roomId").value(room[0]))
                .andExpect(jsonPath("$.data.cities[0].rooms[0].scrollReady").value(false))
                .andExpect(jsonPath("$.data.cities[0].places[0].photoId").value(published))
                .andExpect(jsonPath("$.data.cities[0].places[0].placeName").value("황리단길 카페"))
                .andExpect(jsonPath("$.data.cities[0].places[0].imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.cities[0].places[0].uploader.nickname").value("서연"))
                .andExpect(jsonPath("$.data.cities[0].places[0].uploader.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.cities[0].places[0].firstEntrySnippet").value("비 오던 날 다 같이 젖었어"));
        // 방에 없는 사람에게는 보이지 않는다.
        mvc.perform(get("/api/v1/users/me/map").session(stranger))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cities", hasSize(0)));

        firstEntry(unpublished, guest);
        mvc.perform(get("/api/v1/users/me/map").session(host))
                .andExpect(jsonPath("$.data.cities[0].photoCount").value(2))
                .andExpect(jsonPath("$.data.cities[0].latitude").value(org.hamcrest.Matchers.closeTo(35.82, 0.0001)))
                .andExpect(jsonPath("$.data.cities[0].longitude").value(org.hamcrest.Matchers.closeTo(129.21, 0.0001)));

        send(post("/api/v1/rooms/" + room[0] + "/close"), host, "{\"mode\":\"DEMO\"}").andExpect(status().isAccepted());
        while (worker.runNext()) { }
        mvc.perform(get("/api/v1/users/me/map").session(guest))
                .andExpect(jsonPath("$.data.cities[0].bloomed").value(true))
                .andExpect(jsonPath("$.data.cities[0].rooms[0].status").value("GROWING"))
                .andExpect(jsonPath("$.data.cities[0].rooms[0].scrollReady").value(true))
                .andExpect(jsonPath("$.data.cities[0].summary").isNotEmpty())
                .andExpect(jsonPath("$.data.cities[0].rooms[0].summary").isNotEmpty());

        // 두루마리에도 장소가 함께 나온다.
        mvc.perform(get("/api/v1/rooms/" + room[0] + "/scroll").session(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphs[?(@.photo.id == '" + published + "')].photo.location.placeName").value("황리단길 카페"));

        // 나간 사람의 지도에서는 사라진다.
        send(delete("/api/v1/rooms/" + room[0] + "/members/me"), guest, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/map").session(guest)).andExpect(jsonPath("$.data.cities", hasSize(0)));
    }

    @Test void 본인_사진만_바꿀_수_있고_삭제하면_지도에서_빠진다() throws Exception {
        MockHttpSession host = login(), guest = login();
        String[] room = createRoom(host, "2026-10-18");
        send(post("/api/v1/invites/" + room[1] + "/join"), guest, "{\"nickname\":\"민주\"," + CONSENT + "}").andExpect(status().isCreated());
        String photo = upload(room[0], host);
        firstEntry(photo, host);

        send(put("/api/v1/photos/" + photo + "/location"), guest, GYEONGJU).andExpect(status().isForbidden());
        send(delete("/api/v1/photos/" + photo + "/location"), guest, null).andExpect(status().isForbidden());
        // 좌표는 둘 다, 주소와 함께 보내야 한다.
        send(put("/api/v1/photos/" + photo + "/location"), host, "{\"placeName\":\"카페\",\"latitude\":35.8}").andExpect(status().isBadRequest());
        send(put("/api/v1/photos/" + photo + "/location"), host, "{\"placeName\":\"카페\",\"latitude\":35.8,\"longitude\":129.2}").andExpect(status().isBadRequest());
        send(put("/api/v1/photos/" + photo + "/location"), host, "{\"placeName\":\"카페\",\"address\":\"경북 경주시\",\"latitude\":135,\"longitude\":129.2}").andExpect(status().isBadRequest());

        send(put("/api/v1/photos/" + photo + "/location"), host, GYEONGJU).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/map").session(host)).andExpect(jsonPath("$.data.cities", hasSize(1)));
        send(delete("/api/v1/photos/" + photo + "/location"), host, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/map").session(host)).andExpect(jsonPath("$.data.cities", hasSize(0)));
        mvc.perform(get("/api/v1/users/me/map")).andExpect(status().isUnauthorized());
    }

    @Test void processingPhotoKeepsLocationAfterDerivativeCompletes() throws Exception {
        var host=login(); var room=createRoom(host,"2026-11-07");
        var member=members.findAllByRoomIdAndLeftAtIsNull(room[0]).get(0);
        var photo=com.dandimate.durooduroo.photo.domain.Photo.pending(UUID.randomUUID().toString(),room[0],member.getId(),"image.heic","image.heic","image/heic",1);
        photos.save(photo);
        send(put("/api/v1/photos/"+photo.getId()+"/location"),host,GYEONGJU).andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PHOTO_NOT_READY"));
        photo.complete("hash"); photos.save(photo);
        send(put("/api/v1/photos/"+photo.getId()+"/location"),host,GYEONGJU).andExpect(status().isOk());
        photo=photos.findById(photo.getId()).orElseThrow(); photo.derived("derived.jpg");photo.publish();photos.save(photo);
        mvc.perform(get("/api/v1/users/me/map").session(host)).andExpect(jsonPath("$.data.cities[0].places[0].placeName").value("황리단길 카페"));
    }
}
