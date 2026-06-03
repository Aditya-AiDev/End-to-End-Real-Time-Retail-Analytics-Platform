package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;

    public MetricsService(StoreEventRepository eventRepo, PosTransactionRepository posRepo) {
        this.eventRepo = eventRepo; this.posRepo = posRepo;
    }

    public MetricsDto getMetrics(String storeId, String traceId) {
        long t0 = System.currentTimeMillis();
        // Use a wide window covering both historical footage (2026-03-08)
        // and today's live data. 90-day window catches all ingested clips.
        Instant from = Instant.now().minus(Duration.ofDays(90));
        Instant to   = Instant.now();

        long visitors = eventRepo.countDistinctVisitors(storeId, from, to);
        double convRate = 0.0;
        if (visitors > 0) {
            long buyers = countConverted(storeId, from, to);
            convRate = Math.min(1.0, (double) buyers / visitors);
        }

        List<Object[]> dwellStats = eventRepo.findZoneDwellStats(storeId, from, to);
        Map<String, Long> avgByZone = new LinkedHashMap<>();
        long totalMs = 0, totalCnt = 0;
        for (Object[] r : dwellStats) {
            long cnt = ((Number) r[1]).longValue();
            long ms  = ((Number) r[2]).longValue();
            avgByZone.put((String) r[0], cnt > 0 ? ms / cnt : 0L);
            totalMs += ms; totalCnt += cnt;
        }

        int qd       = (int) Math.max(0, eventRepo.findCurrentQueueDepth(storeId, to.minusSeconds(1800)));
        long joins   = eventRepo.countBillingQueueJoins(storeId, from, to);
        long abandons= eventRepo.countAbandonments(storeId, from, to);

        log.info("trace_id={} metrics storeId={} latency_ms={}", traceId, storeId, System.currentTimeMillis()-t0);

        return MetricsDto.builder()
            .storeId(storeId).windowStart(from).windowEnd(to)
            .uniqueVisitors((int) visitors).conversionRate(convRate)
            .avgDwellSeconds(totalCnt > 0 ? (totalMs / totalCnt) / 1000L : 0L)
            .queueDepth(qd)
            .abandonmentRate(joins > 0 ? Math.min(1.0, (double) abandons / joins) : 0.0)
            .avgDwellByZone(avgByZone).build();
    }

    private long countConverted(String storeId, Instant from, Instant to) {
        var txList = posRepo.findByStoreIdAndTransactionTimeBetween(storeId, from, to);
        if (txList.isEmpty()) return 0;
        Set<String> converted = new HashSet<>();
        for (var tx : txList) {
            converted.addAll(eventRepo.findBillingVisitors(
                storeId, tx.getTransactionTime().minusSeconds(300), tx.getTransactionTime()));
        }
        return converted.size();
    }
}
