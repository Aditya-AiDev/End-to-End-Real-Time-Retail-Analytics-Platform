"""
emit.py — Event Schema + Batched HTTP POST
==========================================
TECHNIQUE : UUID-based idempotency + exponential-backoff retry + JSONL fallback
WHY       : Each event gets a UUID v4 event_id. Spring Boot deduplicates by it,
            so re-sending a batch is always safe. On API failure, events are
            written to a local JSONL file for later replay.
"""

import uuid, json, time, requests
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional, Dict, Any

VALID_TYPES = {"ENTRY","EXIT","ZONE_ENTER","ZONE_EXIT","ZONE_DWELL",
               "BILLING_QUEUE_JOIN","BILLING_QUEUE_ABANDON","REENTRY"}


def make_event(store_id, camera_id, visitor_id, event_type, timestamp,
               zone_id=None, dwell_ms=0, is_staff=False, confidence=0.8,
               queue_depth=None, sku_zone=None, session_seq=1, extra_meta=None) -> dict:
    assert event_type in VALID_TYPES, f"Bad event_type: {event_type}"
    if not timestamp or timestamp == "clip_end":
        timestamp = datetime.now(tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    elif "T" in timestamp and not timestamp.endswith("Z"):
        timestamp = timestamp.split(".")[0] + "Z"
    meta = {"queue_depth": queue_depth, "sku_zone": sku_zone, "session_seq": session_seq}
    if extra_meta: meta.update(extra_meta)
    return {"event_id": str(uuid.uuid4()), "store_id": store_id, "camera_id": camera_id,
            "visitor_id": visitor_id, "event_type": event_type, "timestamp": timestamp,
            "zone_id": zone_id, "dwell_ms": dwell_ms, "is_staff": bool(is_staff),
            "confidence": round(float(confidence), 4), "metadata": meta}


class EventEmitter:
    ENDPOINT  = "/api/v1/events/ingest"
    MAX_RETRY = 3

    def __init__(self, api_url, store_id, camera_id, output_path=None):
        self.api_url      = api_url.rstrip("/")
        self.store_id     = store_id
        self.camera_id    = camera_id
        self.total_emitted = 0
        self._f = None
        if output_path:
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            self._f = open(output_path, "a", encoding="utf-8")

    def _convert(self, raw: dict) -> Optional[dict]:
        et = raw.get("event_type", "").upper()
        if et not in VALID_TYPES: return None
        meta = raw.get("metadata", {}) or {}
        return make_event(
            store_id   = self.store_id, camera_id = self.camera_id,
            visitor_id = raw["visitor_id"], event_type = et,
            timestamp  = raw.get("timestamp",""), zone_id = raw.get("zone_id"),
            dwell_ms   = meta.get("dwell_ms", 0), is_staff = raw.get("is_staff", False),
            confidence = raw.get("confidence", 0.8),
            queue_depth= meta.get("queue_depth"), session_seq = raw.get("session_seq", 1),
            extra_meta = {k:v for k,v in meta.items() if k not in ("dwell_ms","queue_depth")}
        )

    def flush(self, raw_events: List[dict]):
        events = [e for e in (self._convert(r) for r in raw_events) if e]
        if not events: return
        if self._f:
            for e in events: self._f.write(json.dumps(e)+"\n")
            self._f.flush()
        self._post(events)
        self.total_emitted += len(events)

    def emit_single(self, raw): self.flush([raw])

    def _post(self, events):
        url, delay = self.api_url + self.ENDPOINT, 2
        for attempt in range(1, self.MAX_RETRY+1):
            try:
                r = requests.post(url, json={"events": events},
                                  headers={"Content-Type":"application/json"}, timeout=10)
                if r.status_code < 300: return
                print(f"[emit] HTTP {r.status_code}")
            except Exception as e:
                print(f"[emit] attempt {attempt} failed: {e}")
            if attempt < self.MAX_RETRY:
                time.sleep(delay); delay *= 2
        print(f"[emit] Failed to deliver {len(events)} events after {self.MAX_RETRY} retries")

    def close(self):
        if self._f: self._f.close()
