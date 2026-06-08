package nbc.c1oud_mall.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.common.domain.BaseEntity;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_number", nullable = false, length = 80, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus = OrderStatus.PENDING_PAYMENT;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Builder
    public Order(User user, Long totalAmount, List<OrderItem> orderItems) {
        this.user = user;
        this.orderNumber = generateOrderNumber();
        this.totalAmount = totalAmount;
        orderItems.forEach(this::addOrderItem);
    }

    private static String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        return "ORD-" + date + "-" + random;
    }

    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    public String getOrderName() {
        if (orderItems.isEmpty()) return "주문";
        String firstName = orderItems.get(0).getProductNameSnapshot();
        if (orderItems.size() == 1) return firstName;
        return firstName + " 외 " + (orderItems.size() - 1) + "건";
    }

    public void markAsConfirmed() {
        changeStatus(OrderStatus.CONFIRMED);
    }

    public void markAsCancelled() {
        changeStatus(OrderStatus.CANCELLED);
    }

    private void changeStatus(OrderStatus newStatus) {
        if (!this.orderStatus.canTransitTo(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        this.orderStatus = newStatus;
    }
}
