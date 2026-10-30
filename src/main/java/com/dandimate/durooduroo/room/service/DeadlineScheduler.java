/** 마감 시각이 지난 방을 찾아 mode=SCHEDULED 마감을 실행한다. 한 방의 실패가 다른 방 처리를 막지 않는다. */
package com.dandimate.durooduroo.room.service;

import com.dandimate.durooduroo.ai.service.WeaveJobService;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomStatus;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineScheduler.class);

    private final RoomRepository rooms;
    private final WeaveJobService weaveJobs;

    public DeadlineScheduler(RoomRepository rooms, WeaveJobService weaveJobs) {
        this.rooms = rooms;
        this.weaveJobs = weaveJobs;
    }

    @Scheduled(fixedDelayString = "${app.jobs.deadline-check-interval-ms:60000}")
    public void closeDueRooms() {
        LocalDateTime now = LocalDateTime.now();
        for (Room room : rooms.findAllByStatusInAndDeadlineAtLessThanEqual(List.of(RoomStatus.COLLECTING, RoomStatus.CIRCULATING), now)) {
            try {
                weaveJobs.closeIfDue(room.getId(), now);
            } catch (RuntimeException e) {
                log.warn("Scheduled close failed for room {}: {}", room.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
