package nbc.c1oud_mall.payment.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.common.jwt.JwtAuthFilter;
import nbc.c1oud_mall.payment.application.PaymentWebhookUseCase;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;
import nbc.c1oud_mall.payment.presentation.dto.PortOneWebhookPayload;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentWebhookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = { JwtAuthFilter.class, PortOneWebhookSignatureFilter.class }))
@AutoConfigureMockMvc(addFilters = false)
class PaymentWebhookControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PaymentWebhookUseCase paymentWebhookUseCase;

    private static final String WEBHOOK_PATH = PortOneWebhookSignatureFilter.WEBHOOK_PATH;

    @Test
    @DisplayName("portonePaymentId 포함 payload → handleWebhook 호출 → 200")
    void valid_payload_calls_use_case_and_returns_200() throws Exception {
        given(paymentWebhookUseCase.handleWebhook("portone-id-001")).willReturn(
                new PaymentConfirmationResult(1L, "portone-id-001", PaymentStatus.COMPLETED, false));

        String body = objectMapper.writeValueAsString(
                new PortOneWebhookPayload("Transaction.Paid",
                        new PortOneWebhookPayload.PayloadData("portone-id-001")));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(paymentWebhookUseCase).handleWebhook("portone-id-001");
    }

    @Test
    @DisplayName("portonePaymentId 없는 payload (빈 data) → handleWebhook 미호출 → 200 (silent skip)")
    void empty_payment_id_skips_use_case_and_returns_200() throws Exception {
        String body = "{\"type\":\"Subscription.Scheduled\",\"data\":{}}";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(paymentWebhookUseCase, never()).handleWebhook(any());
    }

    @Test
    @DisplayName("handleWebhook이 PAYMENT_NOT_FOUND(PM008) throw → 404")
    void use_case_throws_payment_not_found_maps_to_404() throws Exception {
        given(paymentWebhookUseCase.handleWebhook(any()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        String body = objectMapper.writeValueAsString(
                new PortOneWebhookPayload("Transaction.Paid",
                        new PortOneWebhookPayload.PayloadData("portone-id-002")));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("handleWebhook이 PORTONE_QUERY_FAILED(PM004) throw → 502 (PortOne retry 유도)")
    void use_case_throws_portone_query_failed_maps_to_502() throws Exception {
        given(paymentWebhookUseCase.handleWebhook(any()))
                .willThrow(new BusinessException(ErrorCode.PORTONE_QUERY_FAILED));

        String body = objectMapper.writeValueAsString(
                new PortOneWebhookPayload("Transaction.Paid",
                        new PortOneWebhookPayload.PayloadData("portone-id-003")));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("handleWebhook이 alreadyCompleted=true 반환 → 200 (멱등 정상)")
    void already_completed_payment_returns_200() throws Exception {
        given(paymentWebhookUseCase.handleWebhook("portone-id-004")).willReturn(
                new PaymentConfirmationResult(2L, "portone-id-004", PaymentStatus.COMPLETED, true));

        String body = objectMapper.writeValueAsString(
                new PortOneWebhookPayload("Transaction.Paid",
                        new PortOneWebhookPayload.PayloadData("portone-id-004")));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
