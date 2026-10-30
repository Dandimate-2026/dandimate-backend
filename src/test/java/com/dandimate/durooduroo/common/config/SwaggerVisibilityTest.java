package com.dandimate.durooduroo.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

class SwaggerVisibilityTest {

    @Nested
    @SpringBootTest(properties = "app.jobs.poll-interval-ms=3600000")
    @AutoConfigureMockMvc
    class 켜져_있으면 {
        @Autowired MockMvc mockMvc;

        @Test
        void 문서와_계약서가_열린다() throws Exception {
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
            mockMvc.perform(get("/openapi/public-api.yaml")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = {"app.swagger.enabled=false", "app.jobs.poll-interval-ms=3600000"})
    @AutoConfigureMockMvc
    class 꺼져_있으면 {
        @Autowired MockMvc mockMvc;

        @Test
        void 문서를_숨긴다() throws Exception {
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
            mockMvc.perform(get("/openapi/public-api.yaml")).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        }
    }
}
