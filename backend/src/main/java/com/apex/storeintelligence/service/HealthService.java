package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.repository.StoreEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private final StoreEventRepository eventRepo;

    public HealthService(StoreEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    public HealthDto getHealth(String traceId) {
        Instant now  = Instant.now();
        boolean dbOk = true;
        Map<String, HealthDto.StoreHealth> map = new LinkedHashMap<>();

        try {
            for (String sid : eventRepo.findDistinctStoreIds()) {
                var last = eventRepo.findTopByStoreIdOrderByTimestampDesc(sid);
                long lag = last.map(e -> now.getEpochSecond() - e.getTimestamp().getEpochSecond())
                               .orElse(Long.MAX_VALUE);
                map.put(sid, HealthDto.StoreHealth.builder()
                    .storeId(sid)
                    .lastEventAt(last.map(e -> e.getTimestamp()).orElse(null))
                    .staleFeed(lag > 600)
                    .lagSeconds(lag == Long.MAX_VALUE ? -1 : lag)
                    .eventCountLast5Min((int) eventRepo.countEventsAfter(sid, now.minusSeconds(300)))
                    .build());
            }
        } catch (Exception e) {
            dbOk = false;
            log.error("health db error: {}", e.getMessage());
        }

        String status = !dbOk ? "DOWN" :
            map.values().stream().anyMatch(HealthDto.StoreHealth::isStaleFeed) ? "DEGRADED" : "UP";

        log.info("trace_id={} health status={}", traceId, status);
        return HealthDto.builder().status(status).checkedAt(now).stores(map).dbConnected(dbOk).build();
    }
}
