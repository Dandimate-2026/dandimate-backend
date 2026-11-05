/** 주소에서 지도 핀을 묶을 도시 이름을 뽑습니다. 광역시는 시 이름, 도는 시·군 이름을 씁니다. */
package com.dandimate.durooduroo.photo.domain;

import java.util.List;

public final class PlaceCity {
    private static final List<String> METROPOLITAN = List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종");

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
}
