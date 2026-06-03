#!/usr/bin/env bash
# run.sh — Orchestrate CCTV clip processing for one store
# Usage: ./run.sh [STORE_ID] [CLIPS_DIR] [API_URL]
# Example: ./run.sh ST1076 /data/clips http://localhost:8080

set -e
STORE_ID="${1:-ST1076}"
CLIPS_DIR="${2:-../CCTV/footage}"
API_URL="${3:-http://localhost:8080}"
LAYOUT="$(dirname "$0")/store_layout.json"
OUT_DIR="$(dirname "$0")/../CCTV/events_output/${STORE_ID}"
mkdir -p "$OUT_DIR"

echo "Store=$STORE_ID  Clips=$CLIPS_DIR  API=$API_URL"

# ── Staff calibration ─────────────────────────────────────────────────────────
PROFILE="$(dirname "$0")/staff_profile.json"
if [ ! -f "$PROFILE" ]; then
    python3 -c "
from staff_detector import StaffDetector; import json
json.dump(StaffDetector.build_profile('navy blue'), open('$PROFILE','w'), indent=2)
print('[run] staff_profile.json created')
"
fi

# ── Camera → clip mapping ─────────────────────────────────────────────────────
declare -A CAMS
for i in 1 2 3 4 5; do
    CLIP=$(find "$CLIPS_DIR" -iname "CAM_${i}.mp4" -o -iname "CAM ${i}.mp4" 2>/dev/null | head -1)
    [ -z "$CLIP" ] && continue
    case $i in
        1) CID="cam1" ;;
        2) CID="CAM2" ;;
        3) CID="PURPLLE_MUM_1076_CAM6" ;;
        4) CID="CAM_FLOOR_02" ;;
        5) CID="CAM_FLOOR_03" ;;
    esac
    CAMS[$CID]="$CLIP"
done

# ── Process clips (max 3 parallel) ────────────────────────────────────────────
PIDS=()
for CID in "${!CAMS[@]}"; do
    CLIP="${CAMS[$CID]}"
    python3 "$(dirname "$0")/detect.py" \
        --video "$CLIP" --store-id "$STORE_ID" --camera-id "$CID" \
        --layout "$LAYOUT" --api-url "$API_URL" \
        --output "${OUT_DIR}/${CID}_events.jsonl" &
    PIDS+=($!)
    if [ ${#PIDS[@]} -ge 3 ]; then wait "${PIDS[0]}"; PIDS=("${PIDS[@]:1}"); fi
done
for PID in "${PIDS[@]}"; do wait "$PID"; done

# ── Load POS transactions ─────────────────────────────────────────────────────
POS=$(find "$CLIPS_DIR" -name "*.csv" | head -1)
if [ -n "$POS" ] && [ -f "$POS" ]; then
    python3 -c "
import csv, json, requests
rows=[]
with open('$POS') as f:
    for r in csv.DictReader(f):
        rows.append({'orderId':r.get('order_id'),'storeId':r.get('store_id','$STORE_ID'),
            'timestamp':(r.get('order_date','')+'T'+r.get('order_time','')).replace(' ','T'),
            'basketValue':float(r.get('total_amount',0)),'productId':r.get('product_id',''),
            'brandName':r.get('brand_name','')})
resp=requests.post('$API_URL/api/v1/pos/ingest',json={'transactions':rows},timeout=30)
print(f'POS ingest HTTP {resp.status_code} — {len(rows)} rows')
"
fi

echo "Done. Events in $OUT_DIR"
