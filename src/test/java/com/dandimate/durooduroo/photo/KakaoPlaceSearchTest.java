package com.dandimate.durooduroo.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.photo.service.KakaoPlaceSearch;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class KakaoPlaceSearchTest {
    private static final String RESULT = """
            {"documents":[{"place_name":"황리단길","address_name":"경북 경주시 황남동 1","road_address_name":"경북 경주시 포석로 1080","x":"129.21","y":"35.83"},
            {"place_name":"동성로","address_name":"대구 중구 동성로2가","road_address_name":"","x":"128.59","y":"35.86"}]}""";

    private HttpServer server(int status, String body, AtomicReference<String> seen) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/keyword.json", exchange -> {
            seen.set(exchange.getRequestHeaders().getFirst("Authorization") + "|" + exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @Test void 검색_결과를_도시와_좌표로_바꾼다() throws Exception {
        var seen = new AtomicReference<String>();
        var server = server(200, RESULT, seen);
        try {
            var places = new KakaoPlaceSearch("rest-key", url(server)).search(" 황리단길 ");
            assertThat(seen.get()).startsWith("KakaoAK rest-key|").contains("query=황리단길");
            assertThat(places).hasSize(2);
            assertThat(places.get(0).address()).isEqualTo("경북 경주시 포석로 1080");
            assertThat(places.get(0).city()).isEqualTo("경주");
            assertThat(places.get(0).latitude()).isEqualTo(35.83);
            assertThat(places.get(0).longitude()).isEqualTo(129.21);
            assertThat(places.get(1).address()).isEqualTo("대구 중구 동성로2가");
            assertThat(places.get(1).city()).isEqualTo("대구");
        } finally { server.stop(0); }
    }

    @Test void 카카오가_거절하면_내부_오류_없이_검색_불가로_답한다() throws Exception {
        var server = server(403, "{\"errorType\":\"NotAuthorizedError\",\"message\":\"secret detail\"}", new AtomicReference<>());
        try {
            assertThatThrownBy(() -> new KakaoPlaceSearch("rest-key", url(server)).search("경주"))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
                        assertThat(ex.getMessage()).doesNotContain("secret detail");
                    });
        } finally { server.stop(0); }
    }

    @Test void 키가_없거나_검색어가_잘못되면_호출하지_않는다() {
        var search = new KakaoPlaceSearch("", "http://127.0.0.1:1");
        assertThatThrownBy(() -> search.search("경주")).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLACE_SEARCH_UNAVAILABLE));
        assertThatThrownBy(() -> search.search("  ")).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
