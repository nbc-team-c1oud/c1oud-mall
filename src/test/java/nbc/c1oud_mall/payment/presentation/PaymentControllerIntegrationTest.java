package nbc.c1oud_mall.payment.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.common.jwt.JwtUtil;
import nbc.c1oud_mall.payment.application.PortOnePaymentCancelPort;
import nbc.c1oud_mall.payment.application.PortOnePaymentQueryPort;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    private static final Long ORDER_ID = 1L;
    private static final Long OWNER_USER_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtUtil.generateToken(userId, "user@test.com", UserRole.USER);
    }

    private Payment savePending(Long orderId, Long userId, long pg, long pointUsed) {
        return paymentRepository.saveAndFlush(
                Payment.of(orderId, userId, pg + pointUsed, pg, pointUsed));
    }

    @Test
    @DisplayName("토큰 없음 → 4xx 거부 (현 SecurityConfig는 AuthenticationEntryPoint 미설정으로 403 반환)")
    void no_token_is_rejected() throws Exception {
        PaymentConfirmRequest request = new PaymentConfirmRequest(ORDER_ID, "p-1");

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("정상 토큰 + 정상 흐름 → 200 + COMPLETED + alreadyCompleted=false")
    void valid_token_and_happy_path_returns_200() throws Exception {
        Payment saved = savePending(ORDER_ID, OWNER_USER_ID, 9_000L, 1_000L);
        String portoneId = saved.getPortonePaymentId();
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 9_000L, "TOSSPAYMENTS", "pg-int-001"));

        PaymentConfirmRequest request = new PaymentConfirmRequest(ORDER_ID, portoneId);

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", bearer(OWNER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.alreadyCompleted").value(false))
                .andExpect(jsonPath("$.data.portonePaymentId").value(portoneId));
    }

    @Test
    @DisplayName("다른 사용자 토큰 → 403 + PM006 (보상 트리거되지 않음)")
    void other_user_token_returns_403() throws Exception {
        Payment saved = savePending(ORDER_ID, OWNER_USER_ID, 9_000L, 1_000L);
        String portoneId = saved.getPortonePaymentId();
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 9_000L, "TOSSPAYMENTS", "pg-int-002"));

        PaymentConfirmRequest request = new PaymentConfirmRequest(ORDER_ID, portoneId);

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", bearer(OTHER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PM006"));
    }

    @Test
    @DisplayName("잘못된 orderId → 400 + PM010")
    void wrong_order_id_returns_400_pm010() throws Exception {
        Payment saved = savePending(ORDER_ID, OWNER_USER_ID, 9_000L, 1_000L);
        String portoneId = saved.getPortonePaymentId();
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 9_000L, "TOSSPAYMENTS", "pg-int-003"));

        PaymentConfirmRequest request = new PaymentConfirmRequest(9_999L, portoneId);

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", bearer(OWNER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PM010"));
    }

    @Test
    @DisplayName("Validation 실패 (portonePaymentId 누락) → 400 + C001")
    void validation_failure_returns_400_c001() throws Exception {
        String body = "{\"orderId\": 1}";

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", bearer(OWNER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
