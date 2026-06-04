package nbc.c1oud_mall.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.payment.application.PaymentWebhookUseCase;
import nbc.c1oud_mall.payment.presentation.dto.PortOneWebhookPayload;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookUseCase paymentWebhookUseCase;

    @PostMapping("/portone")
    public ResponseEntity<Void> receivePortOne(@RequestBody PortOneWebhookPayload payload) {
        String portonePaymentId = payload.portonePaymentId();
        if (portonePaymentId == null || portonePaymentId.isBlank()) {
            log.info("Received PortOne webhook with no paymentId (type={}), skipping", payload.type());
            return ResponseEntity.ok().build();
        }
        paymentWebhookUseCase.handleWebhook(portonePaymentId);
        return ResponseEntity.ok().build();
    }
}
