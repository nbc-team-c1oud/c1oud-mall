package nbc.c1oud_mall.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * PortOne 웹훅 수신 엔드포인트.
 *
 * <p>Story 3-1 범위: 서명 검증 통과 후 단순 200 OK만 반환.
 * <br>본문 파싱·결제 확정 호출은 Story 3-2에서 추가한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {

    @PostMapping("/portone")
    public ResponseEntity<Void> receivePortOne() {
        log.info("PortOne webhook received (signature already verified by filter)");
        return ResponseEntity.ok().build();
    }
}
