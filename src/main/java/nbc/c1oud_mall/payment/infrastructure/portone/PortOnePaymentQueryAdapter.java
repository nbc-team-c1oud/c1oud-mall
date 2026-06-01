package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.PortOnePaymentQueryPort;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PortOnePaymentQueryAdapter implements PortOnePaymentQueryPort {

    private final RestClient restClient;
    private final PortOneProperties properties;

    public PortOnePaymentQueryAdapter(RestClient.Builder restClientBuilder,
                                      PortOneProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public PortOnePaymentInfo query(String portonePaymentId) {
        try {
            PortOnePaymentResponse response = restClient.get()
                    .uri("/payments/{paymentId}", portonePaymentId)
                    .header("Authorization", "PortOne " + properties.secret())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw BusinessException.withDetail(
                                ErrorCode.PORTONE_RESPONSE_INVALID,
                                "status=" + res.getStatusCode().value()
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw BusinessException.withDetail(
                                ErrorCode.PORTONE_QUERY_FAILED,
                                "status=" + res.getStatusCode().value()
                        );
                    })
                    .body(PortOnePaymentResponse.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.PORTONE_RESPONSE_INVALID);
            }
            return response.toInfo(portonePaymentId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PORTONE_QUERY_FAILED,
                    ex.getMessage()
            );
        } catch (RestClientException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PORTONE_RESPONSE_INVALID,
                    ex.getMessage()
            );
        }
    }
}
