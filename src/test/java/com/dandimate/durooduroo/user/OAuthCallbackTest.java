package com.dandimate.durooduroo.user;

import com.dandimate.durooduroo.common.auth.KakaoUserService;
import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:oauth-callback;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "KAKAO_CLIENT_ID=test-client", "KAKAO_CLIENT_SECRET=test-secret", "KAKAO_REDIRECT_URI=http://localhost/login/oauth2/code/kakao",
    "APP_FRONTEND_URL=https://app.example.com", "app.jobs.poll-interval-ms=3600000"})
@AutoConfigureMockMvc
class OAuthCallbackTest {
    @Autowired MockMvc mvc;
    @Autowired KakaoUserService userService;
    @Autowired UserAccountRepository users;
    @MockitoBean(name="kakaoTokenClient") OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient;

    @Test void authorizationCallbackCreatesAccountAndSessionWithoutExposingProviderCredentials() throws Exception {
        when(tokenClient.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("test-access-token")
            .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(3600).build());
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer provider = MockRestServiceServer.createServer(rest);
        userService.setRestOperations(rest);
        provider.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(header("Authorization","Bearer test-access-token"))
            .andRespond(withSuccess("{\"id\":1234567890}", org.springframework.http.MediaType.APPLICATION_JSON));
        var start=mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session=(MockHttpSession)start.getRequest().getSession(false);
        String state=UriComponentsBuilder.fromUriString(start.getResponse().getHeader("Location")).build().getQueryParams().getFirst("state");
        state=UriUtils.decode(state, StandardCharsets.UTF_8);
        String oldSessionId=session.getId();
        var callback=mvc.perform(get("/login/oauth2/code/kakao").session(session).param("code","test-code").param("state",state))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://app.example.com")).andReturn();
        MockHttpSession loggedIn=(MockHttpSession)callback.getRequest().getSession(false);
        assertThat(loggedIn.getId()).isNotEqualTo(oldSessionId);
        String userId=users.findByProviderAndProviderUserId("KAKAO","1234567890").orElseThrow().getId();
        mvc.perform(get("/api/v1/users/me").session(loggedIn)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(userId));
        assertThat(callback.getResponse().getContentAsString()).doesNotContain("test-access-token", "test-secret");
        provider.verify();
        mvc.perform(get("/login/oauth2/code/kakao").session(loggedIn).param("code","test-code").param("state",state))
            .andExpect(status().isUnauthorized());
    }
}
