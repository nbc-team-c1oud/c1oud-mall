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
    // flushAutomatically=true 필수: 같은 TX 내 dirty 엔티티(Payment.markCompleted,
    // Order.markAsConfirmed, User.earnPoints 등)를 bulk DELETE 직전에 DB로 flush해야 한다.
    // 없으면 이어지는 clearAutomatically로 PC가 비워지며 변경분이 폐기됨 → confirm 200
    // 응답인데 DB는 PENDING/PENDING_PAYMENT, User.pointBalance 미반영 사일런트 버그.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

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
