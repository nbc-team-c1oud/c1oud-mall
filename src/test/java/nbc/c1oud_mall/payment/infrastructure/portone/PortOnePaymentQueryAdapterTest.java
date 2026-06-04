package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOnePaymentQueryAdapterTest {

    private static final String BASE_URL = "http://portone.test";
    private static final String SECRET = "test-secret";
    private static final String PAYMENT_ID = "payment-test-001";

    private MockRestServiceServer server;
    private PortOnePaymentQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        PortOneProperties properties = new PortOneProperties(BASE_URL, SECRET, null);
        this.adapter = new PortOnePaymentQueryAdapter(builder, properties);
    }

    @Test
    @DisplayName("200 + 정상 JSON → PortOnePaymentInfo로 매핑")
    void success_returns_mapped_info() {
        String json = """
                {
                  "status": "PAID",
                  "amount": {"total": 10000},
                  "channel": {"pgProvider": "TOSSPAYMENTS"},
                  "pgTxId": "pg-tx-001"
                }
                """;
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "PortOne " + SECRET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        PortOnePaymentInfo info = adapter.query(PAYMENT_ID);

        assertThat(info.portonePaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(info.status()).isEqualTo(PortOnePaymentStatus.PAID);
        assertThat(info.totalAmount()).isEqualTo(10_000L);
        assertThat(info.pgProvider()).isEqualTo("TOSSPAYMENTS");
        assertThat(info.pgTxId()).isEqualTo("pg-tx-001");
        server.verify();
    }

    @Test
    @DisplayName("200 + 알 수 없는 status → BusinessException(PM005)")
    void unknown_status_throws_pm005() {
        String json = """
                {
                  "status": "UNKNOWN_FANCY_STATUS",
                  "amount": {"total": 10000},
                  "channel": {"pgProvider": "TOSSPAYMENTS"},
                  "pgTxId": "pg-tx-001"
                }
                """;
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.query(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("200 + amount 누락 → BusinessException(PM005)")
    void missing_amount_throws_pm005() {
        String json = """
                {
                  "status": "PAID",
                  "channel": {"pgProvider": "TOSSPAYMENTS"},
                  "pgTxId": "pg-tx-001"
                }
                """;
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.query(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("404 → BusinessException(PM005)")
    void not_found_throws_pm005() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.query(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("500 → BusinessException(PM004)")
    void server_error_throws_pm004() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.query(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_QUERY_FAILED);
    }

    @Test
    @DisplayName("IO 오류(타임아웃 시뮬레이션) → BusinessException(PM004)")
    void io_error_throws_pm004() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID))
                .andRespond(request -> {
                    throw new IOException("simulated socket timeout");
                });

        assertThatThrownBy(() -> adapter.query(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_QUERY_FAILED);
    }
}
