package nbc.c1oud_mall.cart.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.cart.application.CartService;
import nbc.c1oud_mall.cart.application.dto.CartItemAddRequest;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/carts/items")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addCartItem(@RequestBody @Valid CartItemAddRequest request) {
        // TODO: 추후 Spring Security 커스텀 필터 구현 시 @AuthenticationPrincipal로 교체 예정
        Long memberId = 1L;

        cartService.addCartItem(memberId, request);

        return ApiResponses.created(null, URI.create("/api/v1/carts/"));
    }
}
