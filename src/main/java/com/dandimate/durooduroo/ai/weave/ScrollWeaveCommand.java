/** 엮기 입력. sceneCards는 7장 결과 전체이고, sourceEntries는 entryId로 찾는 원문이다. */
package com.dandimate.durooduroo.ai.weave;

import com.dandimate.durooduroo.ai.scene.SceneCardResult;
import java.util.List;
import java.util.Map;

public record ScrollWeaveCommand(RoomInput room, List<OrderedSceneCard> sceneCards, Map<String, SourceEntry> sourceEntries) {

    /** meetingDate는 모임 날짜(yyyy-MM-dd, 없으면 null). 두루마리 여는 문장에 쓴다. */
    public record RoomInput(String id, String name, String meetingDate) {

        public RoomInput(String id, String name) {
            this(id, name, null);
        }
    }

    /** photoScene은 사진을 실제로 보고 만든 장면 카드일 때만 card.what을 담는다(사진이 없으면 null). 문단 첫머리 장면 묘사에만 쓴다. */
    public record OrderedSceneCard(int displayOrder, SceneCardResult card, String photoScene) {

        public OrderedSceneCard(int displayOrder, SceneCardResult card) {
            this(displayOrder, card, null);
        }
    }

    public record SourceEntry(String photoId, String memberId, String nickname, String transcript) {}
}
