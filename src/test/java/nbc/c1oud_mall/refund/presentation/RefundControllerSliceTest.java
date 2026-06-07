package nbc.c1oud_mall.refund.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.common.jwt.JwtAuthFilter;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;
import nbc.c1oud_mall.refund.application.RefundProcessService;
import nbc.c1oud_mall.refund.application.dto.RefundResult;
import nbc.c1oud_mall.refund.domain.RefundBreakdown;
import nbc.c1oud_mall.refund.domain.RefundStatus;
import nbc.c1oud_mall.refund.presentation.dto.RefundItemRequest;
import nbc.c1oud_mall.refund.presentation.dto.RefundRequest;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RefundController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, PortOneWebhookSignatureFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class RefundControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RefundProcessService refundProcessService;

    private static final RefundBreakdown BREAKDOWN = new RefundBreakdown(9_000L, 1_000L);
    private static final RefundRequest VALID_REQUEST = new RefundRequest(
            List.of(new RefundItemRequest(1L, 2)), "단순 변심");

    @Test
    @DisplayName("PG 취소 성공 → 200 + PG_CANCELLED + warning 없음")
    void refund_returns_200_when_pg_cancelled() throws Exception {
        given(refundProcessService.process(any()))
                .willReturn(new RefundResult(42L, RefundStatus.PG_CANCELLED, BREAKDOWN));

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refundId").value(42))
                .andExpect(jsonPath("$.data.refundStatus").value("PG_CANCELLED"))
                .andExpect(jsonPath("$.data.pgRefundAmount").value(9000))
                .andExpect(jsonPath("$.data.pointRefundAmount").value(1000))
                .andExpect(jsonPath("$.data.warning").doesNotExist());
    }

    @Test
    @DisplayName("PG 취소 실패(DB_COMMITTED) → 202 + warning 포함")
    void refund_returns_202_when_db_committed() throws Exception {
        given(refundProcessService.process(any()))
                .willReturn(new RefundResult(43L, RefundStatus.DB_COMMITTED, BREAKDOWN));

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refundStatus").value("DB_COMMITTED"))
                .andExpect(jsonPath("$.data.warning").value("PG 취소 처리 진행 중. 운영팀 확인 필요"));
    }

    @Test
    @DisplayName("RF001 잔여 수량 초과 → 409 + RF001")
    void refund_returns_409_rf001_when_quantity_exceeded() throws Exception {
        given(refundProcessService.process(any()))
                .willThrow(new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED));

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RF001"));
    }

    @Test
    @DisplayName("RF002 환불 불가 상태 → 409 + RF002")
    void refund_returns_409_rf002_when_not_refundable() throws Exception {
        given(refundProcessService.process(any()))
                .willThrow(new BusinessException(ErrorCode.REFUND_NOT_REFUNDABLE_STATE));

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RF002"));
    }

    @Test
    @DisplayName("RF003 소유권 위반 → 403 + RF003")
    void refund_returns_403_rf003_when_ownership_failed() throws Exception {
        given(refundProcessService.process(any()))
                .willThrow(new BusinessException(ErrorCode.REFUND_OWNERSHIP_FAILED));

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REQUEST)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RF003"));
    }

    @Test
    @DisplayName("items 빈 배열 → 400 + C001")
    void refund_returns_400_when_items_empty() throws Exception {
        RefundRequest invalid = new RefundRequest(List.of(), "사유");

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("reason 공백 → 400 + C001")
    void refund_returns_400_when_reason_blank() throws Exception {
        RefundRequest invalid = new RefundRequest(
                List.of(new RefundItemRequest(1L, 1)), "");

        mockMvc.perform(post("/api/v1/orders/10/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
