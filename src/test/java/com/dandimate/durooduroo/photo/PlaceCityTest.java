package com.dandimate.durooduroo.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.dandimate.durooduroo.photo.domain.PlaceCity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PlaceCityTest {
    @ParameterizedTest
    @CsvSource({"경북 경주시 포석로 1080,경주", "대구 중구 동성로2가,대구", "서울특별시 종로구 사직로,서울", "경북 울릉군 울릉읍,울릉",
            "제주특별자치도 제주시 연동,제주", "세종특별자치시 한누리대로,세종", "경기 수원시 장안구,수원", "부산 기장군 기장읍,부산"})
    void 주소에서_도시를_뽑는다(String address, String city) {
        assertThat(PlaceCity.of(address)).isEqualTo(city);
    }

    @Test void 주소가_없으면_도시도_없다() {
        assertThat(PlaceCity.of(null)).isNull();
        assertThat(PlaceCity.of("  ")).isNull();
    }
}
