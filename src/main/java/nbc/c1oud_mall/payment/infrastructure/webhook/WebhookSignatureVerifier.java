package nbc.c1oud_mall.payment.infrastructure.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PortOne v2 웹훅 서명 검증기 (Standard Webhooks / svix 사양).
 *
 * <p>Signed payload: {@code {webhook-id}.{webhook-timestamp}.{body}}
 * <br>HMAC-SHA256(secret, signed-payload) → base64 인코딩 → "v1,<sig>" 형식
 * <br>헤더 값에 여러 서명이 공백으로 구분되어 올 수 있고, 하나라도 일치하면 유효.
 *
 * <p>시크릿은 {@code whsec_<base64>} 형식이며 접두사 제거 후 base64 디코드한 바이트가 HMAC 키.
 */
public final class WebhookSignatureVerifier {

    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private final byte[] secretKey;
    private final Duration tolerance;
    private final Clock clock;

    public WebhookSignatureVerifier(String secretValue) {
        this(secretValue, DEFAULT_TOLERANCE, Clock.systemUTC());
    }

    public WebhookSignatureVerifier(String secretValue, Duration tolerance, Clock clock) {
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalArgumentException("webhook secret must not be blank");
        }
        String raw = secretValue.startsWith(SECRET_PREFIX)
                ? secretValue.substring(SECRET_PREFIX.length())
                : secretValue;
        try {
            this.secretKey = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("webhook secret is not valid base64", ex);
        }
        this.tolerance = tolerance;
        this.clock = clock;
    }

    /**
     * 서명·timestamp 윈도우 검증. 통과 시 정상 반환, 실패 시 {@link InvalidWebhookSignatureException}.
     *
     * @param webhookId        {@code webhook-id} 헤더 값
     * @param webhookTimestamp {@code webhook-timestamp} 헤더 값 (epoch seconds)
     * @param signatureHeader  {@code webhook-signature} 헤더 값 ("v1,<sig> v1,<sig>...")
     * @param body             요청 raw body 바이트 (UTF-8 추정)
     */
    public void verify(String webhookId, String webhookTimestamp,
                       String signatureHeader, byte[] body) {
        if (isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(signatureHeader)) {
            throw new InvalidWebhookSignatureException("missing required webhook headers");
        }
        if (body == null) {
            throw new InvalidWebhookSignatureException("body is null");
        }
        verifyTimestamp(webhookTimestamp);

        byte[] expected = computeHmac(webhookId, webhookTimestamp, body);
        if (!matchesAny(signatureHeader, expected)) {
            throw new InvalidWebhookSignatureException("signature mismatch");
        }
    }

    private void verifyTimestamp(String webhookTimestamp) {
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(webhookTimestamp.trim());
        } catch (NumberFormatException ex) {
            throw new InvalidWebhookSignatureException("webhook-timestamp is not a number", ex);
        }
        Instant sent = Instant.ofEpochSecond(epochSeconds);
        Instant now = clock.instant();
        Duration delta = Duration.between(sent, now).abs();
        if (delta.compareTo(tolerance) > 0) {
            throw new InvalidWebhookSignatureException(
                    "webhook timestamp outside tolerance: deltaSeconds=" + delta.getSeconds());
        }
    }

    private byte[] computeHmac(String webhookId, String webhookTimestamp, byte[] body) {
        byte[] idBytes = webhookId.getBytes(StandardCharsets.UTF_8);
        byte[] tsBytes = webhookTimestamp.getBytes(StandardCharsets.UTF_8);
        byte[] dot = new byte[] { '.' };

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            mac.update(idBytes);
            mac.update(dot);
            mac.update(tsBytes);
            mac.update(dot);
            mac.update(body);
            return mac.doFinal();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }

    private boolean matchesAny(String signatureHeader, byte[] expected) {
        String[] parts = signatureHeader.trim().split("\\s+");
        for (String part : parts) {
            int comma = part.indexOf(',');
            if (comma < 0) {
                continue;
            }
            String version = part.substring(0, comma);
            String value = part.substring(comma + 1);
            if (!SIGNATURE_VERSION.equals(version)) {
                continue;
            }
            byte[] candidate;
            try {
                candidate = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (MessageDigest.isEqual(candidate, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
