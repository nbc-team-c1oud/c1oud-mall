package nbc.c1oud_mall.payment.infrastructure.webhook;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.payment.infrastructure.portone.PortOneProperties;

/**
 * PortOne 웹훅 엔드포인트({@value #WEBHOOK_PATH}) 진입 전에 HMAC-SHA256 서명을 검증하는 Filter.
 *
 * <p>raw body를 캐싱한 {@link CachedBodyHttpServletRequest}로 감싸서 chain에 전달하므로,
 * 컨트롤러가 본문을 다시 읽어도 안전하다.
 *
 * <p>검증 실패 시 즉시 401을 응답하고 chain은 진행하지 않는다.
 */
@Slf4j
@Component
public class PortOneWebhookSignatureFilter extends OncePerRequestFilter {

    public static final String WEBHOOK_PATH = "/api/v1/payments/webhooks/portone";

    static final String HEADER_ID = "webhook-id";
    static final String HEADER_TIMESTAMP = "webhook-timestamp";
    static final String HEADER_SIGNATURE = "webhook-signature";

    private final WebhookSignatureVerifier verifier;

    public PortOneWebhookSignatureFilter(PortOneProperties properties) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            // 시크릿 미설정 상태로는 검증 자체가 의미 없으므로 즉시 기동 실패.
            throw new IllegalStateException(
                    "portone.webhook-secret is not configured — webhook endpoint cannot be served safely");
        }
        this.verifier = new WebhookSignatureVerifier(secret);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request, body);

        try {
            verifier.verify(
                    request.getHeader(HEADER_ID),
                    request.getHeader(HEADER_TIMESTAMP),
                    request.getHeader(HEADER_SIGNATURE),
                    body);
        } catch (InvalidWebhookSignatureException ex) {
            // 외부 노출 메시지는 통일된 형태로 마스킹. 구체 사유는 로그에만.
            log.warn("PortOne webhook signature verification failed: uri={}, reason={}",
                    request.getRequestURI(), ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(cached, response);
    }
}
