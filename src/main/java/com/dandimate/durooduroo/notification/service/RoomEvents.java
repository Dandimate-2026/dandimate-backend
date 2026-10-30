/**
 * 방 이벤트를 발행한다. 트랜잭션 안에서 부르면 커밋된 뒤에만 SSE로 나가고(롤백되면 버려진다), 트랜잭션 밖이면 바로 나간다.
 * 프론트는 이벤트를 받으면 REST를 다시 조회하므로, 잠금 규칙은 REST에서 그대로 적용된다.
 */
package com.dandimate.durooduroo.notification.service;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoomEvents {

    private final ApplicationEventPublisher publisher;
    private final RoomEventHub hub;

    public RoomEvents(ApplicationEventPublisher publisher, RoomEventHub hub) {
        this.publisher = publisher;
        this.hub = hub;
    }

    public void toRoom(String roomId, String name, Map<String, Object> data) {
        publisher.publishEvent(RoomEvent.toRoom(roomId, name, data));
    }

    public void toMember(String roomId, String memberId, String name, Map<String, Object> data) {
        publisher.publishEvent(RoomEvent.toMember(roomId, memberId, name, data));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void deliver(RoomEvent event) {
        hub.send(event);
    }
}
