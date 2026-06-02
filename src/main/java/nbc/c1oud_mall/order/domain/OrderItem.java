package nbc.c1oud_mall.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.product.domain.Product;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "refunded_quantity", nullable = false)
    private Integer refundedQuantity = 0;

    public OrderItem(Product product, String productNameSnapshot, Long priceSnapshot, Integer quantity) {
        this.product = product;
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getSubtotal() {
        return priceSnapshot * quantity;
    }

    public Long getProductId(){
        return this.product.getId();
    }
}
