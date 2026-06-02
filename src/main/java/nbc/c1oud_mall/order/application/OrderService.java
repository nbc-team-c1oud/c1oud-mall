package nbc.c1oud_mall.order.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.order.domain.OrderItem;
import nbc.c1oud_mall.order.infrastructure.OrderJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderJpaRepository orderJpaRepository;

    @Transactional
    public Order createOrder(User user, Long totalPrice, List<OrderItem> orderItems) {
        Order order = new Order(user, totalPrice, orderItems );
        return orderJpaRepository.save(order);
    }
}
