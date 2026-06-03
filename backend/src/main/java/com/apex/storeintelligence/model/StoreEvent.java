package com.apex.storeintelligence.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "store_events", indexes = {
    @Index(name = "idx_store_ts",   columnList = "store_id, timestamp"),
    @Index(name = "idx_visitor",    columnList = "visitor_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_event_id",   columnList = "event_id", unique = true)
})
public class StoreEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="event_id",   nullable=false, unique=true, length=36) private String eventId;
    @Column(name="store_id",   nullable=false, length=32)  private String storeId;
    @Column(name="camera_id",  nullable=false, length=32)  private String cameraId;
    @Column(name="visitor_id", nullable=false, length=32)  private String visitorId;
    @Column(name="event_type", nullable=false, length=32)  private String eventType;
    @Column(name="timestamp",  nullable=false)             private Instant timestamp;
    @Column(name="zone_id",    length=64)                  private String zoneId;
    @Column(name="dwell_ms")                               private Long dwellMs;
    @Column(name="is_staff",   nullable=false)             private boolean staff;
    @Column(name="confidence")                             private Double confidence;
    @Column(name="queue_depth")                            private Integer queueDepth;
    @Column(name="sku_zone",   length=64)                  private String skuZone;
    @Column(name="session_seq")                            private Integer sessionSeq;
    @Column(name="ingested_at",nullable=false)             private Instant ingestedAt;

    public StoreEvent() {}

    public Long    getId()         { return id; }
    public String  getEventId()    { return eventId; }
    public String  getStoreId()    { return storeId; }
    public String  getCameraId()   { return cameraId; }
    public String  getVisitorId()  { return visitorId; }
    public String  getEventType()  { return eventType; }
    public Instant getTimestamp()  { return timestamp; }
    public String  getZoneId()     { return zoneId; }
    public Long    getDwellMs()    { return dwellMs; }
    public boolean isStaff()       { return staff; }
    public Double  getConfidence() { return confidence; }
    public Integer getQueueDepth() { return queueDepth; }
    public String  getSkuZone()    { return skuZone; }
    public Integer getSessionSeq() { return sessionSeq; }
    public Instant getIngestedAt() { return ingestedAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final StoreEvent o = new StoreEvent();
        public Builder eventId(String v)    { o.eventId=v;    return this; }
        public Builder storeId(String v)    { o.storeId=v;    return this; }
        public Builder cameraId(String v)   { o.cameraId=v;   return this; }
        public Builder visitorId(String v)  { o.visitorId=v;  return this; }
        public Builder eventType(String v)  { o.eventType=v;  return this; }
        public Builder timestamp(Instant v) { o.timestamp=v;  return this; }
        public Builder zoneId(String v)     { o.zoneId=v;     return this; }
        public Builder dwellMs(Long v)      { o.dwellMs=v;    return this; }
        public Builder staff(boolean v)     { o.staff=v;      return this; }
        public Builder confidence(Double v) { o.confidence=v; return this; }
        public Builder queueDepth(Integer v){ o.queueDepth=v; return this; }
        public Builder skuZone(String v)    { o.skuZone=v;    return this; }
        public Builder sessionSeq(Integer v){ o.sessionSeq=v; return this; }
        public Builder ingestedAt(Instant v){ o.ingestedAt=v; return this; }
        public StoreEvent build()           { return o; }
    }
}
