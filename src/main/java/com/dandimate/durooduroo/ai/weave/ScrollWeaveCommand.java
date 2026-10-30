/** 엮기 입력. sceneCards는 7장 결과 전체이고, sourceEntries는 entryId로 찾는 원문이다. */
package com.dandimate.durooduroo.ai.weave;

import com.dandimate.durooduroo.ai.scene.SceneCardResult;
import java.util.List;
import java.util.Map;

public record ScrollWeaveCommand(RoomInput room, List<OrderedSceneCard> sceneCards, Map<String, SourceEntry> sourceEntries) {

    public record RoomInput(String id, String name) {}

    public record OrderedSceneCard(int displayOrder, SceneCardResult card) {}

    public record SourceEntry(String photoId, String memberId, String nickname, String transcript) {}
}
