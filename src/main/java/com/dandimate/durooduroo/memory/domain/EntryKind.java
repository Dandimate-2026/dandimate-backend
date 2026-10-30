/** 원문 종류: 첫 한마디 / 덧붙임 / 되묻기 답. */
package com.dandimate.durooduroo.memory.domain;

public enum EntryKind {
    FIRST(5, 300),
    ADDON(1, 200),
    ASKBACK_ANSWER(1, 200);

    private final int minLength;
    private final int maxLength;

    EntryKind(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    /** 글 길이 규칙: 첫 한마디 5~300자, 덧붙임·되묻기 답 1~200자. */
    public boolean acceptsLength(String transcript) {
        int length = transcript.strip().length();
        return length >= minLength && length <= maxLength;
    }

    public int minLength() {
        return minLength;
    }

    public int maxLength() {
        return maxLength;
    }
}
