package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
        PortOneProperties properties = new PortOneProperties(BASE_URL, SECRET, null);
        this.adapter = new PortOnePaymentCancelAdapter(builder, properties);
    }

    @Nested
    @DisplayName("body 직렬화")
    class BodySerialization {

        @Test
        @DisplayName("전체취소 (amount=null, requestKey=null) → body에 reason만 포함, amount/requestKey 키 제외")
        void full_cancel_omits_nullable_keys() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "PortOne " + SECRET))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.reason").value(REASON))
                    .andExpect(jsonPath("$.amount").doesNotExist())
                    .andExpect(jsonPath("$.requestKey").doesNotExist())
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatCode(() -> adapter.cancel(PAYMENT_ID, null, REASON, null))
                    .doesNotThrowAnyException();
            server.verify();
        }

        @Test
        @DisplayName("부분취소 + 멱등키 모두 포함 → body에 reason/amount/requestKey 모두 포함")
        void partial_cancel_with_idempotency_includes_all_keys() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.reason").value(REASON))
                    .andExpect(jsonPath("$.amount").value(500))
                    .andExpect(jsonPath("$.requestKey").value("refund-42"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatCode(() -> adapter.cancel(PAYMENT_ID, 500L, REASON, "refund-42"))
                    .doesNotThrowAnyException();
            server.verify();
        }

        @Test
        @DisplayName("멱등키만 전달 (amount=null) → body에 reason/requestKey 포함, amount 키 제외")
        void idempotency_key_only_omits_amount() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andExpect(jsonPath("$.reason").value(REASON))
                    .andExpect(jsonPath("$.amount").doesNotExist())
                    .andExpect(jsonPath("$.requestKey").value("compensate-x"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatCode(() -> adapter.cancel(PAYMENT_ID, null, REASON, "compensate-x"))
                    .doesNotThrowAnyException();
            server.verify();
        }

        @Test
        @DisplayName("amount만 전달 (requestKey=null) → body에 reason/amount 포함, requestKey 키 제외")
        void amount_only_omits_request_key() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andExpect(jsonPath("$.reason").value(REASON))
                    .andExpect(jsonPath("$.amount").value(1000))
                    .andExpect(jsonPath("$.requestKey").doesNotExist())
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatCode(() -> adapter.cancel(PAYMENT_ID, 1000L, REASON, null))
                    .doesNotThrowAnyException();
            server.verify();
        }
    }

    @Nested
    @DisplayName("외부 응답 분기 (기존)")
    class ExternalResponses {

        @Test
        @DisplayName("404 → BusinessException(PM009)")
        void not_found_throws_pm009() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, null, REASON, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PORTONE_CANCEL_FAILED);
        }

        @Test
        @DisplayName("500 → BusinessException(PM009)")
        void server_error_throws_pm009() {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                    .andRespond(withServerError());

            assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, null, REASON, null))
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

            assertThatThrownBy(() -> adapter.cancel(PAYMENT_ID, null, REASON, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PORTONE_CANCEL_FAILED);
        }
    }
}
