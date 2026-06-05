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
    public void addCartItem(Long memberId, CartItemAddRequest request) {
        Product product = productJpaRepository.findById(request.getProductId()).orElseThrow(
                () -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        );

        Optional<CartItem> optionalCartItem = cartItemJpaRepository.findByMemberIdAndProductId(memberId, product.getId());

        if (optionalCartItem.isPresent()) {
            CartItem existingItem = optionalCartItem.get();
            existingItem.addQuantity((request.getQuantity()));
        } else {
            if (product.getStockQuantity() < request.getQuantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
            CartItem newItem = CartItem.builder()
                    .memberId(memberId)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartItemJpaRepository.save(newItem);
        }
    }

    @Transactional
    public void updateCartItemQuantity(Long memberId, Long cartItemId, CartItemUpdateRequest request) {
        CartItem cartItem = cartItemJpaRepository.findById(cartItemId).orElseThrow(
                () -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)
        );

        cartItem.validateOwner(memberId);

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

    @Transactional
    public void deleteCartItem(Long memberId, Long cartItemId) {
        CartItem cartItem = cartItemJpaRepository.findById(cartItemId).orElseThrow(
                () -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)
        );

        cartItem.validateOwner(memberId);

        cartItemJpaRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(Long memberId) {
        cartItemJpaRepository.deleteAllByMemberId(memberId);
    }

    public void clearCartItems(Long userId, List<Long> orderedItemIds) {
        int deleted = cartItemJpaRepository.deleteAllByUserIdAndCartId(userId, orderedItemIds);
        if (deleted != orderedItemIds.size()) {
            log.warn("장바구니 삭제 불일치: expected={}, actual={}, memberId={}",
                    orderedItemIds.size(), deleted, userId);
        }
    }
}
