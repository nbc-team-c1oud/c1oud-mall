package nbc.c1oud_mall.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.domain.BaseEntity;

@Entity
@Table(name = "refund_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price_snapshot_at_payment", nullable = false)
    private long priceSnapshotAtPayment;

    @Column(name = "item_refund_amount", nullable = false)
    private long itemRefundAmount;

    // 패키지 프라이빗: Aggregate 외부에서 직접 생성 차단 — Refund.of(...)에서만 호출
    RefundItem(Long orderItemId, int quantity, long priceSnapshotAtPayment) {
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.priceSnapshotAtPayment = priceSnapshotAtPayment;
        this.itemRefundAmount = priceSnapshotAtPayment * quantity;
    }
}
