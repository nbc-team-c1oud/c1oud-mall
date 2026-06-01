package nbc.c1oud_mall.order.infrastructure;

import nbc.c1oud_mall.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
}
