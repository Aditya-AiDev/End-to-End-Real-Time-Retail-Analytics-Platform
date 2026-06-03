package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
public class FunnelService {

    private static final Logger log = LoggerFactory.getLogger(FunnelService.class);
    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;

    public FunnelService(StoreEventRepository eventRepo, PosTransactionRepository posRepo) {
        this.eventRepo = eventRepo; this.posRepo = posRepo;
    }

    public FunnelDto getFunnel(String storeId, String traceId) {
        Instant from = Instant.now().minus(Duration.ofDays(90));
        Instant to   = Instant.now();

        long entries  = eventRepo.countDistinctVisitors(storeId, from, to);
        long zoneV    = Math.min(eventRepo.countVisitorsWithZoneEnter(storeId, from, to), entries);
        long billing  = Math.min(eventRepo.countBillingQueueJoins(storeId, from, to), zoneV);
        long purchases= Math.min(posRepo.countTransactions(storeId, from, to), billing);

        log.info("trace_id={} funnel storeId={}", traceId, storeId);
        return FunnelDto.builder()
            .storeId(storeId).entryCount((int) entries).zoneVisitCount((int) zoneV)
            .billingQueueCount((int) billing).purchaseCount((int) purchases)
            .entryToZoneDropPct(pct(entries - zoneV, entries))
            .zoneToBillingDropPct(pct(zoneV - billing, zoneV))
            .billingToPurchaseDropPct(pct(billing - purchases, billing))
            .build();
    }

    private double pct(long n, long d) {
        return d == 0 ? 0.0 : Math.round((double) n / d * 10000.0) / 100.0;
    }
}
