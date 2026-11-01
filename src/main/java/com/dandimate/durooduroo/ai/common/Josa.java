/**
 * 한국어 조사를 받침에 맞춘다.
 * - topic: 서연 → 서연은, 지수 → 지수는. 한글로 끝나지 않으면 "은(는)".
 * - fixQuoteParticles: 인용 뒤 '라고'를 인용 끝 받침에 맞춘다: '…넘어졌거든'라고 → '…넘어졌거든'이라고.
 */
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

    private static final java.util.regex.Pattern QUOTE_PARTICLE = java.util.regex.Pattern.compile("([가-힣])([’'])(이라고|라고)");

    public static String fixQuoteParticles(String text) {
        if (text == null) return null;
        var matcher = QUOTE_PARTICLE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            char last = matcher.group(1).charAt(0);
            String particle = (last - 0xAC00) % 28 == 0 ? "라고" : "이라고";
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + particle));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
