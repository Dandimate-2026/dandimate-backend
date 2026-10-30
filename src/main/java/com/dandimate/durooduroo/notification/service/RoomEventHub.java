/**
 * 방별 SSE 연결을 들고 이벤트를 보낸다. 서버 한 대 기준이며, 여러 대로 늘리면 Redis pub/sub 같은 중계가 필요하다.
 * 끊긴 연결은 보내다 실패하거나 시간이 지나면 정리하고, 25초마다 주석 줄을 보내 프록시가 연결을 끊지 않게 한다.
 */
package com.dandimate.durooduroo.notification.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RoomEventHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private record Subscriber(String memberId, SseEmitter emitter) {}

    private final Map<String, List<Subscriber>> rooms = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String roomId, String memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(memberId, emitter);
        rooms.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>()).add(subscriber);
        Runnable remove = () -> rooms.getOrDefault(roomId, List.of()).remove(subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    public void send(RoomEvent event) {
        for (Subscriber subscriber : rooms.getOrDefault(event.roomId(), List.of())) {
            if (event.targetMemberId() != null && !event.targetMemberId().equals(subscriber.memberId())) continue;
            try {
                subscriber.emitter().send(SseEmitter.event().name(event.name()).data(event.data()));
            } catch (IOException | IllegalStateException e) {
                rooms.getOrDefault(event.roomId(), List.of()).remove(subscriber);
            }
        }
    }

    @Scheduled(fixedDelayString = "PT25S")
    public void heartbeat() {
        rooms.forEach((roomId, subscribers) -> subscribers.forEach(subscriber -> {
            try {
                subscriber.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                subscribers.remove(subscriber);
            }
        }));
    }
}
