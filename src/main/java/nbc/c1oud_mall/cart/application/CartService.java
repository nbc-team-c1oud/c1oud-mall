package nbc.c1oud_mall.cart.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.cart.application.dto.CartItemAddRequest;
import nbc.c1oud_mall.cart.application.dto.CartItemResponse;
import nbc.c1oud_mall.cart.application.dto.CartItemUpdateRequest;
import nbc.c1oud_mall.cart.application.dto.CartListResponse;
import nbc.c1oud_mall.cart.domain.CartItem;
import nbc.c1oud_mall.cart.infrastructure.CartItemJpaRepository;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.infrastructure.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemJpaRepository cartItemJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    @Transactional
    public void addCartItem(Long userId, CartItemAddRequest request) {
        Product product = productJpaRepository.findById(request.getProductId()).orElseThrow(
                () -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        );

        Optional<CartItem> optionalCartItem = cartItemJpaRepository.findByUserIdAndProductId(userId, product.getId());

        if (optionalCartItem.isPresent()) {
            CartItem existingItem = optionalCartItem.get();
            existingItem.addQuantity((request.getQuantity()));
        } else {
            if (product.getStockQuantity() < request.getQuantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
            CartItem newItem = CartItem.builder()
                    .userId(userId)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartItemJpaRepository.save(newItem);
        }
    }

    @Transactional
    public void updateCartItemQuantity(Long userId, Long cartItemId, CartItemUpdateRequest request) {
        CartItem cartItem = cartItemJpaRepository.findById(cartItemId).orElseThrow(
                () -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)
        );

        cartItem.validateOwner(userId);

        cartItem.updateQuantity(request.getQuantity());
    }

    @Transactional(readOnly = true)
    public CartListResponse getCartList(Long userId) {
        List<CartItem> cartItems = cartItemJpaRepository.findByUserId(userId);

        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(CartItemResponse::new)
                .toList();

        return new CartListResponse(itemResponses);
    }

    @Transactional(readOnly = true)
    public CartListResponse getSelectedCartList(Long userId, List<Long> ids) {
        List<CartItem> cartItems = cartItemJpaRepository.findByUserIdAndCartId(userId, ids);

        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(CartItemResponse::new)
                .toList();

        return new CartListResponse(itemResponses);
    }
    // 내부 서비스 호출 전용
    @Transactional(readOnly = true)
    public List<CartItem> getValidatedCartItemsForOrder(Long userId, List<Long> cartItemIds) {
        // cartItemIds가 비어있으면 전체조회, 있으면 선택조회
        List<CartItem> cartItems = (cartItemIds == null || cartItemIds.isEmpty())
                ? cartItemJpaRepository.findByUserId(userId)
                : cartItemJpaRepository.findByUserIdAndCartId(userId, cartItemIds);

        // 장바구니가 비어있는지 검증
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // 요청한 개수와 실제 DB에서 조회된 개수가 다르면 예외(변경,조작 검증)
        if (cartItemIds != null && !cartItemIds.isEmpty() && cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        return cartItems;
    }

    @Transactional
    public void deleteCartItem(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemJpaRepository.findById(cartItemId).orElseThrow(
                () -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)
        );

        cartItem.validateOwner(userId);

        cartItemJpaRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemJpaRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void clearCartItems(Long userId, List<Long> orderedItemIds) {
        int deleted = cartItemJpaRepository.deleteAllByUserIdAndCartId(userId, orderedItemIds);
        if (deleted != orderedItemIds.size()) {
            log.warn("장바구니 삭제 불일치: expected={}, actual={}, memberId={}",
                    orderedItemIds.size(), deleted, userId);
        }
    }
}
