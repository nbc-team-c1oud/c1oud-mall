package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PortOnePaymentCancelAdapter implements PortOnePaymentCancelPort {

    private final RestClient restClient;
    private final PortOneProperties properties;

    public PortOnePaymentCancelAdapter(RestClient.Builder restClientBuilder,
                                       PortOneProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public void cancel(String portonePaymentId, Long amount, String reason, String requestKey) {
        try {
            restClient.post()
                    .uri("/payments/{paymentId}/cancel", portonePaymentId)
                    .header("Authorization", "PortOne " + properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PortOneCancelRequest(reason, amount, requestKey))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw BusinessException.withDetail(
                                ErrorCode.PORTONE_CANCEL_FAILED,
                                "status=" + res.getStatusCode().value()
                                        + ", portonePaymentId=" + portonePaymentId
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw BusinessException.withDetail(
                                ErrorCode.PORTONE_CANCEL_FAILED,
                                "status=" + res.getStatusCode().value()
                                        + ", portonePaymentId=" + portonePaymentId
                        );
                    })
                    .toBodilessEntity();
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PORTONE_CANCEL_FAILED,
                    "io: " + ex.getMessage()
            );
        } catch (RestClientException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PORTONE_CANCEL_FAILED,
                    "rest: " + ex.getMessage()
            );
        }
    }
}
