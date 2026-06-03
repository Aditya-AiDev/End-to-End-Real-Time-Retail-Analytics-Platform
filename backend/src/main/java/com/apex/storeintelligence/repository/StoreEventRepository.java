package com.apex.storeintelligence.repository;

import com.apex.storeintelligence.model.StoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StoreEventRepository
 * =====================
 * WHY JPQL: works with both H2 (dev) and PostgreSQL (prod) unchanged.
 * All analytics queries live here — service layer stays clean.
 */
@Repository
public interface StoreEventRepository extends JpaRepository<StoreEvent, Long> {

    boolean existsByEventId(String eventId);

    Optional<StoreEvent> findTopByStoreIdOrderByTimestampDesc(String storeId);

    @Query("SELECT DISTINCT e.storeId FROM StoreEvent e")
    List<String> findDistinctStoreIds();

    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType='ENTRY' AND e.staff=false AND e.timestamp BETWEEN :f AND :t")
    long countDistinctVisitors(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType='ZONE_ENTER' AND e.staff=false AND e.timestamp BETWEEN :f AND :t")
    long countVisitorsWithZoneEnter(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType='BILLING_QUEUE_JOIN' AND e.staff=false AND e.timestamp BETWEEN :f AND :t")
    long countBillingQueueJoins(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType='BILLING_QUEUE_ABANDON' AND e.staff=false AND e.timestamp BETWEEN :f AND :t")
    long countAbandonments(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    /** Returns [zone_id, visit_count, total_dwell_ms] for heatmap + avg dwell */
    @Query("SELECT e.zoneId, COUNT(e), COALESCE(SUM(e.dwellMs),0) FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType IN ('ZONE_EXIT','ZONE_DWELL') " +
           "AND e.staff=false AND e.zoneId IS NOT NULL AND e.timestamp BETWEEN :f AND :t GROUP BY e.zoneId")
    List<Object[]> findZoneDwellStats(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    /** current queue depth = joins - exits since :since */
    @Query("SELECT " +
           "(SELECT COUNT(DISTINCT e1.visitorId) FROM StoreEvent e1 WHERE e1.storeId=:s AND e1.eventType='BILLING_QUEUE_JOIN' AND e1.timestamp>:since) - " +
           "(SELECT COUNT(DISTINCT e2.visitorId) FROM StoreEvent e2 WHERE e2.storeId=:s AND e2.eventType IN ('EXIT','BILLING_QUEUE_ABANDON') AND e2.timestamp>:since)")
    long findCurrentQueueDepth(@Param("s") String s, @Param("since") Instant since);

    @Query("SELECT DISTINCT e.visitorId FROM StoreEvent e " +
           "WHERE e.storeId=:s AND e.eventType='BILLING_QUEUE_JOIN' AND e.staff=false AND e.timestamp BETWEEN :f AND :t")
    List<String> findBillingVisitors(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    @Query("SELECT DISTINCT e.zoneId FROM StoreEvent e WHERE e.storeId=:s AND e.eventType='ZONE_ENTER' AND e.timestamp>:since")
    List<String> findActiveZonesSince(@Param("s") String s, @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM StoreEvent e WHERE e.storeId=:s AND e.timestamp>:since")
    long countEventsAfter(@Param("s") String s, @Param("since") Instant since);
}
