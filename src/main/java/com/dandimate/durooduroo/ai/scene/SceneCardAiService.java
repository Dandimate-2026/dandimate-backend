/** 장면 카드 추출 계약 (명세 7장). 사진과 그 사진의 원문에서만 사실을 뽑는다. */
package com.dandimate.durooduroo.ai.scene;

public interface SceneCardAiService {
    SceneCardResult extract(SceneCardCommand command);
}
