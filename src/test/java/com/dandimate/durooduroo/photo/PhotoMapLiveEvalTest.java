package com.dandimate.durooduroo.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.job.service.JobWorker;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 카카오 장소 검색과 OpenAI로 검색 → 장소 저장 → 엮기 → 내 지도까지 돌린다.
 * MAP_LIVE_EVAL=true일 때만 돈다(기본 CI는 건너뜀). 결과는 build/reports/map-live/report.md.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:map-live;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "storage.local-root=build/test-uploads", "KAKAO_CLIENT_SECRET=test-secret",
        "KAKAO_REDIRECT_URI=http://localhost/login/oauth2/code/kakao", "app.demo-enabled=true", "app.jobs.retry-delay-ms=0",
        "app.jobs.poll-interval-ms=3600000", "app.jobs.deadline-check-interval-ms=3600000", "app.jobs.stale-check-interval-ms=3600000"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MAP_LIVE_EVAL", matches = "true")
class PhotoMapLiveEvalTest {
    private static final String CONSENT = "\"consent\":{\"version\":\"v1\",\"terms\":true,\"mediaProcessing\":true,\"externalAiProcessing\":true}";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired KakaoAccountService accounts;
    @Autowired JobWorker worker;

    private MockHttpSession login() {
        AccountPrincipal principal = new AccountPrincipal(accounts.findOrCreate(UUID.randomUUID().toString()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private String send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        request.session(session).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) request.contentType("application/json").content(body);
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertThat(response.getStatus()).as(response.getContentAsString()).isLessThan(300);
        return response.getContentAsString();
    }

    private String[] room(MockHttpSession host, String name, String date) throws Exception {
        String created = send(post("/api/v1/rooms"), host, "{\"name\":\"" + name + "\",\"meetingDate\":\"" + date + "\",\"planType\":\"FREE\",\"hostNickname\":\"서연\"," + CONSENT + "}");
        return new String[]{JsonPath.read(created, "$.data.room.id"), JsonPath.read(created, "$.data.inviteToken")};
    }

    /** 카카오에서 검색해 고른 첫 장소를 사진에 저장하고 첫 한마디를 남긴다. */
    private String photoAt(String roomId, MockHttpSession session, String query, String story, List<String> log) throws Exception {
        String requested = send(post("/api/v1/rooms/" + roomId + "/photos/upload-requests"), session,
                "{\"files\":[{\"clientFileId\":\"c1\",\"fileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"displayOrder\":1}]}");
        String photoId = JsonPath.read(requested, "$.data.uploads[0].photoId");
        String uploadUrl = JsonPath.read(requested, "$.data.uploads[0].uploadUrl");
        mvc.perform(put(uploadUrl.substring("http://localhost".length())).contentType("image/png").content(UUID.randomUUID().toString().getBytes()));
        send(post("/api/v1/photos/" + photoId + "/complete"), session, null);

        var search = mvc.perform(get("/api/v1/places/search").param("query", query).session(session)).andReturn().getResponse();
        search.setCharacterEncoding("UTF-8");
        assertThat(search.getStatus()).as(search.getContentAsString()).isEqualTo(200);
        Map<String, Object> place = JsonPath.read(search.getContentAsString(), "$.data[0]");
        log.add("- 검색 `" + query + "` → " + place);
        String saved = send(put("/api/v1/photos/" + photoId + "/location"), session, JSON.writeValueAsString(place.entrySet().stream()
                .filter(e -> !"city".equals(e.getKey())).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        log.add("- 저장 → " + saved);
        send(post("/api/v1/photos/" + photoId + "/entries"), session, "{\"kind\":\"FIRST\",\"inputMode\":\"TEXT\",\"transcript\":\"" + story + "\"}");
        return photoId;
    }

    @Test void 실제_카카오와_AI로_지도까지_돌린다() throws Exception {
        MockHttpSession host = login();
        List<String> log = new ArrayList<>(List.of("# 지도 실제 실행 결과", ""));
        String[] gyeongju = room(host, "경주 여행", "2026-10-17");
        photoAt(gyeongju[0], host, "경주 황리단길", "비가 쏟아져서 우산 하나로 셋이 걸었어. 민호가 제일 많이 젖었지.", log);
        photoAt(gyeongju[0], host, "첨성대", "첨성대 앞에서 신발 끈 묶다 넘어졌어. 다음엔 보문호수 가자고 했잖아.", log);
        String[] daegu = room(host, "대구 모임", "2026-10-24");
        photoAt(daegu[0], host, "대구 동성로", "막차 놓쳐서 택시 나눠 탔어.", log);

        send(post("/api/v1/rooms/" + gyeongju[0] + "/close"), host, "{\"mode\":\"DEMO\"}");
        long started = System.currentTimeMillis();
        while (worker.runNext()) { }
        log.add("- 엮기·요약 작업 " + (System.currentTimeMillis() - started) / 1000 + "초");

        var map = mvc.perform(get("/api/v1/users/me/map").session(host)).andReturn().getResponse();
        map.setCharacterEncoding("UTF-8");
        log.add("");
        log.add("```json");
        log.add(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(map.getContentAsString()).path("data")));
        log.add("```");
        Path report = Path.of("build/reports/map-live/report.md");
        Files.createDirectories(report.getParent());
        Files.write(report, log);
        assertThat(map.getStatus()).isEqualTo(200);
    }
}
