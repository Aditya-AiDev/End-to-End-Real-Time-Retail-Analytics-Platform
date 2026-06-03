"""
staff_detector.py — Staff vs Customer via HSV Uniform Color
=============================================================
TECHNIQUE : HSV color histogram + dominant-color matching
WHY       : Staff wear uniforms of a consistent, known color. No labelled training
            data needed — just the HSV range of the uniform (configurable per store).
            cv2.inRange masks pixels matching the uniform hue; if ≥20% of the
            upper-body ROI matches → staff.
WHY NOT CNN: Needs annotated data. We have raw CCTV only. HSV generalises
             across stores by changing staff_profile.json.

Upper body = top 50% of bounding box (torso/uniform, not legs/shoes).
"""

import json
import cv2
import numpy as np
from pathlib import Path

DEFAULT_RANGES = [
    ((100, 50, 20),  (130, 255, 120)),   # dark navy blue (common retail uniform)
    ((0,   0,  0),   (180,  50,  60)),   # near-black / charcoal
]


class StaffDetector:
    UPPER_RATIO = 0.50
    THRESH      = 0.20   # ≥20% matching pixels → staff

    def __init__(self, profile_path: str = "staff_profile.json"):
        self.ranges = DEFAULT_RANGES
        if Path(profile_path).exists():
            try:
                p = json.load(open(profile_path))
                self.ranges = [(tuple(r["low"]), tuple(r["high"]))
                               for r in p.get("hsv_ranges", [])]
                print(f"[staff] Loaded {len(self.ranges)} HSV ranges")
            except Exception as e:
                print(f"[staff] profile load failed: {e}")

    def classify(self, roi_bgr) -> bool:
        if roi_bgr is None or roi_bgr.size == 0: return False
        h, w = roi_bgr.shape[:2]
        if h < 20 or w < 10: return False
        upper = roi_bgr[:int(h * self.UPPER_RATIO), :]
        hsv   = cv2.cvtColor(upper, cv2.COLOR_BGR2HSV)
        total = hsv.shape[0] * hsv.shape[1]
        if total == 0: return False
        hits  = sum(int(np.sum(cv2.inRange(hsv, np.array(lo), np.array(hi)) > 0))
                    for lo, hi in self.ranges)
        return hits / total >= self.THRESH

    @staticmethod
    def build_profile(color_name: str) -> dict:
        MAP = {
            "navy blue": [{"low":[100,50,20],"high":[130,255,120]}],
            "black":     [{"low":[0,0,0],    "high":[180,50,60]}],
            "white":     [{"low":[0,0,200],  "high":[180,30,255]}],
            "red":       [{"low":[0,100,100],"high":[10,255,255]},
                          {"low":[170,100,100],"high":[180,255,255]}],
            "green":     [{"low":[40,50,50], "high":[80,255,255]}],
        }
        for k, v in MAP.items():
            if k in color_name.lower():
                return {"hsv_ranges": v, "color_name": color_name}
        return {"hsv_ranges": MAP["navy blue"], "color_name": "default_navy"}
