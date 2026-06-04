# Purplle Store Intelligence — System Design

## Overview

The Purplle Store Intelligence system is an end-to-end retail analytics platform that processes CCTV footage to extract customer behavior insights, generate KPIs, and provide anomaly detection for store operations.

## Architecture Components

### 1. **Python Pipeline** (CV/ML Layer)
**Purpose**: Extract and process real-world events from CCTV footage

**Key Components**:
- **YOLOv8s Object Detection**: Detects persons in video frames
- **ByteTrack + ResNet ReID**: Maintains consistent tracking IDs across frames and cameras
- **Zone Mapper**: Uses `pointPolygonTest()` to track customer movement through defined zones (entry, exit, shelves, displays, billing)
- **Staff Detector**: HSV-based color classifier for uniform detection
- **Event Emitter**: Batches and HTTP POSTs events to backend every 1-5 seconds

**Output**: JSONL event stream with entry/exit/zone_entered/zone_exited/queue_* events

### 2. **Spring Boot Backend** (API & Processing Layer)
**Purpose**: Ingest, validate, deduplicate, and process events; compute analytics in real-time

**Key Services**:
- **EventIngestionService**: Idempotent event ingestion with UUID-based deduplication
- **MetricsService**: Computes KPIs (footfall, conversion, dwell time, queue metrics)
- **FunnelService**: Tracks 4-stage funnel (Entry → Shelf → Billing → Exit)
- **HeatmapService**: Aggregates zone traffic patterns over time windows
- **AnomalyService**: 60-second scan for crowd anomalies, queue abandonment
- **HealthService**: Monitors pipeline feed lag and data freshness
- **WebSocket STOMP**: Pushes live updates to frontend

**Database**: H2 in-memory (development) with schema for events, metrics, anomalies

**API**: RESTful endpoints with idempotent POST operations, real-time WebSocket push

### 3. **React Frontend** (Visualization Layer)
**Purpose**: Real-time dashboard for store managers and analytics teams

**Key Components**:
- **MetricsCards**: Display 4 KPI cards (footfall, conversion, avg dwell, queue wait)
- **FunnelChart**: Recharts bar chart showing stage-wise funnel drop-off
- **HeatmapView**: Zone heatmap with color intensity mapping (0-100 traffic level)
- **AnomalyPanel**: Real-time alerts (CRITICAL/WARN/INFO severity)
- **useStore Hook**: Combined polling + WebSocket for real-time updates

## Data Flow

```
CCTV Footage
    ↓
Python Pipeline (detect.py, tracker.py, zone_mapper.py, staff_detector.py)
    ↓ (JSONL via HTTP POST)
Spring Boot Backend (/api/v1/events/ingest)
    ↓ (Event Ingestion Service)
Store (PosTransaction, StoreEvent tables)
    ↓ (MetricsService, FunnelService, AnomalyService)
Computed Metrics & Anomalies
    ↓ (REST API + WebSocket STOMP)
React Dashboard (Real-time visualization)
```

## Event Schema

Events are produced in JSONL format with varying fields based on event type:

### Entry/Exit Events
```json
{
  "event_type": "entry|exit",
  "id_token": "ID_60001",
  "store_code": "store_1076",
  "camera_id": "cam1",
  "event_timestamp": "2026-03-08T18:10:05.120000",
  "is_staff": false,
  "gender_pred": "F|M",
  "age_pred": 28,
  "age_bucket": "25-34|18-24|35-44|45-54|55+",
  "is_face_hidden": false,
  "group_id": "G_10|null",
  "group_size": 2|null
}
```

### Zone Events
```json
{
  "event_type": "zone_entered|zone_exited",
  "track_id": 101,
  "store_id": "ST1076",
  "camera_id": "CAM2",
  "zone_id": "PURPLLE_MUM_1076_Z01",
  "zone_name": "Left Shelf",
  "zone_type": "SHELF|DISPLAY|BILLING|ENTRY",
  "is_revenue_zone": "Yes",
  "event_time": "2026-03-08T18:10:45.280000",
  "zone_hotspot_x": 412.6,
  "zone_hotspot_y": 238.4,
  "gender": "F",
  "age": 28,
  "age_bucket": "25-34"
}
```

### Queue Events
```json
{
  "queue_event_id": "cfd8e3c5-7aa0-4ea3-9b59-692d50da8308",
  "event_type": "queue_completed|queue_abandoned",
  "track_id": 102,
  "store_id": "ST1076",
  "camera_id": "PURPLLE_MUM_1076_CAM6",
  "zone_id": "PURPLLE_MUM_1076_Z_BILLING_01",
  "zone_name": "Billing Counter Queue",
  "queue_join_ts": "2026-03-08T18:13:05.080000",
  "queue_served_ts": "2026-03-08T18:13:13.240000",
  "queue_exit_ts": "2026-03-08T18:15:31.840000",
  "wait_seconds": 8,
  "queue_position_at_join": 2,
  "abandoned": false
}
```

## AI-Assisted Decisions

### 1. **Model Selection: YOLOv8s**
- **Why YOLOv8s?**
  - Lightweight (small) variant for inference speed on edge/CPU
  - Pre-trained COCO person class with 80%+ mAP
  - Real-time detection at 30 FPS on standard hardware
  - Trade-off: Slightly lower accuracy vs YOLOv8m/l, but acceptable for retail use

- **Alternative Considered**: Faster R-CNN
  - Higher accuracy but 3-5x slower inference
  - Rejected due to real-time processing requirements

### 2. **Tracking: ByteTrack + ResNet ReID**
- **Why ByteTrack?**
  - Multi-object tracking without explicit person re-identification
  - Robust to occlusion and ID switches
  - Low computational overhead
  - Maintains track IDs across frame drops

- **Why ResNet ReID?**
  - Learned feature embeddings for person appearance matching
  - Helps ByteTrack associate detections across camera views
  - Pre-trained on large-scale pedestrian ReID datasets

### 3. **Zone Definition: Point-in-Polygon (pip)**
- **Why Geometric Approach?**
  - No ML needed; rules-based ensures determinism
  - Fast computation (O(n) per point test)
  - Supports arbitrary zone shapes (rectangles, polygons, circles)
  - Easy to reconfigure zones for store layout changes
  - Alternative (ML-based segmentation) rejected for lack of labeled data

### 4. **Age/Gender Prediction**
- **Model**: Pre-trained CNN-based classifiers or YOLO auxiliary branches
- **Rationale**:
  - Provides demographic segmentation for KPIs
  - Useful for targeted store insights
  - Trade-off: Prediction accuracy ~75-80%; biases possible
  - Mitigated by using age buckets instead of raw values

### 5. **Staff Detection: HSV Uniform Classifier**
- **Why HSV + Color Thresholding?**
  - No expensive training required
  - Fast inference (pixel-level operations)
  - Purplle staff uniforms have consistent color profiles
  - Simple calibration per store

- **Alternative Rejected**: Deep learning classifier
  - Would require labeled staff/non-staff dataset
  - Over-engineered for color-based problem

### 6. **Event Deduplication: UUID-based Idempotency**
- **Strategy**: Each Python pipeline event carries UUID; backend rejects duplicates by ID
- **Rationale**:
  - Handles network retries and out-of-order delivery
  - Enables safe HTTP POST without transactions
  - Simplifies distributed processing

### 7. **Real-Time Analytics Window**
- **60-second sliding window** for anomaly detection and heatmap aggregation
- **Rationale**:
  - Balanced latency vs accuracy
  - Captures transient crowd events
  - Manageable memory footprint for in-memory DB

### 8. **API First vs Batch Processing**
- **Chose**: Event-driven with in-memory state
- **Rationale**:
  - Real-time dashboard requirements
  - Immediate anomaly alerting
  - Batch processing rejected due to 60s latency requirement

## Performance Considerations

| Component | Metric | Target |
|-----------|--------|--------|
| YOLO Detection | FPS | 30+ (1080p) |
| ByteTrack | FPS | 100+ (tracking only) |
| Zone Pipeline | Events/sec | 100+ |
| Backend Ingestion | Events/sec | 1000+ (Spring Boot) |
| WebSocket Push | Latency | <100ms |
| Frontend Update | Refresh Rate | 1-2 sec (polling) |

## Scalability & Future Enhancements

1. **Multi-Store Aggregation**: Extend metrics to chain-level insights
2. **Predictive Analytics**: Forecast peak hours, convert propensity models
3. **Computer Vision Improvements**: Fine-tuned models for Purplle's specific store layouts
4. **Message Queue**: Add Kafka/RabbitMQ for event buffering and fault tolerance
5. **Distributed Analytics**: Migrate from H2 to PostgreSQL/Cassandra for persistence
6. **Mobile App**: Push critical alerts to store manager mobile devices
