"""
detect.py — YOLOv8 + ByteTrack Detection Pipeline
===================================================
TECHNIQUE : YOLOv8s  (You Only Look Once v8 small)
WHY YOLOv8 : Pretrained on COCO (class-0 = person). Single-pass detector that
             runs at ~30 fps on a mid-range GPU. 'small' variant balances speed
             and accuracy better than 'nano' for crowded retail scenes.

TECHNIQUE : ByteTrack (via supervision library)
WHY ByteTrack : Unlike SORT (drops low-confidence detections), ByteTrack keeps
                partially-occluded tracks alive using a second association pass.
                This is critical in queues where people overlap.

FLOW:
  1. Load clip → iterate frames (every PROCESS_EVERY_N for speed)
  2. YOLOv8 detects person bounding boxes
  3. ByteTrack assigns stable track IDs across frames
  4. ZoneMapper checks which zone each centroid falls in
  5. StaffDetector flags uniform-wearing people
  6. EventEmitter ships events to Spring Boot API
"""

import cv2
import argparse
from pathlib import Path
from datetime import datetime, timezone, timedelta

from ultralytics import YOLO

from tracker import VisitorTracker
from zone_mapper import ZoneMapper
from staff_detector import StaffDetector
from emit import EventEmitter

YOLO_MODEL      = "yolov8s.pt"
CONF_THRESHOLD  = 0.35
IOU_THRESHOLD   = 0.45
PROCESS_EVERY_N = 3          # process every 3rd frame → ~5 fps effective on 15fps clip


def parse_clip_start(clip_path: str) -> datetime:
    name = Path(clip_path).stem
    try:
        ts = name.split("_")[-1]
        return datetime.strptime(ts, "%Y%m%dT%H%M%SZ").replace(tzinfo=timezone.utc)
    except Exception:
        return datetime(2026, 3, 8, 18, 0, 0, tzinfo=timezone.utc)


def frame_ts(clip_start: datetime, frame_idx: int, fps: float) -> str:
    return (clip_start + timedelta(seconds=frame_idx / fps)).strftime("%Y-%m-%dT%H:%M:%SZ")


def process_clip(video_path, store_id, camera_id, layout_path, api_url, output_jsonl=None):
    print(f"[detect] {video_path}  store={store_id}  cam={camera_id}")

    model       = YOLO(YOLO_MODEL)
    zone_mapper = ZoneMapper(layout_path, store_id, camera_id)
    staff_det   = StaffDetector()
    tracker     = VisitorTracker()
    emitter     = EventEmitter(api_url, store_id, camera_id, output_jsonl)

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open: {video_path}")

    fps         = cap.get(cv2.CAP_PROP_FPS) or 15.0
    frame_h     = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    clip_start  = parse_clip_start(video_path)
    entry_line  = int(0.75 * frame_h)   # lower 25% = entry/exit threshold line
    buf         = []
    frame_idx   = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break
        frame_idx += 1
        if frame_idx % PROCESS_EVERY_N != 0:
            continue

        # ── YOLOv8 inference ──────────────────────────────────────────────────
        results    = model(frame, conf=CONF_THRESHOLD, iou=IOU_THRESHOLD, classes=[0], verbose=False)
        detections = [(int(b.xyxy[0][0]), int(b.xyxy[0][1]), int(b.xyxy[0][2]),
                       int(b.xyxy[0][3]), float(b.conf[0]))
                      for b in results[0].boxes]

        # ── ByteTrack update ──────────────────────────────────────────────────
        ts     = frame_ts(clip_start, frame_idx, fps)
        events = tracker.update(detections, frame, frame_idx, ts, entry_line)

        # ── Annotate zone + staff ─────────────────────────────────────────────
        for ev in events:
            if ev.get("bbox"):
                x1, y1, x2, y2 = ev["bbox"]
                cx, cy  = (x1 + x2) // 2, (y1 + y2) // 2
                fw       = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                ev["zone_id"]  = zone_mapper.get_zone(cx, cy, fw, frame_h)
                ev["is_staff"] = staff_det.classify(frame[y1:y2, x1:x2])
            buf.append(ev)

        if len(buf) >= 50:
            emitter.flush(buf); buf = []

    if buf:
        emitter.flush(buf)

    cap.release()
    tracker.finalize(emitter)
    emitter.close()
    print(f"[detect] Done. Emitted: {emitter.total_emitted}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--video",     required=True)
    p.add_argument("--store-id",  required=True)
    p.add_argument("--camera-id", required=True)
    p.add_argument("--layout",    default="store_layout.json")
    p.add_argument("--api-url",   default="http://localhost:8080")
    p.add_argument("--output",    default=None)
    a = p.parse_args()
    process_clip(a.video, a.store_id, a.camera_id, a.layout, a.api_url, a.output)
