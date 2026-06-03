"""
zone_mapper.py — Zone Polygon Containment
==========================================
TECHNIQUE : cv2.pointPolygonTest  (point-in-polygon)
WHY       : Zone boundaries are defined as polygons in normalised [0,1] space.
            pointPolygonTest is O(n) per detection — microseconds for 5-10 zones
            at 30 detections/frame. No approximation, fully accurate.

Polygons stored in store_layout.json in [x,y] normalised coordinates.
Scaled to pixel space per frame at query time → resolution-independent.
"""

import json
import cv2
import numpy as np
from typing import Optional, List
from pathlib import Path


class ZoneMapper:
    def __init__(self, layout_path: str, store_id: str, camera_id: str):
        self.zones: List[dict] = []
        try:
            with open(layout_path) as f:
                layout = json.load(f)
            cams = layout.get("stores", {}).get(store_id, {}).get("cameras", {})
            cam  = cams.get(camera_id) or next(
                (v for k, v in cams.items() if camera_id in k or k in camera_id), {}
            )
            self.zones = cam.get("zones", [])
            print(f"[zone_mapper] {len(self.zones)} zones for {store_id}/{camera_id}")
        except FileNotFoundError:
            print(f"[zone_mapper] layout not found: {layout_path}")
        except Exception as e:
            print(f"[zone_mapper] error: {e}")

    def get_zone(self, cx: int, cy: int, fw: int = 1920, fh: int = 1080) -> Optional[str]:
        if not self.zones: return None
        nx, ny = cx / fw, cy / fh
        for z in self.zones:
            poly = np.array(z["polygon"], dtype=np.float32)
            if cv2.pointPolygonTest(poly, (float(nx), float(ny)), False) >= 0:
                return z["zone_id"]
        return None

    def get_info(self, zone_id: str) -> dict:
        return next((z for z in self.zones if z["zone_id"] == zone_id), {})
