package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.repository.StoreEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HeatmapService {

    private static final Logger log = LoggerFactory.getLogger(HeatmapService.class);
    private final StoreEventRepository eventRepo;

    public HeatmapService(StoreEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    public HeatmapDto getHeatmap(String storeId, String traceId) {
        Instant from = Instant.now().minus(Duration.ofDays(90));
        Instant to   = Instant.now();
        List<Object[]> raw = eventRepo.findZoneDwellStats(storeId, from, to);

        if (raw.isEmpty()) {
            return HeatmapDto.builder().storeId(storeId).zones(Collections.emptyList()).dataConfidence(false).build();
        }

        long max      = raw.stream().mapToLong(r -> ((Number) r[1]).longValue()).max().orElse(1L);
        long sessions = eventRepo.countDistinctVisitors(storeId, from, to);

        List<ZoneHeat> zones = raw.stream().map(r -> {
            long cnt = ((Number) r[1]).longValue();
            long ms  = ((Number) r[2]).longValue();
            return ZoneHeat.builder()
                .zoneId((String) r[0]).zoneName((String) r[0])
                .visitCount((int) cnt).avgDwellMs(cnt > 0 ? ms / cnt : 0L)
                .normalizedScore((int) Math.round((double) cnt / max * 100))
                .build();
        }).collect(Collectors.toList());

        log.info("trace_id={} heatmap storeId={} zones={}", traceId, storeId, zones.size());
        return HeatmapDto.builder().storeId(storeId).zones(zones).dataConfidence(sessions >= 20).build();
    }
}
