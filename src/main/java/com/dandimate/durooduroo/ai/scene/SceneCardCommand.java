/** 장면 카드 입력. entries에는 FIRST·ADDON·ASKBACK_ANSWER 확정 원문을 모두 넣는다. */
package com.dandimate.durooduroo.ai.scene;

import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import java.util.List;

public record SceneCardCommand(PhotoInput photo, List<EntryInput> entries) {
}
