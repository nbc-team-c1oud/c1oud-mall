package nbc.c1oud_mall.refund.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import nbc.c1oud_mall.refund.application.RefundProcessService;
import nbc.c1oud_mall.refund.application.dto.RefundResult;
import nbc.c1oud_mall.refund.presentation.dto.RefundRequest;
import nbc.c1oud_mall.refund.presentation.dto.RefundResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class RefundController {

    private final RefundProcessService refundProcessService;

    @PostMapping("/{orderId}/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> refund(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid RefundRequest request) {

        RefundResult result = refundProcessService.process(request.toCommand(orderId, userId));

        if (result.isPgCancelled()) {
            return ApiResponses.ok(RefundResponse.from(result));
        }
        return ApiResponses.accepted(RefundResponse.fromDbCommitted(result));
    }
}
