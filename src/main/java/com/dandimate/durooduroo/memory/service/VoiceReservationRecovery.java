package com.dandimate.durooduroo.memory.service;

import com.dandimate.durooduroo.memory.repository.VoiceUsageRepository;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 프로세스 중단으로 남은 예약을 복구한다. 외부 AI 요청 제한시간보다 충분히 긴 1시간 후 해제. */
@Component
public class VoiceReservationRecovery {
    private final VoiceUsageRepository usages;
    private final VoiceQuotaService quota;
    public VoiceReservationRecovery(VoiceUsageRepository usages, VoiceQuotaService quota) {
        this.usages = usages; this.quota = quota;
    }
    @Scheduled(fixedDelayString = "PT5M")
    public void recover() {
        var cutoff = LocalDateTime.now().minusHours(1);
        for (var usage : usages.findAllByStatusAndUpdatedAtBefore("RESERVED", cutoff))
            quota.expire(usage.getRoomId(), usage.getId(), cutoff);
    }
}
