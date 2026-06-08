package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationServiceTest {

    private static final String PORTONE_ID = "portone-payment-compensate-001";
    private static final String REASON = "amount mismatch";

    @Mock
    private PaymentCompensationTxOp txOp;
    @Mock
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    @InjectMocks
    private PaymentCompensationService service;

    @Test
    @DisplayName("정상 보상: TxOp DB 작업 + PortOne 취소 호출")
    void compensate_calls_db_and_portone() {
        service.compensate(PORTONE_ID, REASON);

        verify(txOp).compensateDb(PORTONE_ID, REASON);
        verify(portOnePaymentCancelPort).cancel(PORTONE_ID, null, REASON, null);
    }

    @Test
    @DisplayName("PortOne 취소 실패 → log만, compensate 정상 종료 (예외 전파 X)")
    void compensate_portone_failure_swallowed() {
        doThrow(new BusinessException(ErrorCode.PORTONE_CANCEL_FAILED))
                .when(portOnePaymentCancelPort).cancel(PORTONE_ID, null, REASON, null);

        assertThatCode(() -> service.compensate(PORTONE_ID, REASON))
                .doesNotThrowAnyException();

        verify(txOp).compensateDb(PORTONE_ID, REASON);
        verify(portOnePaymentCancelPort).cancel(PORTONE_ID, null, REASON, null);
    }
}
