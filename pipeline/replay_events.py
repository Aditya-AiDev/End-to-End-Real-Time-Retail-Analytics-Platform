import json
import requests

# ── Load all JSONL events from disk ──────────────────────────────────────────
files = [
    "../CCTV/events_output/cam1.jsonl",
    "../CCTV/events_output/CAM2.jsonl",
    "../CCTV/events_output/billing.jsonl",
]

all_events = []
for f in files:
    try:
        with open(f) as fh:
            for line in fh:
                line = line.strip()
                if line:
                    all_events.append(json.loads(line))
        print(f"Loaded {f}")
    except FileNotFoundError:
        print(f"SKIP (not found): {f}")

print(f"\nTotal events to send: {len(all_events)}")

if not all_events:
    print("No events found. Run detect.py first.")
    exit(1)

# ── Send in batches of 100 ────────────────────────────────────────────────────
API = "http://localhost:8080/api/v1/events/ingest"
total_accepted = 0
total_rejected = 0
total_dup = 0

for i in range(0, len(all_events), 100):
    batch = all_events[i:i+100]
    try:
        r = requests.post(API, json={"events": batch}, timeout=15)
        d = r.json()
        acc = d.get("accepted", 0)
        rej = d.get("rejected", 0)
        dup = d.get("duplicates", 0)
        total_accepted += acc
        total_rejected += rej
        total_dup += dup
        print(f"Batch {i//100+1}: HTTP {r.status_code} | accepted={acc} rejected={rej} duplicates={dup}")
        if rej > 0:
            print(f"  Errors: {d.get('errors', [])[:3]}")
    except Exception as e:
        print(f"Batch {i//100+1}: FAILED - {e}")

print(f"\nDone. accepted={total_accepted} rejected={total_rejected} duplicates={total_dup}")

# ── Load POS transactions ─────────────────────────────────────────────────────
import csv
POS_FILE = "../CCTV/footage/POS_transactions.csv"
POS_API  = "http://localhost:8080/api/v1/pos/ingest"

try:
    rows = []
    with open(POS_FILE) as f:
        for r in csv.DictReader(f):
            rows.append({
                "orderId":     r.get("order_id", ""),
                "storeId":     r.get("store_id", "ST1076"),
                "timestamp":   r.get("order_date","") + "T" + r.get("order_time","") + "Z",
                "basketValue": float(r.get("total_amount", 0) or 0),
                "productId":   r.get("product_id", ""),
                "brandName":   r.get("brand_name", ""),
            })
    resp = requests.post(POS_API, json={"transactions": rows}, timeout=30)
    print(f"\nPOS ingest: HTTP {resp.status_code} | {resp.json()}")
except FileNotFoundError:
    print(f"\nPOS file not found: {POS_FILE}")
except Exception as e:
    print(f"\nPOS ingest failed: {e}")

# ── Print final metrics ───────────────────────────────────────────────────────
print("\n--- Final Metrics ---")
try:
    m = requests.get("http://localhost:8080/api/v1/stores/ST1076/metrics", timeout=10).json()
    print(f"Unique Visitors  : {m.get('uniqueVisitors')}")
    print(f"Conversion Rate  : {round(m.get('conversionRate',0)*100, 1)}%")
    print(f"Avg Dwell        : {m.get('avgDwellSeconds')} seconds")
    print(f"Queue Depth      : {m.get('queueDepth')}")
    print(f"Abandonment Rate : {round(m.get('abandonmentRate',0)*100, 1)}%")
    print(f"Dwell by Zone    : {m.get('avgDwellByZone')}")
except Exception as e:
    print(f"Could not fetch metrics: {e}")

print("\n--- Funnel ---")
try:
    f = requests.get("http://localhost:8080/api/v1/stores/ST1076/funnel", timeout=10).json()
    print(f"Entry            : {f.get('entryCount')}")
    print(f"Zone Visit       : {f.get('zoneVisitCount')}")
    print(f"Billing Queue    : {f.get('billingQueueCount')}")
    print(f"Purchase         : {f.get('purchaseCount')}")
except Exception as e:
    print(f"Could not fetch funnel: {e}")
