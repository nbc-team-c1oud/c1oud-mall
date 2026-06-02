package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOnePaymentCancelAdapterTest {

    private static final String BASE_URL = "http://portone.test";
    private static final String SECRET = "test-secret";
    private static final String PAYMENT_ID = "payment-test-cancel-001";
    private static final String REASON = "amount mismatch";

    private MockRestServiceServer server;
    private PortOnePaymentCancelAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        PortOneProperties properties = new PortOneProperties(BASE_URL, SECRET);
        this.adapter = new PortOnePaymentCancelAdapter(builder, properties);
    }

    @Test
    @DisplayName("200 + 정상 응답 → 예외 없이 종료, reason이 body에 포함")
    void success_no_exception() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "PortOne " + SECRET))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reason").value(REASON))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> adapter.cancel(PAYMENT_ID, REASON)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("404 → BusinessException(PM009)")
    void not_found_throws_pm009() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, REASON))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_CANCEL_FAILED);
    }

    @Test
    @DisplayName("500 → BusinessException(PM009)")
    void server_error_throws_pm009() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, REASON))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_CANCEL_FAILED);
    }

    @Test
    @DisplayName("IO 오류(타임아웃 시뮬레이션) → BusinessException(PM009)")
    void io_error_throws_pm009() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(request -> {
                    throw new IOException("simulated socket timeout");
                });

        assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, REASON))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PORTONE_CANCEL_FAILED);
    }
}
