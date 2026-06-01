package nbc.c1oud_mall.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //@ManyToOne(fetch = FetchType.LAZY)
    @Column(name = "user_id", nullable = false)
    private Long user;

    @Column(name = "order_number", nullable = false, length = 80, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @CreatedDate
    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderAt;

    @Builder
    public Order(Long user, String orderNumber, OrderStatus orderStatus, Long totalAmount, LocalDateTime orderAt) {
        this.user = user;
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.orderAt = orderAt;
    }
}
