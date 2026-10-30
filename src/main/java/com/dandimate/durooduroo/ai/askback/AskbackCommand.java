/**
 * 사진 한 장의 되묻기 분석 입력. eligibleMemberIds는 Spring이 FORGOT 응답자·한도 도달자 등을 미리 뺀 목록이고,
 * allowedGapTypes는 이번에 허용하는 구멍 유형이다(NO_ADDON은 Spring이 조건을 판정해 넣는다).
 */
package com.dandimate.durooduroo.ai.askback;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import java.util.List;

public record AskbackCommand(PhotoInput photo, List<MemberInput> members, List<EntryInput> entries,
                             List<PreviousAskback> previousAskbacks, Limits limits) {

    public record PreviousAskback(String gapType, String targetMemberId, String question, String status) {}

    public record Limits(List<String> eligibleMemberIds, List<String> allowedGapTypes) {}
}
