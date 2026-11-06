/** 카카오 로컬 API로 장소를 검색합니다. 카카오 오류 내용과 키는 응답·로그에 남기지 않습니다. */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.photo.domain.PlaceCity;
import com.dandimate.durooduroo.photo.dto.PlaceView;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoPlaceSearch {
    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceSearch.class);
    private static final int MAX_QUERY_LENGTH = 50;

    private final String restKey;
    private final RestClient client;

    public KakaoPlaceSearch(@Value("${KAKAO_CLIENT_ID:}") String restKey,
                            @Value("${app.places.base-url:https://dapi.kakao.com}") String baseUrl) {
        this.restKey = restKey;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restKey).build();
    }

    public List<PlaceView> search(String query) {
        String keyword = query == null ? "" : query.strip();
        if (keyword.isEmpty() || keyword.length() > MAX_QUERY_LENGTH)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "검색어는 1~50자로 입력해 주세요.");
        if (restKey.isBlank()) throw unavailable();
        try {
            JsonNode body = client.get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json").queryParam("query", keyword).queryParam("size", 10).build())
                    .retrieve().body(JsonNode.class);
            List<PlaceView> places = new ArrayList<>();
            for (JsonNode place : body.path("documents")) {
                String road = place.path("road_address_name").asText("");
                String lot = place.path("address_name").asText("");
                places.add(new PlaceView(place.path("place_name").asText(), road.isBlank() ? lot : road,
                        PlaceCity.of(lot.isBlank() ? road : lot), place.path("y").asDouble(), place.path("x").asDouble(), place.path("id").asText(null)));
            }
            return places;
        } catch (RestClientResponseException ex) {
            log.warn("카카오 장소 검색 실패: HTTP {}", ex.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException ex) {
            log.warn("카카오 장소 검색 실패: {}", ex.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE, "지금은 장소를 검색할 수 없습니다. 장소 이름을 직접 입력해 주세요.");
    }
}
