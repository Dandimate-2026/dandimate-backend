/**
 * 사진 한 장의 되묻기 분석 입력. eligibleMemberIds는 Spring이 FORGOT 응답자·한도 도달자 등을 미리 뺀 목록이고,
 * allowedGapTypes는 이번에 허용하는 구멍 유형이다(NO_ADDON은 Spring이 조건을 판정해 넣는다).
 * image는 사진 바이트(없으면 null)로, JSON 입력에는 넣지 않고 이미지를 볼 수 있는 구현만 따로 보낸다.
 */
package com.dandimate.durooduroo.ai.askback;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.ImageInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record AskbackCommand(PhotoInput photo, List<MemberInput> members, List<EntryInput> entries,
                             List<PreviousAskback> previousAskbacks, Limits limits, @JsonIgnore ImageInput image) {

    public AskbackCommand(PhotoInput photo, List<MemberInput> members, List<EntryInput> entries,
                          List<PreviousAskback> previousAskbacks, Limits limits) {
        this(photo, members, entries, previousAskbacks, limits, null);
    }

    public record PreviousAskback(String gapType, String targetMemberId, String question, String status) {}

    public record Limits(List<String> eligibleMemberIds, List<String> allowedGapTypes) {}
}
