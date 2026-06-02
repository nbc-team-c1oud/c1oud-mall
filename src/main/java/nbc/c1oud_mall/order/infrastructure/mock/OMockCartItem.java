package nbc.c1oud_mall.order.infrastructure.mock;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.product.domain.Product;

@Entity
@Table(name = "cart_item_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OMockCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne
    @JoinColumn
    private Product product;

    private Integer quantity;

}
