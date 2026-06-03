package com.apex.storeintelligence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class Dtos {

    // ── Ingest ────────────────────────────────────────────────────────────────
    public static class IngestRequest {
        @Valid @Size(min=1, max=500) private List<@Valid EventDto> events;
        public List<EventDto> getEvents() { return events; }
        public void setEvents(List<EventDto> e) { this.events = e; }
    }

    public static class IngestResponse {
        private int accepted, rejected, duplicates;
        private List<String> errors;
        public IngestResponse(int a, int r, int d, List<String> e) { accepted=a; rejected=r; duplicates=d; errors=e; }
        public int getAccepted()    { return accepted; }
        public int getRejected()    { return rejected; }
        public int getDuplicates()  { return duplicates; }
        public List<String> getErrors() { return errors; }
    }

    public static class EventDto {
        @NotBlank @JsonProperty("event_id")   private String eventId;
        @NotBlank @JsonProperty("store_id")   private String storeId;
        @NotBlank @JsonProperty("camera_id")  private String cameraId;
        @NotBlank @JsonProperty("visitor_id") private String visitorId;
        @NotBlank @Pattern(regexp="ENTRY|EXIT|ZONE_ENTER|ZONE_EXIT|ZONE_DWELL|BILLING_QUEUE_JOIN|BILLING_QUEUE_ABANDON|REENTRY")
        @JsonProperty("event_type") private String eventType;
        @NotBlank private String timestamp;
        @JsonProperty("zone_id")   private String zoneId;
        @JsonProperty("dwell_ms")  private Long dwellMs;
        @JsonProperty("is_staff")  private boolean staff;
        @DecimalMin("0.0") @DecimalMax("1.0") private Double confidence;
        private Map<String, Object> metadata;

        public String getEventId()   { return eventId; }
        public String getStoreId()   { return storeId; }
        public String getCameraId()  { return cameraId; }
        public String getVisitorId() { return visitorId; }
        public String getEventType() { return eventType; }
        public String getTimestamp() { return timestamp; }
        public String getZoneId()    { return zoneId; }
        public Long   getDwellMs()   { return dwellMs; }
        public boolean isStaff()     { return staff; }
        public Double getConfidence(){ return confidence; }
        public Map<String,Object> getMetadata() { return metadata; }
    }

    // ── Metrics ───────────────────────────────────────────────────────────────
    public static class MetricsDto {
        public String storeId;
        public Instant windowStart, windowEnd;
        public int uniqueVisitors;
        public double conversionRate, abandonmentRate;
        public long avgDwellSeconds;
        public int queueDepth;
        public Map<String, Long> avgDwellByZone;

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final MetricsDto o = new MetricsDto();
            public Builder storeId(String v)              { o.storeId=v; return this; }
            public Builder windowStart(Instant v)         { o.windowStart=v; return this; }
            public Builder windowEnd(Instant v)           { o.windowEnd=v; return this; }
            public Builder uniqueVisitors(int v)          { o.uniqueVisitors=v; return this; }
            public Builder conversionRate(double v)       { o.conversionRate=v; return this; }
            public Builder abandonmentRate(double v)      { o.abandonmentRate=v; return this; }
            public Builder avgDwellSeconds(long v)        { o.avgDwellSeconds=v; return this; }
            public Builder queueDepth(int v)              { o.queueDepth=v; return this; }
            public Builder avgDwellByZone(Map<String,Long> v){ o.avgDwellByZone=v; return this; }
            public MetricsDto build()                     { return o; }
        }
    }

    // ── Funnel ────────────────────────────────────────────────────────────────
    public static class FunnelDto {
        public String storeId;
        public int entryCount, zoneVisitCount, billingQueueCount, purchaseCount;
        public double entryToZoneDropPct, zoneToBillingDropPct, billingToPurchaseDropPct;

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final FunnelDto o = new FunnelDto();
            public Builder storeId(String v)                   { o.storeId=v; return this; }
            public Builder entryCount(int v)                   { o.entryCount=v; return this; }
            public Builder zoneVisitCount(int v)               { o.zoneVisitCount=v; return this; }
            public Builder billingQueueCount(int v)            { o.billingQueueCount=v; return this; }
            public Builder purchaseCount(int v)                { o.purchaseCount=v; return this; }
            public Builder entryToZoneDropPct(double v)        { o.entryToZoneDropPct=v; return this; }
            public Builder zoneToBillingDropPct(double v)      { o.zoneToBillingDropPct=v; return this; }
            public Builder billingToPurchaseDropPct(double v)  { o.billingToPurchaseDropPct=v; return this; }
            public FunnelDto build()                           { return o; }
        }
    }

    // ── Heatmap ───────────────────────────────────────────────────────────────
    public static class HeatmapDto {
        public String storeId;
        public List<ZoneHeat> zones;
        public boolean dataConfidence;

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final HeatmapDto o = new HeatmapDto();
            public Builder storeId(String v)         { o.storeId=v; return this; }
            public Builder zones(List<ZoneHeat> v)   { o.zones=v; return this; }
            public Builder dataConfidence(boolean v) { o.dataConfidence=v; return this; }
            public HeatmapDto build()                { return o; }
        }
    }

    public static class ZoneHeat {
        public String zoneId, zoneName;
        public int visitCount, normalizedScore;
        public long avgDwellMs;

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final ZoneHeat o = new ZoneHeat();
            public Builder zoneId(String v)          { o.zoneId=v; return this; }
            public Builder zoneName(String v)        { o.zoneName=v; return this; }
            public Builder visitCount(int v)         { o.visitCount=v; return this; }
            public Builder normalizedScore(int v)    { o.normalizedScore=v; return this; }
            public Builder avgDwellMs(long v)        { o.avgDwellMs=v; return this; }
            public ZoneHeat build()                  { return o; }
        }
    }

    // ── Anomaly ───────────────────────────────────────────────────────────────
    public static class AnomalyDto {
        public String anomalyId, storeId, anomalyType, severity, description, suggestedAction;
        public Instant detectedAt;
        public Map<String, Object> details;

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final AnomalyDto o = new AnomalyDto();
            public Builder anomalyId(String v)       { o.anomalyId=v; return this; }
            public Builder storeId(String v)         { o.storeId=v; return this; }
            public Builder anomalyType(String v)     { o.anomalyType=v; return this; }
            public Builder severity(String v)        { o.severity=v; return this; }
            public Builder description(String v)     { o.description=v; return this; }
            public Builder suggestedAction(String v) { o.suggestedAction=v; return this; }
            public Builder detectedAt(Instant v)     { o.detectedAt=v; return this; }
            public Builder details(Map<String,Object> v){ o.details=v; return this; }
            public AnomalyDto build()                { return o; }
        }
    }

    // ── Health ────────────────────────────────────────────────────────────────
    public static class HealthDto {
        public String status;
        public Instant checkedAt;
        public Map<String, StoreHealth> stores;
        public boolean dbConnected;

        public String getStatus() { return status; }

        public static Builder builder() { return new Builder(); }
        public static class Builder {
            private final HealthDto o = new HealthDto();
            public Builder status(String v)                    { o.status=v; return this; }
            public Builder checkedAt(Instant v)                { o.checkedAt=v; return this; }
            public Builder stores(Map<String,StoreHealth> v)   { o.stores=v; return this; }
            public Builder dbConnected(boolean v)              { o.dbConnected=v; return this; }
            public HealthDto build()                           { return o; }
        }

        public static class StoreHealth {
            public String storeId;
            public Instant lastEventAt;
            public boolean staleFeed;
            public long lagSeconds;
            public int eventCountLast5Min;

            public boolean isStaleFeed() { return staleFeed; }

            public static Builder builder() { return new Builder(); }
            public static class Builder {
                private final StoreHealth o = new StoreHealth();
                public Builder storeId(String v)           { o.storeId=v; return this; }
                public Builder lastEventAt(Instant v)      { o.lastEventAt=v; return this; }
                public Builder staleFeed(boolean v)        { o.staleFeed=v; return this; }
                public Builder lagSeconds(long v)          { o.lagSeconds=v; return this; }
                public Builder eventCountLast5Min(int v)   { o.eventCountLast5Min=v; return this; }
                public StoreHealth build()                 { return o; }
            }
        }
    }

    // ── POS Ingest ────────────────────────────────────────────────────────────
    public static class PosIngestRequest {
        private List<PosTransactionDto> transactions;
        public List<PosTransactionDto> getTransactions() { return transactions; }
        public void setTransactions(List<PosTransactionDto> t) { this.transactions = t; }
    }

    public static class PosTransactionDto {
        private String orderId, storeId, timestamp, productId, brandName;
        private double basketValue;
        public String getOrderId()    { return orderId; }
        public String getStoreId()    { return storeId; }
        public String getTimestamp()  { return timestamp; }
        public String getProductId()  { return productId; }
        public String getBrandName()  { return brandName; }
        public double getBasketValue(){ return basketValue; }
    }
}
