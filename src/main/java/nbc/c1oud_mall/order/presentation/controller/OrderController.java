package nbc.c1oud_mall.order.presentation.controller;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.order.application.OrderFacade;
import nbc.c1oud_mall.order.application.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<GetOrderPreviewResponse>> getOrderPreview(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) List<Long> cartItemIds
            ) {

        GetOrderPreviewResponse response = orderFacade.getOrderPreview(userId,cartItemIds);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderCheckoutResponse>> createOrder(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) OrderCheckoutRequest request) {
        OrderCheckoutResponse response = orderFacade.createOrder(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));

    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersMe(@AuthenticationPrincipal Long userId) {
        List<OrderResponse> response = orderFacade.getOrdersMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));

    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@AuthenticationPrincipal Long userId, @PathVariable Long orderId) {
        OrderResponse response = orderFacade.getOrder(userId, orderId);
        return ResponseEntity.ok(ApiResponse.success(response));

    }


}
