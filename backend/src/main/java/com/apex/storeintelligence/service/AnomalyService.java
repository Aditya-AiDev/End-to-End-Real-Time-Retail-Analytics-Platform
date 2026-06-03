package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);
    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;
    private final SimpMessagingTemplate ws;
    private final Map<String, List<AnomalyDto>> cache = new HashMap<>();

    public AnomalyService(StoreEventRepository eventRepo, PosTransactionRepository posRepo,
                          SimpMessagingTemplate ws) {
        this.eventRepo = eventRepo; this.posRepo = posRepo; this.ws = ws;
    }

    public List<AnomalyDto> getAnomalies(String storeId, String traceId) {
        log.info("trace_id={} anomalies storeId={}", traceId, storeId);
        return cache.getOrDefault(storeId, Collections.emptyList());
    }

    @Scheduled(fixedDelay = 60_000)
    public void scan() {
        eventRepo.findDistinctStoreIds().forEach(sid -> {
            try {
                List<AnomalyDto> found = detect(sid);
                cache.put(sid, found);
                if (!found.isEmpty()) ws.convertAndSend("/topic/anomalies/" + sid, found);
            } catch (Exception e) {
                log.error("anomaly scan failed store={}: {}", sid, e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<AnomalyDto> detect(String sid) {
        List<AnomalyDto> res = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Queue spike
        long qd = Math.max(0, eventRepo.findCurrentQueueDepth(sid, now.minusSeconds(1800)));
        if (qd >= 15) res.add(a(sid,"QUEUE_SPIKE","CRITICAL",
            "Queue depth "+qd+" (threshold 15)","Deploy additional staff immediately",
            Map.of("queue_depth", qd)));
        else if (qd >= 8) res.add(a(sid,"QUEUE_SPIKE","WARN",
            "Queue depth "+qd+" (threshold 8)","Prepare to open additional counter",
            Map.of("queue_depth", qd)));

        // Use 90-day window to cover historical footage (2026-03-08)
        Instant todayStart = Instant.now().minus(Duration.ofDays(90));
        long tv = eventRepo.countDistinctVisitors(sid, todayStart, now);
        long tp = posRepo.countTransactions(sid, todayStart, now);
        double tr = tv > 0 ? (double) tp / tv : 0;
        long pv  = eventRepo.countDistinctVisitors(sid, now.minus(Duration.ofDays(7)), todayStart);
        long pp  = posRepo.countTransactions(sid, now.minus(Duration.ofDays(7)), todayStart);
        double pr = pv > 0 ? (double) pp / pv : 0;
        if (pr > 0 && tv > 10 && tr < pr * 0.70)
            res.add(a(sid,"CONVERSION_DROP","WARN",
                String.format("Today %.1f%% vs 7-day avg %.1f%%", tr*100, pr*100),
                "Review billing queue and promotional displays",
                Map.of("today_rate", tr, "avg_rate", pr)));

        // 3. Dead zone
        List<String> active4h  = eventRepo.findActiveZonesSince(sid, now.minusSeconds(14400));
        List<String> active30m = eventRepo.findActiveZonesSince(sid, now.minusSeconds(1800));
        for (String z : active4h)
            if (!active30m.contains(z))
                res.add(a(sid,"DEAD_ZONE","INFO",
                    "Zone "+z+" had no visits in 30 min",
                    "Check camera and display for zone "+z,
                    Map.of("zone_id", z)));

        // 4. Stale feed
        eventRepo.findTopByStoreIdOrderByTimestampDesc(sid).ifPresent(last -> {
            long lag = now.getEpochSecond() - last.getTimestamp().getEpochSecond();
            if (lag > 600) res.add(a(sid,"STALE_FEED","CRITICAL",
                "No events for "+(lag/60)+" minutes","Check detection pipeline health",
                Map.of("lag_seconds", lag)));
        });

        return res;
    }

    private AnomalyDto a(String sid, String type, String sev, String desc, String action,
                          Map<String,Object> det) {
        return AnomalyDto.builder().anomalyId(UUID.randomUUID().toString()).storeId(sid)
            .anomalyType(type).severity(sev).description(desc).suggestedAction(action)
            .detectedAt(Instant.now()).details(det).build();
    }
}
