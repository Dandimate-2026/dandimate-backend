package com.dandimate.durooduroo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dandimate.durooduroo.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiResponseFormatTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 성공_응답은_data와_meta_requestId를_가진다() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void 토큰_없는_요청은_401_INVALID_MEMBER_TOKEN() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/any"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/rooms/any/circulation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_MEMBER_TOKEN"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 깨진_JSON은_400_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 없는_초대_토큰은_404_ROOM_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/v1/invites/NOPE00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 기존_코드_문자열은_명세_코드로_매핑된다() {
        assertThat(ErrorCode.from("MEMBER_FORBIDDEN")).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ErrorCode.from("DUPLICATE_NICKNAME").status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.from("PHOTO_NOT_FOUND")).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ErrorCode.from("FILE_TOO_LARGE").status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
