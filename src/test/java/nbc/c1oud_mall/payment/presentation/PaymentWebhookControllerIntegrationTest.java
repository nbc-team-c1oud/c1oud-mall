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

import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.application.PortOnePaymentQueryPort;
import nbc.c1oud_mall.payment.infrastructure.portone.PortOneProperties;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookControllerIntegrationTest {

    private static final String WEBHOOK_PATH = PortOneWebhookSignatureFilter.WEBHOOK_PATH;
    private static final String WEBHOOK_ID = "msg_integration_001";
    private static final String SAMPLE_BODY = "{\"type\":\"Transaction.Paid\",\"data\":{}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortOneProperties properties;

    // 외부 PortOne port는 실호출 안 함
    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    private byte[] rawSecret;

    @BeforeEach
    void setUp() {
        String secretValue = properties.webhookSecret();
        // 테스트에서는 application.yml의 dev placeholder를 그대로 사용
        String stripped = secretValue.startsWith("whsec_")
                ? secretValue.substring("whsec_".length())
                : secretValue;
        this.rawSecret = Base64.getDecoder().decode(stripped);
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

    @Test
    @DisplayName("정상 서명 → 200 OK (Story 3-1 범위: stub 응답)")
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
        byte[] tampered = "{\"type\":\"Transaction.Paid\",\"data\":{\"evil\":true}}"
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
    @DisplayName("다른 URL은 웹훅 필터 영향 없음 (인증 필요한 엔드포인트는 401/403)")
    void other_url_not_affected_by_webhook_filter() throws Exception {
        // 임의 인증 필요 URL을 호출. 401 또는 403이 정상 (CORS/SecurityConfig 동작),
        // 200이 아니어야 한다 (즉 웹훅 필터 통과 후 처리되는 게 아님)
        mockMvc.perform(get("/api/v1/payments/some-other-endpoint"))
                .andExpect(status().is4xxClientError());
    }
}
