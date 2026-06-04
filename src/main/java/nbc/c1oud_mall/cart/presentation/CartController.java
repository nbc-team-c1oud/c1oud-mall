package nbc.c1oud_mall.cart.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.cart.application.dto.CartItemAddRequest;
import nbc.c1oud_mall.cart.application.dto.CartItemUpdateRequest;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Void>> addCartItem(@RequestBody @Valid CartItemAddRequest request) {
        // TODO: 추후 Spring Security 커스텀 필터 구현 시 @AuthenticationPrincipal로 교체 예정
        Long memberId = 1L;

        cartService.addCartItem(memberId, request);

        return ApiResponses.created(null, URI.create("/api/v1/carts/"));
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> updateCartItemQuantity(
            @PathVariable Long cartItemId,
            @RequestBody @Valid CartItemUpdateRequest request) {
        // TODO: 추후 Spring Security 커스텀 필터 구현 시 @AuthenticationPrincipal로 교체 예정
        Long memberId = 1L;

        cartService.updateCartItemQuantity(memberId, cartItemId, request);

        return ApiResponses.noContent();
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(@PathVariable Long cartItemId) {
        Long memberId = 1L; // 연동 시 교체
        cartService.deleteCartItem(memberId, cartItemId);
        return ApiResponses.noContent();
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        Long memberId = 1L; // 연동 시 교체
        cartService.clearCart(memberId);
        return ApiResponses.noContent();
    }
}
