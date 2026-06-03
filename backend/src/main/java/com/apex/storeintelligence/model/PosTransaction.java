package com.apex.storeintelligence.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_transactions", indexes = {
    @Index(name = "idx_pos_store_ts", columnList = "store_id, transaction_time")
})
public class PosTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="order_id",        length=64)  private String orderId;
    @Column(name="store_id",        nullable=false, length=32) private String storeId;
    @Column(name="transaction_time",nullable=false)            private Instant transactionTime;
    @Column(name="basket_value",    precision=12, scale=2)     private BigDecimal basketValue;
    @Column(name="product_id",      length=32)  private String productId;
    @Column(name="brand_name",      length=128) private String brandName;

    public PosTransaction() {}

    public Long       getId()              { return id; }
    public String     getOrderId()         { return orderId; }
    public String     getStoreId()         { return storeId; }
    public Instant    getTransactionTime() { return transactionTime; }
    public BigDecimal getBasketValue()     { return basketValue; }
    public String     getProductId()       { return productId; }
    public String     getBrandName()       { return brandName; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final PosTransaction o = new PosTransaction();
        public Builder orderId(String v)          { o.orderId=v;          return this; }
        public Builder storeId(String v)          { o.storeId=v;          return this; }
        public Builder transactionTime(Instant v) { o.transactionTime=v;  return this; }
        public Builder basketValue(BigDecimal v)  { o.basketValue=v;      return this; }
        public Builder productId(String v)        { o.productId=v;        return this; }
        public Builder brandName(String v)        { o.brandName=v;        return this; }
        public PosTransaction build()             { return o; }
    }
}
