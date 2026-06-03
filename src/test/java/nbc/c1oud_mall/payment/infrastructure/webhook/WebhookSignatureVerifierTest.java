package nbc.c1oud_mall.payment.infrastructure.webhook;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {

    // 테스트용 시크릿: "test-secret-key-32-bytes-padded!!" → base64 → whsec_ prefix
    private static final byte[] RAW_SECRET_BYTES =
            "test-secret-key-32-bytes-padded!!".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET_VALUE =
            "whsec_" + Base64.getEncoder().encodeToString(RAW_SECRET_BYTES);

    private static final String WEBHOOK_ID = "msg_2abcdEFG";
    private static final long FIXED_NOW_EPOCH = 1_800_000_000L; // 2027-01-15 근처
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochSecond(FIXED_NOW_EPOCH), ZoneOffset.UTC);

    private static String hmacSignature(String webhookId, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(RAW_SECRET_BYTES, "HmacSHA256"));
            byte[] signed = (webhookId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
            mac.update(signed);
            mac.update(body);
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private WebhookSignatureVerifier verifier() {
        return new WebhookSignatureVerifier(SECRET_VALUE, Duration.ofMinutes(5), FIXED_CLOCK);
    }

    @Nested
    @DisplayName("생성자 검증")
    class Construction {

        @Test
        @DisplayName("시크릿이 null이면 IllegalArgumentException")
        void null_secret_throws() {
            assertThatThrownBy(() -> new WebhookSignatureVerifier(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("시크릿이 빈 문자열이면 IllegalArgumentException")
        void blank_secret_throws() {
            assertThatThrownBy(() -> new WebhookSignatureVerifier("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("base64가 아닌 시크릿이면 IllegalArgumentException")
        void invalid_base64_throws() {
            assertThatThrownBy(() -> new WebhookSignatureVerifier("whsec_!!!not-base64!!!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("base64");
        }

        @Test
        @DisplayName("whsec_ 접두사 없이도 base64로 받아준다")
        void accepts_without_prefix() {
            String noPrefix = Base64.getEncoder().encodeToString(RAW_SECRET_BYTES);
            assertThatCode(() -> new WebhookSignatureVerifier(noPrefix))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("정상 서명")
    class ValidSignature {

        @Test
        @DisplayName("유효 서명 + timestamp 윈도우 내 → 정상 통과")
        void valid_passes() {
            byte[] body = "{\"type\":\"Transaction.Paid\"}".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatCode(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("빈 body에 대한 유효 서명도 정상 통과")
        void empty_body_valid_passes() {
            byte[] body = new byte[0];
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatCode(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("다중 서명 헤더 → 하나라도 일치하면 통과")
        void multi_signature_one_matches_passes() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String valid = hmacSignature(WEBHOOK_ID, ts, body);
            String header = "v1,YWxsZWdlZA== " + valid + " v1,b3RoZXI=";

            assertThatCode(() -> verifier().verify(WEBHOOK_ID, ts, header, body))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("서명 위조 / 변조")
    class TamperedSignature {

        @Test
        @DisplayName("body 변조 → InvalidWebhookSignatureException")
        void tampered_body_throws() {
            byte[] body = "{\"amount\":1000}".getBytes(StandardCharsets.UTF_8);
            byte[] tampered = "{\"amount\":9999}".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, sig, tampered))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("signature mismatch");
        }

        @Test
        @DisplayName("webhookId 변조 → InvalidWebhookSignatureException")
        void tampered_id_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatThrownBy(() -> verifier().verify("msg_other", ts, sig, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class);
        }

        @Test
        @DisplayName("다른 시크릿으로 서명된 헤더 → InvalidWebhookSignatureException")
        void wrong_secret_signature_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String bogusSig = "v1," + Base64.getEncoder().encodeToString(new byte[32]);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, bogusSig, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("signature mismatch");
        }

        @Test
        @DisplayName("서명 헤더 형식이 v1 prefix 누락 → InvalidWebhookSignatureException")
        void missing_version_prefix_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            // version prefix 없이 base64만
            String onlyValue = Base64.getEncoder().encodeToString(new byte[32]);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, onlyValue, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class);
        }

        @Test
        @DisplayName("v2 등 알 수 없는 버전 → InvalidWebhookSignatureException")
        void unknown_version_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH);
            String sig = "v2," + Base64.getEncoder().encodeToString(new byte[32]);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class);
        }
    }

    @Nested
    @DisplayName("헤더 누락")
    class MissingHeaders {

        @Test
        @DisplayName("webhookId 누락 → InvalidWebhookSignatureException")
        void missing_id_throws() {
            assertThatThrownBy(() -> verifier().verify(null, "1", "v1,sig", new byte[0]))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("timestamp 누락 → InvalidWebhookSignatureException")
        void missing_ts_throws() {
            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, "", "v1,sig", new byte[0]))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("signature 누락 → InvalidWebhookSignatureException")
        void missing_sig_throws() {
            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, "1", "  ", new byte[0]))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("body가 null → InvalidWebhookSignatureException")
        void null_body_throws() {
            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, "1", "v1,sig", null))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("body is null");
        }
    }

    @Nested
    @DisplayName("Timestamp 윈도우")
    class TimestampWindow {

        @Test
        @DisplayName("정확히 5분 이내(과거) → 통과")
        void within_window_past_passes() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH - 299); // 4분 59초 전
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatCode(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("정확히 5분 이내(미래) → 통과")
        void within_window_future_passes() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH + 299);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatCode(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("5분 초과(과거) → InvalidWebhookSignatureException")
        void exceed_window_past_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH - 301);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("tolerance");
        }

        @Test
        @DisplayName("5분 초과(미래) → InvalidWebhookSignatureException")
        void exceed_window_future_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            String ts = String.valueOf(FIXED_NOW_EPOCH + 301);
            String sig = hmacSignature(WEBHOOK_ID, ts, body);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, ts, sig, body))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("tolerance");
        }

        @Test
        @DisplayName("timestamp가 숫자가 아니면 InvalidWebhookSignatureException")
        void non_numeric_ts_throws() {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> verifier().verify(WEBHOOK_ID, "not-a-number", "v1,sig", body))
                    .isInstanceOf(InvalidWebhookSignatureException.class)
                    .hasMessageContaining("not a number");
        }
    }
}
