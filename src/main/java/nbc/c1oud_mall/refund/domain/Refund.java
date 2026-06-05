package nbc.c1oud_mall.refund.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.common.domain.BaseEntity;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Embedded
    private RefundBreakdown breakdown;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "refund_id", nullable = false)
    private List<RefundItem> refundItems;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "db_committed_at")
    private LocalDateTime dbCommittedAt;

    @Column(name = "pg_cancelled_at")
    private LocalDateTime pgCancelledAt;

    @Column(name = "pg_cancel_tx_id", length = 100)
    private String pgCancelTxId;

    private Refund(Long paymentId, Long userId, String reason,
                   RefundBreakdown breakdown, List<RefundItem> refundItems) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.reason = reason;
        this.breakdown = breakdown;
        this.refundItems = refundItems;
        this.status = RefundStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    public static Refund of(RefundablePayment payment,
                            List<RefundItemRequest> itemRequests,
                            RefundBreakdown breakdown,
                            String reason) {
        if (!payment.isCompleted()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_REFUNDABLE_STATE);
        }
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new IllegalArgumentException("RefundItems must not be empty");
        }
        if (breakdown == null) {
            throw new IllegalArgumentException("RefundBreakdown must not be null");
        }
        List<RefundItem> items = new ArrayList<>(itemRequests.size());
        for (RefundItemRequest req : itemRequests) {
            if (req.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Refund quantity must be positive: " + req.quantity());
            }
            if (req.quantity() > req.remainingRefundableQuantity()) {
                throw BusinessException.withDetail(
                        ErrorCode.REFUND_QUANTITY_EXCEEDED,
                        "orderItemId=" + req.orderItemId()
                                + ", requested=" + req.quantity()
                                + ", remaining=" + req.remainingRefundableQuantity());
            }
            items.add(new RefundItem(
                    req.orderItemId(), req.quantity(), req.priceSnapshotAtPayment()));
        }
        return new Refund(payment.paymentId(), payment.userId(),
                reason, breakdown, items);
    }
}
