
# End-to-End Real-Time Retail Analytics & Computer Vision Platform

### Purplle Store Intelligence

---

## Table of Contents
1. [System Architecture](#1-system-architecture)
2. [Project Structure](#2-project-structure)
3. [Prerequisites](#3-prerequisites)
4. [How to Run — Step by Step](#4-how-to-run--step-by-step)
   - [Step 1 — Spring Boot Backend](#step-1--spring-boot-backend)
   - [Step 2 — React Frontend](#step-2--react-frontend)
   - [Step 3 — Python Pipeline](#step-3--python-pipeline)
   - [Step 4 — Verify Everything is Working](#step-4--verify-everything-is-working)
5. [CCTV Resource Folder](#5-cctv-resource-folder)
6. [API Reference](#6-api-reference)
7. [Event Schema](#7-event-schema)
8. [ML/CV Techniques](#8-mlcv-techniques)
9. [File Guide](#9-file-guide)

---

## 1. System Architecture

```
CCTV/footage/  (5 x .mp4 clips)
       |
       |  run.sh  (Git Bash / WSL)
       v
+------------------------------------------+
|         Python Pipeline                  |
|  detect.py      YOLOv8s  person detect   |
|  tracker.py     ByteTrack + ResNet ReID  |
|  zone_mapper.py pointPolygonTest zones   |
|  staff_detector HSV uniform classifier  |
|  emit.py        UUID batch HTTP POST     |
+----------------+-------------------------+
                 | POST /api/v1/events/ingest
                 v
+------------------------------------------+
|         Spring Boot API  :8080           |
|  EventIngestionService   idempotent      |
|  MetricsService          KPIs            |
|  FunnelService           4-stage funnel  |
|  HeatmapService          zone traffic    |
|  AnomalyService          60s scan        |
|  HealthService           feed lag        |
|  WebSocket STOMP         live push       |
|  H2 in-memory DB (dev)                   |
+----------------+-------------------------+
                 | REST + WebSocket
                 v
+------------------------------------------+
|         React Dashboard  :3000           |
|  MetricsCards   4 KPI cards              |
|  FunnelChart    Recharts bar chart       |
|  HeatmapView    colour bars 0-100        |
|  AnomalyPanel   CRITICAL/WARN/INFO       |
|  useStore hook  polling + WebSocket      |
+------------------------------------------+
```

---

## 2. Project Structure

```
E:\Perplle_Projrct_YOLO_java\
|
|-- CCTV\                          <-- All resource files
|   |-- footage\                   <-- Raw CCTV clips (input to pipeline)
|   |   |-- CAM_1.mp4              Entry / Exit camera
|   |   |-- CAM_2.mp4              Main floor camera
|   |   |-- CAM_3.mp4              Billing counter camera
|   |   |-- CAM_4.mp4              Floor camera 2
|   |   |-- CAM_5.mp4              Floor camera 3
|   |   `-- POS_transactions.csv   Auto-discovered by run.sh
|   |-- dataset\                   <-- Reference / schema files
|   |   |-- POS_transactions.csv   Original POS data
|   |   |-- sample_events.jsonl    Sample event schema
|   |   `-- Problem_Statement.pdf  Purplle PS3 challenge brief
|   |-- events_output\             <-- Pipeline writes JSONL here (auto-created)
|   `-- README.md
|
|-- pipeline\                      <-- Python CV/ML pipeline
|   |-- detect.py
|   |-- tracker.py
|   |-- zone_mapper.py
|   |-- staff_detector.py
|   |-- emit.py
|   |-- store_layout.json
|   |-- run.sh
|   `-- requirements.txt
|
|-- backend\                       <-- Spring Boot API
|   |-- pom.xml
|   `-- src\main\
|       |-- java\com\apex\storeintelligence\
|       |   |-- StoreIntelligenceApplication.java
|       |   |-- config\WebSocketConfig.java
|       |   |-- model\StoreEvent.java
|       |   |-- model\PosTransaction.java
|       |   |-- dto\Dtos.java
|       |   |-- repository\StoreEventRepository.java
|       |   |-- repository\PosTransactionRepository.java
|       |   |-- service\EventIngestionService.java
|       |   |-- service\MetricsService.java
|       |   |-- service\AnalyticsServices.java
|       |   `-- controller\Controllers.java
|       `-- resources\application.yml
|
|-- frontend\                      <-- React dashboard
|   |-- package.json
|   `-- src\
|       |-- services\api.js
|       |-- hooks\useStore.js
|       |-- components\MetricsCards.jsx
|       |-- components\FunnelChart.jsx
|       |-- components\HeatmapView.jsx
|       |-- components\AnomalyPanel.jsx
|       |-- pages\Dashboard.jsx
|       |-- App.js
|       `-- index.js
|
`-- README.md                      <-- This file
```

---

## 3. Prerequisites

Check what you already have installed on this machine:

| Tool | Required | Your Version |
|------|----------|-------------|
| Java JDK | 17+ | 21.0.1 |
| Maven | 3.8+ | 3.9.16 |
| Python | 3.8+ | 3.13.5 |
| pip | any | 26.1.2 |
| Node.js | 16+ | 22.17.0 |
| npm | 8+ | 11.16.0 |

### Python packages:
### by
```cmd
cd pipeline

pip install -r requirements.txt

pip install supervision
```

- torch 2.12.0
- ultralytics 8.4.60 (YOLOv8)
- supervision 0.28.0 (ByteTrack)
- opencv-python 4.13.0.92

### Install Maven (required for Spring Boot)

**Option A — Download manually:**
1. Go to https://maven.apache.org/download.cgi
2. Download `apache-maven-3.9.x-bin.zip`
3. Extract to `C:\maven`
4. Add `C:\maven\bin` to System Environment Variable `PATH`
5. Open a NEW command prompt and run: `mvn -version`

**Option B — Using Chocolatey (if installed):**
```cmd
choco install maven
```

**Option C — Using Scoop (if installed):**
```cmd
scoop install maven
```



## 4. How to Run — Step by Step

> Run each step in a SEPARATE terminal window. All 3 must be running at the same time.

---

### Step 1 — Spring Boot Backend

Open Terminal 1 (Command Prompt or PowerShell):
### 1
```cmd

cd backend

`````
### 2 give your maven directory which you unzip previously...
### like: "maven directory\bin\mvn.cmd" spring-boot:run

`````cmd

"C:\Users\adity\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run

```````
### or

`````cmd

mvn spring-boot:run

`````



### Step 2 — React Frontend

Open Terminal 2 (Command Prompt or PowerShell):

```cmd

cd frontend
npm install
npm start

```



### Step 3 — Python Pipeline


> Run this AFTER Step 1 (backend) is running.

```cmd

cd pipeline

pip install -r requirements.txt

pip install supervision requests


python detect.py --video "../CCTV/footage/CAM_1.mp4" --store-id ST1076 --camera-id cam1 --layout store_layout.json --api-url http://localhost:8080 --output "../CCTV/events_output/cam1_events.jsonl"

python detect.py --video "../CCTV/footage/CAM_2.mp4" --store-id ST1076 --camera-id CAM2 --layout store_layout.json --api-url http://localhost:8080 --output "../CCTV/events_output/CAM2_events.jsonl"

python detect.py --video "../CCTV/footage/CAM_3.mp4" --store-id ST1076 --camera-id CAM3 --layout store_layout.json --api-url http://localhost:8080 --output "../CCTV/events_output/CAM2_events.jsonl"

python detect.py --video "../CCTV/footage/CAM_4.mp4" --store-id ST1076 --camera-id CAM4 --layout store_layout.json --api-url http://localhost:8080 --output "../CCTV/events_output/CAM2_events.jsonl"

python detect.py --video "../CCTV/footage/CAM_5.mp4" --store-id ST1076 --camera-id PURPLLE_MUM_1076_CAM6 --layout store_layout.json --api-url http://localhost:8080 --output "../CCTV/events_output/billing_events.jsonl"


python replay_events.py

```

### Step 4 — Verify Everything is Working

After all 3 steps are running, check these URLs:

| URL | What you should see |
|-----|---------------------|
| http://localhost:8080/health | `{"status":"UP", ...}` |
| http://localhost:8080/api/v1/stores/ST1076/metrics | visitor counts, conversion rate |
| http://localhost:8080/api/v1/stores/ST1076/funnel | 4-stage funnel with drop-off % |
| http://localhost:8080/api/v1/stores/ST1076/heatmap | zone visit counts |
| http://localhost:8080/api/v1/stores/ST1076/anomalies | any detected anomalies |
| http://localhost:3000 | Live React dashboard with real data |
| http://localhost:8080/h2-console | Browse the DB (JDBC: jdbc:h2:mem:storedb) |

---

## 5. CCTV Resource Folder

```
CCTV/footage/       Input clips and POS data for the pipeline
CCTV/dataset/       Original source files (unchanged copies)
CCTV/events_output/ Pipeline output — one JSONL per camera after run.sh
```

### Camera to Zone Mapping

| File | Camera ID | Zones Covered |
|------|-----------|---------------|
| CAM_1.mp4 | cam1 | Entry / Exit threshold |
| CAM_2.mp4 | CAM2 | Left Shelf (Skincare), Right Shelf (Makeup) |
| CAM_3.mp4 | PURPLLE_MUM_1076_CAM6 | Billing Counter Queue |
| CAM_4.mp4 | CAM_FLOOR_02 | Additional floor area |
| CAM_5.mp4 | CAM_FLOOR_03 | Additional floor area |

Files were renamed (spaces removed) from the originals for shell script compatibility:
- `CAM 1.mp4` → `CAM_1.mp4`
- `POS - sample transactionsb1e826f.csv` → `POS_transactions.csv`
- `sample_eventsbe42122.jsonl` → `sample_events.jsonl`
- `Purplle Tech Challenge 2026 _ Round 2 Problem Statement480e74e.pdf` → `Problem_Statement.pdf`

---

## 6. API Reference

| Method | Endpoint | Body / Params | Description |
|--------|----------|---------------|-------------|
| POST | `/api/v1/events/ingest` | `{events: [...]}` batch 1-500 | Ingest CCTV events. Idempotent by event_id. Returns accepted/rejected/duplicates counts. |
| POST | `/api/v1/pos/ingest` | `{transactions: [...]}` | Load POS CSV data. Idempotent by order_id. |
| GET | `/api/v1/stores/{id}/metrics` | — | Visitors, conversion rate, avg dwell, queue depth, abandonment rate. Today's window. |
| GET | `/api/v1/stores/{id}/funnel` | — | Entry → Zone → Billing → Purchase with drop-off % at each stage. |
| GET | `/api/v1/stores/{id}/heatmap` | — | Zone visit count + avg dwell, normalized 0-100. |
| GET | `/api/v1/stores/{id}/anomalies` | — | Active anomalies. Refreshed every 60 seconds. |
| GET | `/health` | — | DB status + per-store feed lag. Used by ops monitoring. |
| WS | `/ws` (SockJS/STOMP) | — | Subscribe to `/topic/events/{storeId}` and `/topic/anomalies/{storeId}` |

**Response codes:**
- `200` — success
- `207` — partial success (some events rejected, some accepted)
- `400` — all events in batch rejected
- `503` — DB unavailable

---

## 7. Event Schema

All 8 event types share this schema. Nullable fields are omitted where not applicable.

```json
{
  "event_id":   "550e8400-e29b-41d4-a716-446655440000",
  "store_id":   "ST1076",
  "camera_id":  "CAM2",
  "visitor_id": "VIS_00001",
  "event_type": "ENTRY",
  "timestamp":  "2026-03-08T18:10:05Z",
  "zone_id":    null,
  "dwell_ms":   0,
  "is_staff":   false,
  "confidence": 0.87,
  "metadata": {
    "queue_depth": null,
    "sku_zone":    null,
    "session_seq": 1
  }
}
```

**event_type values:**
```
ENTRY                  Person crosses entry line inward
EXIT                   Person crosses entry line outward
ZONE_ENTER             Person enters a named zone polygon
ZONE_EXIT              Person leaves a named zone polygon (includes dwell_ms)
ZONE_DWELL             Periodic dwell heartbeat every 30 seconds
BILLING_QUEUE_JOIN     Person enters billing queue zone
BILLING_QUEUE_ABANDON  Person leaves billing queue without completing
REENTRY                Same visitor_id seen again after EXIT (Re-ID match)
```

---

## 8. ML/CV Techniques

| Component | Technique | Why This Was Chosen |
|-----------|-----------|---------------------|
| Person Detection | **YOLOv8s** (pretrained COCO class-0) | Single-pass detector, ~30fps on GPU. 'small' variant balances speed and accuracy for crowded scenes. Already installed on this machine. |
| Multi-Object Tracking | **ByteTrack** (supervision 0.28.0) | Unlike SORT, uses BOTH high and low confidence detections in two-stage matching. People partially hidden in billing queue keep their track ID instead of getting a new one on every occlusion. |
| Re-Identification | **ResNet-50** (torchvision, fc removed → 2048-dim embedding) + cosine similarity threshold 0.72 | Links a re-entering visitor to their original visitor_id without face recognition. Cosine similarity works on L2-normalised feature vectors. |
| Zone Assignment | **cv2.pointPolygonTest** | Point-in-polygon, O(n). Polygons stored in normalised [0,1] space so they work at any video resolution. First-match wins for overlapping zones. |
| Staff Detection | **HSV color histogram** on upper-body ROI | No labelled training data needed. Staff wear consistent uniform colors. Configure via staff_profile.json. If >= 20% of upper-body pixels match uniform HSV range → staff. |
| Conversion Correlation | **5-minute time-window join** (billing zone presence ↔ POS transaction time) | POS data has no customer_id. Best-effort: any visitor in BILLING zone in the 5 min before a transaction is counted as converted. Standard retail analytics approach. |
| Anomaly Detection | **Threshold rules** + **7-day rolling average baseline** | Queue spike: hard thresholds (8=WARN, 15=CRITICAL). Conversion drop: today's rate < 70% of 7-day average. Dead zone: zone visited in last 4h but not in last 30 min. Stale feed: no events for > 10 min. |

---

## 9. File Guide

### pipeline/

| File | Role |
|------|------|
| `detect.py` | Main loop. Opens video, runs YOLOv8 every 3rd frame, calls tracker, zone_mapper, staff_detector, emitter. Converts frame index to ISO timestamp. |
| `tracker.py` | ByteTrack wrapper. Maintains Track objects with centroid history. Y-velocity sign determines ENTRY vs EXIT. Appearance gallery for Re-ID across sessions. |
| `zone_mapper.py` | Loads polygon definitions from store_layout.json. Maps pixel centroid to zone_id using pointPolygonTest. Normalised coordinates are scale-independent. |
| `staff_detector.py` | HSV uniform classifier. Top 50% of bounding box = upper body. Configurable HSV ranges. build_profile() converts color name to HSV for calibration. |
| `emit.py` | Validates event type, generates UUID event_id, builds canonical schema dict, batches and POSTs to Spring Boot with exponential retry. Falls back to JSONL on failure. |
| `store_layout.json` | Zone polygon definitions for every camera of every store. Polygons in normalised [0,1] x/y space. |
| `run.sh` | Orchestration. Maps CAM_1..5 to camera IDs, runs detect.py in parallel (max 3), loads POS CSV. Defaults to ../CCTV/footage. |
| `requirements.txt` | Python dependencies. Most already installed on this machine. |

### backend/

| File | Role |
|------|------|
| `StoreIntelligenceApplication.java` | Entry point. @EnableScheduling activates the 60s anomaly scan. |
| `config/WebSocketConfig.java` | STOMP over SockJS. In-memory broker for /topic. CorsConfig allows port 3000. |
| `model/StoreEvent.java` | Flat JPA entity. One table for all 8 event types. Unique index on event_id enforces idempotency at DB level. |
| `model/PosTransaction.java` | POS transaction entity. Separate table from events. |
| `dto/Dtos.java` | All request/response DTOs. @Pattern validates event_type. @Size limits batch to 500. |
| `repository/StoreEventRepository.java` | All analytics JPQL queries. Works on H2 and PostgreSQL unchanged. |
| `repository/PosTransactionRepository.java` | POS queries. countTransactions for funnel purchase step. |
| `service/EventIngestionService.java` | Per-event save (not saveAll) for partial success. existsByEventId for idempotency. WebSocket broadcast after each batch. |
| `service/MetricsService.java` | Queries today's window (midnight UTC → now). Conversion rate via 5-min time window join. Zero-safe (never returns NaN). |
| `service/AnalyticsServices.java` | Contains FunnelService, HeatmapService, AnomalyService, HealthService. AnomalyService runs @Scheduled every 60s and stores results in-memory. |
| `controller/Controllers.java` | All 7 REST controllers. Optional X-Trace-Id header. Structured JSON error responses. |
| `src/main/resources/application.yml` | H2 config for dev profile. PostgreSQL config activated by SPRING_PROFILES_ACTIVE=prod. |

### frontend/

| File | Role |
|------|------|
| `services/api.js` | Axios client. Interceptor auto-injects X-Trace-Id UUID on every request. |
| `hooks/useStore.js` | Custom React hook. Fetches all 4 endpoints in parallel. Subscribes to WebSocket topics. 30-second fallback poll. Toast notifications for CRITICAL anomalies. |
| `components/MetricsCards.jsx` | 4 KPI cards. Colour-coded green/amber/red by threshold values. |
| `components/FunnelChart.jsx` | Recharts horizontal BarChart. Drop-off % labels on each stage. |
| `components/HeatmapView.jsx` | CSS colour bars green/amber/red by normalised score 0-100. dataConfidence badge when < 20 sessions. |
| `components/AnomalyPanel.jsx` | Alert cards sorted CRITICAL → WARN → INFO. Each shows description + suggested action. |
| `pages/Dashboard.jsx` | Main layout. Store selector dropdown re-triggers useStore. Shows all panels in 3-column grid. |

### CCTV/

| Path | Contents |
|------|----------|
| `CCTV/footage/` | 5 renamed MP4 clips + POS CSV (pipeline input) |
| `CCTV/dataset/` | Original reference files (POS CSV, sample events JSONL, problem statement PDF) |
| `CCTV/events_output/` | Pipeline output JSONL files written here after run.sh |

---






