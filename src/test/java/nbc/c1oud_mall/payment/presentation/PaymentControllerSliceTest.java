package nbc.c1oud_mall.payment.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.common.jwt.JwtAuthFilter;
import nbc.c1oud_mall.payment.application.PaymentConfirmationUseCase;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = { JwtAuthFilter.class, PortOneWebhookSignatureFilter.class }))
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PaymentConfirmationUseCase paymentConfirmationUseCase;

    @Test
    @DisplayName("정상 확정 → 200 + 응답 본문에 paymentId/portonePaymentId/status/alreadyCompleted 매핑")
    void confirm_returns_200_with_response_body() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, "portone-id-001");
        given(paymentConfirmationUseCase.confirm(any())).willReturn(
                new PaymentConfirmationResult(42L, "portone-id-001", PaymentStatus.COMPLETED, false));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(42))
                .andExpect(jsonPath("$.data.portonePaymentId").value("portone-id-001"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.alreadyCompleted").value(false));
    }

    @Test
    @DisplayName("이미 완료된 결제 → 200 + alreadyCompleted=true")
    void confirm_idempotent_returns_already_completed_flag() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, "portone-id-002");
        given(paymentConfirmationUseCase.confirm(any())).willReturn(
                new PaymentConfirmationResult(42L, "portone-id-002", PaymentStatus.COMPLETED, true));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyCompleted").value(true));
    }

    @Test
    @DisplayName("orderId null → 400 + C001")
    void validation_fails_when_order_id_null() throws Exception {
        String body = "{\"orderId\": null, \"portonePaymentId\": \"portone-id\"}";

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("portonePaymentId 공백 → 400 + C001")
    void validation_fails_when_portone_id_blank() throws Exception {
        String body = "{\"orderId\": 1, \"portonePaymentId\": \"\"}";

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("UseCase가 BusinessException(PM010) throw → 400 + PM010 ApiResponse")
    void global_handler_maps_business_exception_to_error_code() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(999L, "portone-id-003");
        given(paymentConfirmationUseCase.confirm(any()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_MISMATCH));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PM010"));
    }

    @Test
    @DisplayName("UseCase가 BusinessException(PM006) throw → 403 + PM006")
    void global_handler_maps_authorization_failure_to_403() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, "portone-id-004");
        given(paymentConfirmationUseCase.confirm(any()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_AUTHORIZATION_FAILED));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PM006"));
    }

    @Test
    @DisplayName("UseCase가 BusinessException(PM004) throw → 502 + PM004")
    void global_handler_maps_portone_failure_to_502() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(1L, "portone-id-005");
        given(paymentConfirmationUseCase.confirm(any()))
                .willThrow(new BusinessException(ErrorCode.PORTONE_QUERY_FAILED));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PM004"));
    }
}
