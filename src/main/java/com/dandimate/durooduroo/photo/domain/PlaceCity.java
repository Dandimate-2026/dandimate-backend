/** 주소에서 지도 핀을 묶을 도시 이름을 뽑습니다. 광역시는 시 이름, 도는 시·군 이름을 씁니다. */
package com.dandimate.durooduroo.photo.domain;

import java.util.List;
import java.util.Map;

public final class PlaceCity {
    private static final List<String> METROPOLITAN = List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종");
    private static final Map<String, String> ADMIN_CODES = Map.ofEntries(
            Map.entry("대구", "2700000000"), Map.entry("포항", "4711000000"), Map.entry("경주", "4713000000"),
            Map.entry("김천", "4715000000"), Map.entry("안동", "4717000000"), Map.entry("구미", "4719000000"),
            Map.entry("영주", "4721000000"), Map.entry("영천", "4723000000"), Map.entry("상주", "4725000000"),
            Map.entry("문경", "4728000000"), Map.entry("경산", "4729000000"), Map.entry("군위", "2772000000"),
            Map.entry("의성", "4773000000"), Map.entry("청송", "4775000000"), Map.entry("영양", "4776000000"),
            Map.entry("영덕", "4777000000"), Map.entry("청도", "4782000000"), Map.entry("고령", "4783000000"),
            Map.entry("성주", "4784000000"), Map.entry("칠곡", "4785000000"), Map.entry("예천", "4790000000"),
            Map.entry("봉화", "4792000000"), Map.entry("울진", "4793000000"), Map.entry("울릉", "4794000000")
    );

    private PlaceCity() {}

    /** "경북 경주시 포석로" → "경주", "대구 중구 동성로" → "대구". 주소가 없으면 null. */
    public static String of(String address) {
        if (address == null || address.isBlank()) return null;
        String[] parts = address.strip().split("\\s+");
        for (String city : METROPOLITAN) {
            if (parts[0].startsWith(city)) return city;
        }
        if (parts.length == 1) return parts[0];
        return parts[1].matches(".+[시군]") ? parts[1].substring(0, parts[1].length() - 1) : parts[1];
    }

    /** 기록 지도에서 시·군 단위 경계와 맞출 10자리 행정구역 코드. 현재 서비스 지역(대구·경북)만 제공한다. */
    public static String adminAreaCode(String city) {
        return city == null ? null : ADMIN_CODES.get(city);
    }
}
