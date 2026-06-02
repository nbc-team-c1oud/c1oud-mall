package nbc.c1oud_mall.cart.infrastructure;

import nbc.c1oud_mall.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    // 중복 체크
    Optional<CartItem> findByMemberIdAndProductId(Long memberId, Long productId);

    //전체 비우기용
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
