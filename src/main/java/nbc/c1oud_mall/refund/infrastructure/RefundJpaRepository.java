package nbc.c1oud_mall.refund.infrastructure;

import nbc.c1oud_mall.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {

    /**
     * 동일 paymentId + orderItemId에 대해 누적된 환불 수량 합계.
     * RF001(잔여 수량 초과) 검증을 위해 application service가 호출.
     */
    @Query("SELECT COALESCE(SUM(ri.quantity), 0) "
            + "FROM Refund r JOIN r.refundItems ri "
            + "WHERE r.paymentId = :paymentId "
            + "  AND ri.orderItemId = :orderItemId")
    long sumRefundedQuantity(@Param("paymentId") Long paymentId,
                              @Param("orderItemId") Long orderItemId);
}
