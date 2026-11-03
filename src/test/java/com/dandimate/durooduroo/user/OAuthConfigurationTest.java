package com.dandimate.durooduroo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"KAKAO_CLIENT_ID=", "KAKAO_CLIENT_SECRET=", "KAKAO_REDIRECT_URI=",
    "spring.datasource.url=jdbc:h2:mem:oauth-missing;MODE=MySQL;DB_CLOSE_DELAY=-1", "app.jobs.poll-interval-ms=3600000"})
@AutoConfigureMockMvc
class OAuthConfigurationTest {
    @Autowired MockMvc mvc;
    @Test void missingProviderConfigurationKeepsProtectedApisClosed() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("OAUTH_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}
