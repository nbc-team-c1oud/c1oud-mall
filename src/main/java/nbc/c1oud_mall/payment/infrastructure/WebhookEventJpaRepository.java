package nbc.c1oud_mall.payment.infrastructure;

import nbc.c1oud_mall.payment.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventJpaRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByPortonePaymentIdAndEventType(String portonePaymentId, String eventType);
}
