package com.apex.storeintelligence.repository;

import com.apex.storeintelligence.model.PosTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long> {

    boolean existsByOrderId(String orderId);

    @Query("SELECT COUNT(DISTINCT p.orderId) FROM PosTransaction p WHERE p.storeId=:s AND p.transactionTime BETWEEN :f AND :t")
    long countTransactions(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    @Query("SELECT COALESCE(SUM(p.basketValue),0) FROM PosTransaction p WHERE p.storeId=:s AND p.transactionTime BETWEEN :f AND :t")
    double sumRevenue(@Param("s") String s, @Param("f") Instant f, @Param("t") Instant t);

    List<PosTransaction> findByStoreIdAndTransactionTimeBetween(String storeId, Instant f, Instant t);
}
