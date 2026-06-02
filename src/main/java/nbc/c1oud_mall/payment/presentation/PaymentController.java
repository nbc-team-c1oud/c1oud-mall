package nbc.c1oud_mall.payment.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import nbc.c1oud_mall.payment.application.PaymentConfirmationUseCase;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmRequest;
import nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentConfirmationUseCase paymentConfirmationUseCase;

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirm(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid PaymentConfirmRequest request) {
        PaymentConfirmationResult result =
                paymentConfirmationUseCase.confirm(request.toCommand(userId));
        return ApiResponses.ok(PaymentConfirmResponse.from(result));
    }
}
