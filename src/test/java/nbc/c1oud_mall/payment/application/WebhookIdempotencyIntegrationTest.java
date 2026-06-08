package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.payment.infrastructure.WebhookEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebhookIdempotencyIntegrationTest {

    @Autowired
    private PaymentWebhookUseCase paymentWebhookUseCase;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private WebhookEventJpaRepository webhookEventJpaRepository;

    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        webhookEventJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        savedPayment = paymentJpaRepository.saveAndFlush(Payment.of(1L, 10L, 10_000L, 10_000L, 0L));

        given(portOnePaymentQueryPort.query(savedPayment.getPortonePaymentId()))
                .willReturn(new PortOnePaymentInfo(
                        savedPayment.getPortonePaymentId(),
                        PortOnePaymentStatus.PAID,
                        10_000L,
                        "TOSSPAYMENTS",
                        "pg-tx-idempotency-001"));
    }

    @AfterEach
    void cleanup() {
        webhookEventJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 portonePaymentId 웹훅 2회 동시 수신 → 결제 정확히 1회 확정, WebhookEvent 1건")
    void concurrent_duplicate_webhooks_confirms_payment_exactly_once() throws Exception {
        String portonePaymentId = savedPayment.getPortonePaymentId();
        int threadCount = 2;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<PaymentConfirmationResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return paymentWebhookUseCase.handleWebhook(portonePaymentId);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<PaymentConfirmationResult> results = new ArrayList<>();
        for (Future<PaymentConfirmationResult> future : futures) {
            results.add(future.get());
        }

        long confirmedCount = results.stream().filter(r -> !r.alreadyCompleted()).count();
        long idempotentCount = results.stream().filter(PaymentConfirmationResult::alreadyCompleted).count();

        assertThat(confirmedCount).isEqualTo(1);
        assertThat(idempotentCount).isEqualTo(1);

        Payment confirmed = paymentJpaRepository.findByPortonePaymentId(portonePaymentId).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        assertThat(webhookEventJpaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("역순 수신 (결제 COMPLETED 상태) + 웹훅 도착 → isCompleted 가드 작동, 200 멱등")
    void webhook_after_confirm_api_returns_already_completed() {
        // Confirm API가 먼저 처리했다고 가정 — Payment 직접 COMPLETED로 전이
        Payment payment = paymentJpaRepository.findByPortonePaymentId(
                savedPayment.getPortonePaymentId()).orElseThrow();
        payment.markCompleted("pg-tx-001", 100L, java.time.LocalDateTime.now());
        paymentJpaRepository.saveAndFlush(payment);

        PaymentConfirmationResult result = paymentWebhookUseCase.handleWebhook(
                savedPayment.getPortonePaymentId());

        assertThat(result.alreadyCompleted()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
