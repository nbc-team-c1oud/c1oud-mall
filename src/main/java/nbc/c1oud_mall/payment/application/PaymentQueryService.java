package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.payment.application.dto.PaymentSummary;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentJpaRepository paymentJpaRepository;

    public Optional<PaymentSummary> getPaymentSummaryByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderId(orderId)
                .map(PaymentSummary::from);
    }

    public Map<Long, PaymentSummary> getPaymentSummaryMapByOrderIds(List<Long> orderIds) {
        return paymentJpaRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(
                        payment -> payment.getOrderId(),
                        PaymentSummary::from
                ));
    }
}
