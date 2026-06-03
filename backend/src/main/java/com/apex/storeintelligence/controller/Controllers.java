package com.apex.storeintelligence.controller;

import com.apex.storeintelligence.dto.Dtos.*;
import com.apex.storeintelligence.model.PosTransaction;
import com.apex.storeintelligence.repository.PosTransactionRepository;
import com.apex.storeintelligence.service.AnomalyService;
import com.apex.storeintelligence.service.EventIngestionService;
import com.apex.storeintelligence.service.FunnelService;
import com.apex.storeintelligence.service.HealthService;
import com.apex.storeintelligence.service.HeatmapService;
import com.apex.storeintelligence.service.MetricsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

// ── Event Ingest ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/events")
class EventController {
    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventIngestionService svc;
    EventController(EventIngestionService svc) { this.svc = svc; }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(
            @Valid @RequestBody IngestRequest req,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        String trace = tid != null ? tid : UUID.randomUUID().toString();
        IngestResponse r = svc.ingest(req, trace);
        if (r.getAccepted() == 0 && r.getRejected() > 0) return ResponseEntity.badRequest().body(r);
        if (r.getRejected() > 0) return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(r);
        return ResponseEntity.ok(r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> err(Exception e) {
        log.error("EventController: {}", e.getMessage());
        return ResponseEntity.status(503).body(Map.of("error","SERVICE_UNAVAILABLE","message",e.getMessage()));
    }
}

// ── Metrics ───────────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/stores")
class MetricsController {
    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private final MetricsService svc;
    MetricsController(MetricsService svc) { this.svc = svc; }

    @GetMapping("/{storeId}/metrics")
    public ResponseEntity<MetricsDto> get(@PathVariable String storeId,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        return ResponseEntity.ok(svc.getMetrics(storeId, tid != null ? tid : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> err(Exception e) {
        log.error("MetricsController: {}", e.getMessage());
        return ResponseEntity.status(503).body(Map.of("error","SERVICE_UNAVAILABLE","message",e.getMessage()));
    }
}

// ── Funnel ────────────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/stores")
class FunnelController {
    private final FunnelService svc;
    FunnelController(FunnelService svc) { this.svc = svc; }

    @GetMapping("/{storeId}/funnel")
    public ResponseEntity<FunnelDto> get(@PathVariable String storeId,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        return ResponseEntity.ok(svc.getFunnel(storeId, tid != null ? tid : UUID.randomUUID().toString()));
    }
}

// ── Heatmap ───────────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/stores")
class HeatmapController {
    private final HeatmapService svc;
    HeatmapController(HeatmapService svc) { this.svc = svc; }

    @GetMapping("/{storeId}/heatmap")
    public ResponseEntity<HeatmapDto> get(@PathVariable String storeId,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        return ResponseEntity.ok(svc.getHeatmap(storeId, tid != null ? tid : UUID.randomUUID().toString()));
    }
}

// ── Anomalies ─────────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/stores")
class AnomalyController {
    private final AnomalyService svc;
    AnomalyController(AnomalyService svc) { this.svc = svc; }

    @GetMapping("/{storeId}/anomalies")
    public ResponseEntity<List<AnomalyDto>> get(@PathVariable String storeId,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        return ResponseEntity.ok(svc.getAnomalies(storeId, tid != null ? tid : UUID.randomUUID().toString()));
    }
}

// ── Health ────────────────────────────────────────────────────────────────────
@RestController
class HealthController {
    private final HealthService svc;
    HealthController(HealthService svc) { this.svc = svc; }

    @GetMapping("/health")
    public ResponseEntity<HealthDto> get(
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        HealthDto h = svc.getHealth(tid != null ? tid : UUID.randomUUID().toString());
        return ResponseEntity.status("DOWN".equals(h.getStatus()) ? 503 : 200).body(h);
    }
}

// ── POS Ingest ────────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/api/v1/pos")
class PosController {
    private static final Logger log = LoggerFactory.getLogger(PosController.class);
    private final PosTransactionRepository posRepo;
    PosController(PosTransactionRepository posRepo) { this.posRepo = posRepo; }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String,Object>> ingest(
            @RequestBody PosIngestRequest req,
            @RequestHeader(value="X-Trace-Id", required=false) String tid) {
        String trace = tid != null ? tid : UUID.randomUUID().toString();
        int loaded = 0, skipped = 0;
        for (PosTransactionDto d : req.getTransactions()) {
            try {
                if (d.getOrderId() != null && posRepo.existsByOrderId(d.getOrderId())) { skipped++; continue; }
                String ts = d.getTimestamp();
                if (ts == null) { skipped++; continue; }
                if (!ts.endsWith("Z")) ts += "Z";
                posRepo.save(PosTransaction.builder()
                    .orderId(d.getOrderId()).storeId(d.getStoreId())
                    .transactionTime(Instant.parse(ts))
                    .basketValue(BigDecimal.valueOf(d.getBasketValue()))
                    .productId(d.getProductId()).brandName(d.getBrandName()).build());
                loaded++;
            } catch (Exception e) {
                log.warn("trace_id={} pos skip: {}", trace, e.getMessage());
                skipped++;
            }
        }
        log.info("trace_id={} pos/ingest loaded={} skipped={}", trace, loaded, skipped);
        return ResponseEntity.ok(Map.of("loaded", loaded, "skipped", skipped));
    }
}
