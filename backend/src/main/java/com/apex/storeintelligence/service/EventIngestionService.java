package com.apex.storeintelligence.service;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.model.StoreEvent;
import com.apex.storeintelligence.repository.StoreEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class EventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);
    private final StoreEventRepository repo;
    private final SimpMessagingTemplate ws;

    public EventIngestionService(StoreEventRepository repo, SimpMessagingTemplate ws) {
        this.repo = repo; this.ws = ws;
    }

    @Transactional
    public IngestResponse ingest(IngestRequest request, String traceId) {
        long start = System.currentTimeMillis();
        int accepted = 0, rejected = 0, duplicates = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Integer> byStore = new HashMap<>();

        for (EventDto dto : request.getEvents()) {
            try {
                if (repo.existsByEventId(dto.getEventId())) { duplicates++; continue; }

                String raw = dto.getTimestamp();
                if (!raw.endsWith("Z")) raw += "Z";
                Instant ts = Instant.parse(raw);

                Map<String, Object> meta = dto.getMetadata() != null ? dto.getMetadata() : Map.of();

                repo.save(StoreEvent.builder()
                    .eventId   (dto.getEventId())
                    .storeId   (dto.getStoreId())
                    .cameraId  (dto.getCameraId())
                    .visitorId (dto.getVisitorId())
                    .eventType (dto.getEventType())
                    .timestamp (ts)
                    .zoneId    (dto.getZoneId())
                    .dwellMs   (dto.getDwellMs() != null ? dto.getDwellMs() : 0L)
                    .staff     (dto.isStaff())
                    .confidence(dto.getConfidence() != null ? dto.getConfidence() : 0.8)
                    .queueDepth(meta.containsKey("queue_depth") && meta.get("queue_depth") != null
                                ? ((Number) meta.get("queue_depth")).intValue() : null)
                    .skuZone   ((String) meta.get("sku_zone"))
                    .sessionSeq(meta.containsKey("session_seq") && meta.get("session_seq") != null
                                ? ((Number) meta.get("session_seq")).intValue() : null)
                    .ingestedAt(Instant.now())
                    .build());

                accepted++;
                byStore.merge(dto.getStoreId(), 1, Integer::sum);

            } catch (Exception e) {
                errors.add(dto.getEventId() + ": " + e.getMessage());
                rejected++;
            }
        }

        log.info("trace_id={} ingest accepted={} rejected={} duplicates={} latency_ms={}",
            traceId, accepted, rejected, duplicates, System.currentTimeMillis() - start);

        byStore.forEach((sid, cnt) ->
            ws.convertAndSend("/topic/events/" + sid, Map.of("storeId", sid, "newEvents", cnt)));

        return new IngestResponse(accepted, rejected, duplicates, errors);
    }
}
