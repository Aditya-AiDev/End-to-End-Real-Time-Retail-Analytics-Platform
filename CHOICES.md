# Architectural & Technical Choices

## Overview
This document details the key technical decisions made during the design and implementation of the Purplle Store Intelligence system, including rationale, trade-offs, and alternatives considered.

---

## 1. Model Selection

### Object Detection: YOLOv8s (Small)
**Choice**: YOLOv8s for person detection in CCTV footage

**Rationale**:
- **Speed**: 30+ FPS inference on CPU/standard GPU
- **Accuracy**: ~80% mAP on COCO person class
- **Size**: Lightweight (~11MB model), fits edge deployment
- **Ecosystem**: Mature, well-documented, large community support

**Trade-offs**:
- Slightly lower accuracy than YOLOv8m/l
- Not trained on retail-specific person attributes (pose, activity)

**Alternatives Considered**:
| Alternative | Pros | Cons | Verdict |
|---|---|---|---|
| Faster R-CNN | Higher accuracy (85%+ mAP) | 3-5x slower, heavier model | Rejected |
| EfficientDet | Tunable speed-accuracy trade-off | Less community adoption | Rejected |
| Custom Fine-tuned YOLOv8 | Optimized for Purplle stores | Requires labeled dataset (~1000 images) | Future enhancement |

---

### Re-identification: ResNet50 + ByteTrack
**Choice**: Combined multi-object tracking with learned embeddings

**Rationale**:
- **ByteTrack** ensures stable track IDs across frames
- **ResNet50 ReID** provides appearance-based association across cameras
- Handles multi-camera scenarios (e.g., customer enters CAM1, exits CAM2)
- No explicit manual ID assignment needed

**Alternative: Simple Centroid Tracking**
- Rejected because it fails on occlusions and camera transitions
- Lack of person re-identification across cameras

**Alternative: Deep SORT**
- Similar to ByteTrack but more computationally expensive
- ByteTrack chosen for real-time requirements

---

### Age/Gender Prediction
**Choice**: Pre-trained CNN classifiers (or YOLO auxiliary heads)

**Rationale**:
- Provides demographic insights for KPI segmentation
- Pre-trained models available (no training required)
- Useful for targeted marketing analysis

**Limitations & Mitigations**:
| Issue | Mitigation |
|-------|-----------|
| Prediction bias (esp. for non-Western faces) | Use age buckets instead of exact values; acknowledge limitations in UI |
| Accuracy ~75% for age, ~85% for gender | Don't use for critical decisions; advisory only |
| Privacy implications | Don't store/transmit face images; process on-device |

---

### Staff Uniform Detection: HSV Color Classifier
**Choice**: Rule-based color thresholding in HSV color space

**Rationale**:
- Fast inference (no neural network)
- Purplle uniforms have consistent color (magenta/pink)
- Easy per-store calibration
- No training data required

**Calibration Process**:
1. Sample 50 staff members and 50 customers in frame
2. Extract bounding box regions, convert to HSV
3. Set thresholds: `H_min, H_max, S_min, V_min` for uniform color
4. Flag detections within thresholds as staff

**Alternative: CNN Classifier**
- Would require labeled staff/customer dataset
- Over-engineered for color-based problem
- Rejected due to data scarcity and latency concerns

---

## 2. Schema Design

### Event-Driven Architecture
**Choice**: JSONL-based events with multiple event types

**Structure**:
```
Entry/Exit Events:
- id_token (unique customer identifier per visit)
- demographics (age, gender, group info)
- timestamp

Zone Events:
- track_id (stable within session)
- zone_id, zone_type (shelf, display, billing)
- dwell time (implicit from zone_entered → zone_exited)

Queue Events:
- queue_join_ts, queue_served_ts, queue_exit_ts
- wait_seconds, abandoned flag
```

**Rationale**:
- Supports multiple event types without schema explosion
- Flexible for future event types (e.g., product interactions, hand movements)
- JSONL format allows streaming, easy logging

**Alternative: Flat Schema (All-in-one JSON)**
- Simpler to parse but creates NULLs for irrelevant fields
- Rejected for flexibility

**Alternative: Relational (SQL Tables)**
- Better for joins and aggregations
- Rejected for initial rapid development; H2 in-memory handles current volumes

---

### Deduplication Strategy: UUID-based Idempotency
**Choice**: Each event carries `event_id` (UUID); backend tracks seen IDs

**Rationale**:
- Handles network retries without duplicate processing
- Enables safe HTTP POST (idempotent)
- No external consensus protocol needed (e.g., 2-phase commit)

**Implementation**:
```
Python Pipeline: Generates UUID for each event
HTTP POST: Retryable without side effects
Spring Boot Backend: Checks `event_id` in persistence, rejects duplicates
```

**Alternative: Sequence Numbers**
- Simpler to generate but fails if pipeline restarts
- UUID + server-side dedup more robust

---

### Zone Design: Polygon-based Regions
**Choice**: Store layout as JSON with zone definitions (polygon vertices)

**Example (`store_layout.json`)**:
```json
{
  "zones": [
    {
      "zone_id": "PURPLLE_MUM_1076_Z01",
      "zone_name": "Left Shelf",
      "zone_type": "SHELF",
      "polygon": [[x1, y1], [x2, y2], [x3, y3], [x4, y4]],
      "camera_id": "CAM2",
      "is_revenue_zone": true
    }
  ]
}
```

**Point-in-Polygon Test** (via OpenCV):
- **Function**: `cv2.pointPolygonTest(polygon, (x, y))`
- **Result**: Positive (inside), negative (outside), zero (on boundary)
- **Computation**: O(n) per test, negligible overhead

**Rationale**:
- Supports arbitrary zone shapes
- No ML training required
- Deterministic (same input → same output always)
- Easy reconfiguration for store layout changes

**Alternative: Image-based Segmentation**
- ML model to classify pixels as zone types
- Requires labeled training data (expensive)
- Rejected for MVP phase

---

## 3. API Architecture

### RESTful HTTP + WebSocket STOMP
**Choice**: Hybrid approach for event ingestion and real-time updates

**Event Ingestion**:
```
POST /api/v1/events/ingest
Content-Type: application/json
{
  "events": [
    { event_type: "entry", ... },
    { event_type: "zone_entered", ... }
  ]
}
```
- Batch events (5-20 per request) to reduce network overhead
- Idempotent: Retry-safe via UUID deduplication

**Real-Time Metrics**:
```
WebSocket STOMP Topics:
- /topic/metrics           (KPIs: footfall, conversion, etc.)
- /topic/anomalies         (Alerts: crowd, queue abandonment)
- /topic/heatmap           (Zone traffic updates)
```
- Publish interval: 1-2 seconds
- Push-based eliminates polling latency

**Rationale**:
- REST for ingestion (stateless, scalable)
- WebSocket for real-time dashboard (low latency, bi-directional)
- STOMP protocol simplifies message routing on client side

**Alternative: gRPC for Ingestion**
- Lower latency (~10ms vs ~50ms REST)
- Rejected because Python pipeline uses HTTP; adding gRPC complexity
- REST sufficient for current throughput (~100 events/sec)

**Alternative: GraphQL Subscription**
- Flexible query language for frontend
- Rejected for MVP; REST + WebSocket simpler to implement

---

### Computational Layer: Spring Boot Microservices
**Choice**: Single Spring Boot monolith with service layer (EventIngestionService, MetricsService, etc.)

**Rationale**:
- Simpler deployment and operational management
- Shared in-memory state for fast inter-service communication
- H2 database sufficient for current data volumes

**Future Migration**: Event Sourcing + CQRS
- Separate write (event ingestion) from read (metrics computation)
- Kafka as event bus for fault tolerance
- Read replicas for analytics queries

---

## 4. Persistence & State Management

### H2 In-Memory Database (Development)
**Choice**: H2 for rapid development and testing

**Schema**:
```sql
PosTransaction:
  - transaction_id (PK)
  - store_id, timestamp
  - customer_id, amount
  - (linked to exit events for conversion tracking)

StoreEvent:
  - event_id (PK, UUID)
  - event_type (entry, exit, zone_entered, etc.)
  - track_id, store_id, camera_id
  - demographics (age, gender, group_size)
  - timestamps

Metrics (derived):
  - metric_id (PK)
  - store_id, metric_type (footfall, conversion, dwell_time)
  - time_bucket (60-sec window)
  - value

Anomalies:
  - anomaly_id (PK)
  - store_id, anomaly_type (crowd, queue_abandon)
  - severity (CRITICAL, WARN, INFO)
  - timestamp, description
```

**Rationale**:
- Zero setup (embedded database)
- Fast for small-to-medium data volumes (~10MB/day)
- Easy to reset state during development

**Production Considerations**:
- Migrate to PostgreSQL (durable, distributed)
- Add data retention policy (compress/delete events >30 days)
- Separate analytics DB (read replica) for heavy queries

---

## 5. Frontend Architecture

### React with Polling + WebSocket Hybrid
**Choice**: Combined real-time update strategy

**Polling** (fallback):
```javascript
useEffect(() => {
  const interval = setInterval(() => {
    fetch('/api/v1/metrics')
  }, 2000); // 2-sec polling
  return () => clearInterval(interval);
}, []);
```

**WebSocket** (primary):
```javascript
useEffect(() => {
  const stompClient = new StompJs.Client({...});
  stompClient.subscribe('/topic/metrics', onMetricsUpdate);
}, []);
```

**Rationale**:
- WebSocket for latency-sensitive updates (anomalies, queue alerts)
- Polling fallback for browsers/networks that don't support WebSocket
- Combined approach ensures reliability

**Component Architecture**:
- **useStore Hook**: Centralized state management (Redux alternative)
- **MetricsCards**: Pure components, minimal re-renders
- **FunnelChart, HeatmapView**: Recharts library for charting

**Alternative: Redux**
- More boilerplate for simple state
- Rejected for MVP; useStore hook sufficient

---

## 6. Data Quality & Monitoring

### Anomaly Detection: 60-Second Sliding Window
**Choice**: Real-time aggregation and threshold-based alerting

**Anomalies Detected**:
1. **Crowd Alert**: Footfall > 50 in 60-sec window → CRITICAL
2. **Queue Abandonment**: >30% queue abandon rate → WARNING
3. **Zone Congestion**: >80 persons in single zone → WARNING
4. **Feed Lag**: Backend timestamp - event timestamp > 30s → INFO (health check)

**Rationale**:
- 60 seconds balances responsiveness vs noise
- Simple thresholds easy to tune per store
- No complex ML required

**Future Enhancement**: Anomalies as ML models
- Learn baseline patterns per time-of-day, day-of-week
- Threshold = mean + 2*std_dev (anomaly if exceeds)
- Requires 2-4 weeks of historical data

---

### Health Monitoring
**HealthService** tracks:
- Pipeline event lag (now - event_timestamp)
- Backend processing latency
- WebSocket connection count
- Database disk usage

**Endpoint**: `GET /api/v1/health`
```json
{
  "status": "UP",
  "pipeline_lag_ms": 45,
  "events_processed_24h": 125000,
  "db_size_mb": 450
}
```

---

## 7. Deployment & DevOps

### Containerization (Docker) + Local Development
**Choice**: Docker Compose for local stack

**Services**:
- Backend (Spring Boot :8080)
- Frontend (React :3000)
- H2 embedded (no separate container)
- Python pipeline (local or Docker)

**Rationale**:
- Consistent environment across dev, test, prod
- Easy onboarding for new developers
- Simplifies CI/CD

**Production Deployment**:
- Kubernetes for orchestration
- Persistent PostgreSQL (managed cloud service)
- Redis for session/cache layer (future)

---

## 8. Security Considerations

### Privacy
- **No face images stored**: Process on-device, discard after classification
- **GDPR Compliance**: Retention policy; delete customer data after 30 days
- **IP Camera Security**: Use RTSP over VPN; restrict pipeline network access

### API Security
- **API Key Authentication**: Simple key header for pipeline ingestion
- **CORS**: Allow only React frontend origin
- **Rate Limiting**: 1000 events/sec per store_id (DDoS protection)

### Future: OAuth2 + Role-Based Access
- Admin users: Full system access
- Store managers: View metrics only
- Analytics team: Export data, configure alerts

---

## 9. Lessons Learned & Trade-offs

| Decision | Best Case | Worst Case | Mitigation |
|----------|-----------|-----------|-----------|
| YOLOv8s | Fast, works well | Misses small persons | Add post-processing filters |
| HSV uniform detection | Fast calibration | Fails with lighting changes | Periodic re-calibration |
| H2 in-memory | Zero setup | Data loss on restart | Implement checkpoint/dump |
| 60-sec window | Responsive alerts | Noise during peak hours | Adaptive thresholds |
| Polling + WebSocket | Robust | Extra network overhead | Optimize polling frequency |

---

## 10. Summary: Design Principles

1. **Pragmatism**: Choose simple, proven solutions (YOLO, REST, H2) over cutting-edge complexity
2. **Observability**: Monitor everything (lag, errors, anomalies) for quick debugging
3. **Scalability Path**: Design for gradual migration (H2 → PostgreSQL, REST → gRPC)
4. **Flexibility**: Support store layout reconfiguration without code changes (JSON zones)
5. **Fairness**: Acknowledge ML model limitations; use outputs advisorily, not decisively
