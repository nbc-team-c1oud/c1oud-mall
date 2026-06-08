package nbc.c1oud_mall.payment.presentation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import nbc.c1oud_mall.payment.application.PaymentConfirmationUseCase;
import nbc.c1oud_mall.payment.application.PaymentWebhookUseCase;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.application.PortOnePaymentQueryPort;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.portone.PortOneProperties;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookControllerIntegrationTest {

    private static final String WEBHOOK_PATH = PortOneWebhookSignatureFilter.WEBHOOK_PATH;
    private static final String WEBHOOK_ID = "msg_integration_001";
    private static final String SAMPLE_BODY =
            "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"test-payment-id\"}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortOneProperties properties;

    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    // PaymentConfirmationService가 두 인터페이스를 모두 구현하므로
    // 둘 다 목킹해야 빈 충돌 없이 각 컨트롤러가 올바른 의존성을 주입받는다
    @MockitoBean
    private PaymentConfirmationUseCase paymentConfirmationUseCase;

    @MockitoBean
    private PaymentWebhookUseCase paymentWebhookUseCase;

    private byte[] rawSecret;

    @BeforeEach
    void setUp() {
        String secretValue = properties.webhookSecret();
        String stripped = secretValue.startsWith("whsec_")
                ? secretValue.substring("whsec_".length())
                : secretValue;
        this.rawSecret = Base64.getDecoder().decode(stripped);

        given(paymentWebhookUseCase.handleWebhook(any())).willReturn(
                new PaymentConfirmationResult(1L, "test-payment-id", PaymentStatus.COMPLETED, false));
    }

    private String sign(String id, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rawSecret, "HmacSHA256"));
            byte[] signed = (id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
            mac.update(signed);
            mac.update(body);
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private MockHttpServletRequestBuilder webhookPost(byte[] body) {
        return post(WEBHOOK_PATH).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ─── Story 3-1: 서명 검증 필터 테스트 ───

    @Test
    @DisplayName("정상 서명 → 200 OK")
    void valid_signature_returns_200() throws Exception {
        byte[] body = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(WEBHOOK_ID, ts, body);

        mockMvc.perform(webhookPost(body)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", sig))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("헤더 누락 → 401")
    void missing_headers_returns_401() throws Exception {
        byte[] body = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(webhookPost(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("서명 위조 → 401")
    void tampered_signature_returns_401() throws Exception {
        byte[] body = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String bogus = "v1," + Base64.getEncoder().encodeToString(new byte[32]);

        mockMvc.perform(webhookPost(body)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", bogus))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("body 변조 (서명은 원본 기준) → 401")
    void tampered_body_returns_401() throws Exception {
        byte[] original = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"evil-id\"}}"
                .getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(WEBHOOK_ID, ts, original);

        mockMvc.perform(webhookPost(tampered)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", sig))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("timestamp 만료(과거 10분) → 401")
    void expired_timestamp_returns_401() throws Exception {
        byte[] body = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond() - 600);
        String sig = sign(WEBHOOK_ID, ts, body);

        mockMvc.perform(webhookPost(body)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", sig))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 URL은 웹훅 필터 영향 없음 (인증 필요한 엔드포인트는 4xx)")
    void other_url_not_affected_by_webhook_filter() throws Exception {
        mockMvc.perform(get("/api/v1/payments/some-other-endpoint"))
                .andExpect(status().is4xxClientError());
    }

    // ─── Story 3-2: 핸들러 로직 통합 테스트 ───

    @Test
    @DisplayName("정상 서명 + paymentId 포함 body → handleWebhook 호출 → 200")
    void valid_signature_with_payment_id_calls_handle_webhook() throws Exception {
        byte[] body = SAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(WEBHOOK_ID, ts, body);

        mockMvc.perform(webhookPost(body)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", sig))
                .andExpect(status().isOk());

        verify(paymentWebhookUseCase).handleWebhook(eq("test-payment-id"));
    }

    @Test
    @DisplayName("정상 서명 + data.paymentId 없는 body → handleWebhook 미호출 → 200 (silent skip)")
    void valid_signature_without_payment_id_skips_handle_webhook() throws Exception {
        byte[] body = "{\"type\":\"Subscription.Scheduled\",\"data\":{}}"
                .getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = sign(WEBHOOK_ID, ts, body);

        mockMvc.perform(webhookPost(body)
                        .header("webhook-id", WEBHOOK_ID)
                        .header("webhook-timestamp", ts)
                        .header("webhook-signature", sig))
                .andExpect(status().isOk());

        verify(paymentWebhookUseCase, never()).handleWebhook(any());
    }
}
