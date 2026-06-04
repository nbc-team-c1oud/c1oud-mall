package nbc.c1oud_mall.payment.application;

import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.payment.domain.WebhookEvent;
import nbc.c1oud_mall.payment.infrastructure.WebhookEventJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class WebhookEventRegistrar {

    private final WebhookEventJpaRepository webhookEventJpaRepository;
    private final TransactionTemplate requiresNewTemplate;

    public WebhookEventRegistrar(WebhookEventJpaRepository webhookEventJpaRepository,
                                  PlatformTransactionManager transactionManager) {
        this.webhookEventJpaRepository = webhookEventJpaRepository;
        requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * INSERT-first 패턴으로 WebhookEvent 등록을 시도한다.
     *
     * REQUIRES_NEW 독립 트랜잭션으로 실행. (portonePaymentId, eventType) UNIQUE 위반 시
     * 명시적으로 rollback-only 처리 후 false 반환 — 중복 웹훅 신호.
     * 이 방식은 외부(호출자) 트랜잭션에 rollback-only 오염을 전파하지 않는다.
     */
    public boolean tryRegister(String portonePaymentId, String eventType) {
        Boolean registered = requiresNewTemplate.execute(status -> {
            try {
                webhookEventJpaRepository.saveAndFlush(
                        WebhookEvent.receive(portonePaymentId, eventType, null));
                return true;
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate webhook event suppressed. portonePaymentId={}, eventType={}",
                        portonePaymentId, eventType);
                status.setRollbackOnly();
                return false;
            }
        });
        return Boolean.TRUE.equals(registered);
    }
}
