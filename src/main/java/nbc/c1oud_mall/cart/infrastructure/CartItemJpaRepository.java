package nbc.c1oud_mall.cart.infrastructure;

import nbc.c1oud_mall.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    // 중복 체크
    Optional<CartItem> findByUserIdAndProductId(Long memberId, Long productId);

    //전체 비우기용
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.userId = :memberId")
    void deleteAllByUserId(@Param("userId") Long memberId);

    //멤버 아이디가 장바구니 멤버 아이디와 일치하면 장바구니 상품 전체 불러오기
    //임시, 멤버 아이디를 연결해서 받아오는게 아님...
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.userId = :userId")
    List<CartItem> findByUserId(@Param("userId") Long userId);

    //멤버 아이디가 장바구니 멤버 아이디와 일치하고 장바구니 아이디가 요청한 장바구니 아이디와 일치하면 해당 상품 불러오기
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product WHERE ci.userId = :userId AND ci.id IN :ids")
    List<CartItem> findByUserIdAndCartId(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    //주문 생성 후 주문한 장바구니만 삭제
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.id IN :ids AND c.userId = :userId")
    int deleteAllByUserIdAndCartId(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
