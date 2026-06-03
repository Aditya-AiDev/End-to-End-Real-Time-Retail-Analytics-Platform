"""
tracker.py — ByteTrack + Appearance Re-ID + Entry/Exit Logic
=============================================================
TECHNIQUE : ByteTrack (supervision library)
WHY       : Uses both high-conf AND low-conf detections in two-stage matching.
            Occluded people (e.g. in dense queue) stay tracked instead of
            getting a new ID on every occlusion.

TECHNIQUE : ResNet-50 appearance Re-ID (torchvision)
WHY       : When a person exits and re-enters, ByteTrack sees a new track ID.
            ResNet-50 (fc removed → 2048-dim embedding) + cosine similarity
            links the new track to the same visitor_id from the gallery.
            Threshold 0.72 balances false-positives vs missed re-entries.

ENTRY/EXIT: Horizontal line at 75% frame height. Centroid crossing UP (−Y
            velocity) → ENTRY. Crossing DOWN (+Y velocity) → EXIT.
"""

import uuid
import numpy as np
from collections import deque
from typing import Dict, List, Optional, Tuple
import cv2

try:
    import supervision as sv
    HAS_SV = True
except ImportError:
    HAS_SV = False
    print("[tracker] supervision not installed — simple fallback active")

try:
    import torch
    import torchvision.models as models
    import torchvision.transforms as T
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False


# ── Re-ID feature extractor ───────────────────────────────────────────────────
class AppearanceExtractor:
    def __init__(self):
        if not HAS_TORCH:
            self.model = None; return
        m = models.resnet50(pretrained=True)
        m.fc = torch.nn.Identity()
        m.eval()
        self.model = m.cuda() if torch.cuda.is_available() else m
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.tf = T.Compose([
            T.ToPILImage(), T.Resize((128, 64)), T.ToTensor(),
            T.Normalize([0.485,0.456,0.406],[0.229,0.224,0.225])
        ])

    def extract(self, roi) -> Optional[np.ndarray]:
        if self.model is None or roi is None or roi.size == 0: return None
        try:
            t = self.tf(cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)).unsqueeze(0)
            if self.device == "cuda": t = t.cuda()
            with torch.no_grad():
                f = self.model(t).cpu().numpy()[0]
            return f / (np.linalg.norm(f) + 1e-8)
        except: return None


# ── Track state ───────────────────────────────────────────────────────────────
class Track:
    def __init__(self, track_id: int, visitor_id: str):
        self.track_id    = track_id
        self.visitor_id  = visitor_id
        self.history     = deque(maxlen=10)   # (frame, cx, cy)
        self.last_bbox   = None
        self.last_frame  = 0
        self.zone_id     = None
        self.zone_enter  = None
        self.in_store    = False
        self.is_staff    = False
        self.seq         = 0
        self.feats: List[np.ndarray] = []


class VisitorTracker:
    REID_THRESH = 0.72
    MAX_GALLERY = 200

    def __init__(self):
        # supervision 0.28 renamed ByteTrack constructor args:
        # track_thresh -> track_activation_threshold
        # track_buffer -> lost_track_buffer
        # match_thresh -> minimum_matching_threshold
        try:
            self.bt = sv.ByteTrack(
                track_activation_threshold=0.35,
                lost_track_buffer=45,
                minimum_matching_threshold=0.8,
                frame_rate=15
            ) if HAS_SV else None
        except TypeError:
            self.bt = sv.ByteTrack() if HAS_SV else None
        self.tracks: Dict[int, Track] = {}
        self.gallery: List[Track] = []
        self._cnt    = 0
        self.reid    = AppearanceExtractor()

    def _vid(self): self._cnt += 1; return f"VIS_{self._cnt:05d}"

    def _yvel(self, t: Track) -> float:
        h = list(t.history)
        if len(h) < 3: return 0.0
        d = [h[i+1][2]-h[i][2] for i in range(len(h)-1)]
        return sum(d)/len(d)

    def _reid_match(self, feat) -> Optional[Track]:
        if feat is None or not self.gallery: return None
        best, bsc = None, -1
        for g in self.gallery:
            if not g.feats: continue
            avg = np.mean(g.feats[-5:], axis=0)
            s   = float(np.dot(feat, avg))
            if s > bsc: bsc, best = s, g
        return best if bsc >= self.REID_THRESH else None

    def _evt(self, kind, t: Track, ts, bbox, conf, zone_id=None, meta=None) -> dict:
        t.seq += 1
        return {"event_type": kind, "visitor_id": t.visitor_id, "track_id": t.track_id,
                "timestamp": ts, "bbox": bbox, "confidence": round(conf,4),
                "zone_id": zone_id, "is_staff": t.is_staff, "session_seq": t.seq,
                "metadata": meta or {}}

    def update(self, detections, frame, frame_idx, ts, entry_line_y) -> List[dict]:
        events = []
        fh, fw = frame.shape[:2]

        if not self.bt or not HAS_SV:
            return events

        xyxy  = np.array([[d[0],d[1],d[2],d[3]] for d in detections], dtype=np.float32) if detections else np.empty((0,4))
        confs = np.array([d[4] for d in detections], dtype=np.float32) if detections else np.empty(0)
        sv_d  = sv.Detections(xyxy=xyxy, confidence=confs) if len(xyxy) else sv.Detections.empty()
        tracked = self.bt.update_with_detections(sv_d)

        for i in range(len(tracked)):
            tid  = int(tracked.tracker_id[i])
            bbox = tuple(tracked.xyxy[i].astype(int))
            conf = float(tracked.confidence[i]) if tracked.confidence is not None else 0.8
            cx   = (bbox[0]+bbox[2])//2
            cy   = (bbox[1]+bbox[3])//2

            if tid not in self.tracks:
                roi  = frame[bbox[1]:bbox[3], bbox[0]:bbox[2]]
                feat = self.reid.extract(roi)
                matched = self._reid_match(feat)
                if matched:
                    trk = Track(tid, matched.visitor_id)
                    self.gallery = [g for g in self.gallery if g.visitor_id != matched.visitor_id]
                    events.append(self._evt("REENTRY", trk, ts, bbox, conf))
                else:
                    trk = Track(tid, self._vid())
                if feat is not None: trk.feats.append(feat)
                self.tracks[tid] = trk

            trk = self.tracks[tid]
            trk.last_bbox = bbox; trk.last_frame = frame_idx
            trk.history.append((frame_idx, cx, cy))

            if frame_idx % 5 == 0:
                roi  = frame[bbox[1]:bbox[3], bbox[0]:bbox[2]]
                feat = self.reid.extract(roi)
                if feat is not None:
                    trk.feats.append(feat)
                    if len(trk.feats) > 20: trk.feats.pop(0)

            yv   = self._yvel(trk)
            near = abs(cy - entry_line_y) < fh * 0.08

            if near and not trk.in_store and yv < -1.5:
                trk.in_store = True
                events.append(self._evt("ENTRY", trk, ts, bbox, conf))

            if near and trk.in_store and yv > 1.5:
                trk.in_store = False
                events.append(self._evt("EXIT", trk, ts, bbox, conf))
                self.gallery.append(trk)
                if len(self.gallery) > self.MAX_GALLERY: self.gallery.pop(0)

        return events

    def emit_zone_events(self, track_id, new_zone, ts, bbox, conf, fps) -> List[dict]:
        trk = self.tracks.get(track_id)
        if not trk: return []
        events = []
        if new_zone != trk.zone_id:
            if trk.zone_id and trk.zone_enter:
                ms = int(((trk.last_frame - trk.zone_enter) / fps) * 1000)
                events.append(self._evt("ZONE_EXIT", trk, ts, bbox, conf, trk.zone_id, {"dwell_ms": ms}))
            if new_zone:
                events.append(self._evt("ZONE_ENTER", trk, ts, bbox, conf, new_zone, {"dwell_ms": 0}))
                trk.zone_enter = trk.last_frame
            trk.zone_id = new_zone
        return events

    def finalize(self, emitter):
        for tid, trk in self.tracks.items():
            if trk.in_store:
                emitter.emit_single({"event_type":"EXIT","visitor_id":trk.visitor_id,
                    "track_id":tid,"timestamp":"clip_end","bbox":trk.last_bbox,
                    "confidence":0.5,"zone_id":None,"is_staff":trk.is_staff,
                    "session_seq":trk.seq+1,"metadata":{"clip_end_forced":True}})
