package nbc.c1oud_mall.cart.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.cart.application.dto.CartItemAddRequest;
import nbc.c1oud_mall.cart.application.dto.CartItemUpdateRequest;
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

    public List<CartItem> findCartEntities(Long userId) {
        return cartItemJpaRepository.findByUserId(userId);
    }

    public List<CartItem> findCartEntitiesByIds(Long userId, List<Long> ids) {
        return cartItemJpaRepository.findByUserIdAndCartId(userId, ids);
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

}
