/** 이벤트를 남긴다. 호출한 트랜잭션에 함께 묶이며, 값은 짧은 key=value 문자열로 저장한다. */
package com.dandimate.durooduroo.event;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EventLogger {

    private final EventLogRepository logs;

    public EventLogger(EventLogRepository logs) {
        this.logs = logs;
    }

    public void log(String roomId, String memberId, String name, Map<String, String> properties) {
        String values = new TreeMap<>(properties).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
        logs.save(EventLog.of(roomId, memberId, name, values));
    }

    public void log(String roomId, String memberId, String name) {
        log(roomId, memberId, name, Map.of());
    }

    /** 방에서 처음 한 번만 남기는 이벤트(예: all_members_completed). */
    public void logOnce(String roomId, String name) {
        if (!logs.existsByRoomIdAndName(roomId, name)) log(roomId, null, name);
    }
}
