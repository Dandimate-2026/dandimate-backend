/**
 * 장면 카드 입력. entries에는 FIRST·ADDON·ASKBACK_ANSWER 확정 원문을 모두 넣는다.
 * image는 사진 바이트(없으면 null)로, JSON 입력에는 넣지 않고 이미지를 볼 수 있는 구현만 따로 보낸다.
 */
package com.dandimate.durooduroo.ai.scene;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.ImageInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record SceneCardCommand(PhotoInput photo, List<EntryInput> entries, @JsonIgnore ImageInput image) {

    public SceneCardCommand(PhotoInput photo, List<EntryInput> entries) {
        this(photo, entries, null);
    }
}
