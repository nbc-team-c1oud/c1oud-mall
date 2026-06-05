package nbc.c1oud_mall.cart.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.cart.application.dto.CartItemAddRequest;
import nbc.c1oud_mall.cart.application.dto.CartItemUpdateRequest;
import nbc.c1oud_mall.cart.application.dto.CartListResponse;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Void>> addCartItem(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CartItemAddRequest request) {



        cartService.addCartItem(userId, request);

        return ApiResponses.created(null, URI.create("/api/v1/carts/"));
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> updateCartItemQuantity(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cartItemId,
            @RequestBody @Valid CartItemUpdateRequest request) {

        cartService.updateCartItemQuantity(userId, cartItemId, request);

        return ApiResponses.noContent();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartListResponse>> getCartList(
            @AuthenticationPrincipal Long userId) {

        CartListResponse response = cartService.getCartList(userId);

        return ApiResponses.ok(response);
    }

    @GetMapping("/selected")
    public ResponseEntity<ApiResponse<CartListResponse>> getSelectedCartList(
            @AuthenticationPrincipal Long userId,
            @RequestParam("ids") List<Long> ids) {

        CartListResponse response = cartService.getSelectedCartList(userId, ids);

        return ApiResponses.ok(response);
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cartItemId) {
        cartService.deleteCartItem(userId, cartItemId);
        return ApiResponses.noContent();
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal Long userId) {

        cartService.clearCart(userId);
        return ApiResponses.noContent();
    }

    @DeleteMapping("/selected")
    public ResponseEntity<ApiResponse<Void>> clearCartItems(
            @AuthenticationPrincipal Long userId,
            @RequestParam("ids") List<Long> ids) {
        cartService.clearCartItems(userId, ids);

        return ApiResponses.noContent();
    }
}
