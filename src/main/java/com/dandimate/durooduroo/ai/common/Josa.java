/** 이름 뒤 조사를 받침에 맞춘다: 서연 → 서연은, 지수 → 지수는. 한글로 끝나지 않으면 "은(는)". */
package com.dandimate.durooduroo.ai.common;

public final class Josa {

    private Josa() {
    }

    public static String topic(String name) {
        if (name == null || name.isEmpty()) return "";
        char last = name.charAt(name.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return name + "은(는)";
        return name + ((last - 0xAC00) % 28 == 0 ? "는" : "은");
    }
}
