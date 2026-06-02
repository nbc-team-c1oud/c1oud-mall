package nbc.c1oud_mall.product.infrastructure;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.product.application.dto.ProductSearchCondition;
import nbc.c1oud_mall.product.domain.Product;
import nbc.c1oud_mall.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static nbc.c1oud_mall.product.domain.QProduct.product;

@Repository
@RequiredArgsConstructor
public class ProductJpaRepositoryImpl implements ProductJpaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> findAllByCondition(ProductSearchCondition condition, Pageable pageable){
        List<Product> content = queryFactory
                .selectFrom(product)
                .where(
                        categoryEq(condition.getCategory()),
                        priceGoe(condition.getMinPrice()),
                        priceLoe(condition.getMaxPrice()),
                        statusEq(condition.getStatus())
                )
                .orderBy(getOrderSpecifiers(pageable).toArray(OrderSpecifier[]::new))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(
                        categoryEq(condition.getCategory()),
                        priceGoe(condition.getMinPrice()),
                        priceLoe(condition.getMaxPrice()),
                        statusEq(condition.getStatus())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression categoryEq(String category) {
        return category != null && !category.isBlank() ? product.category.eq(category) : null;
    }

    private BooleanExpression priceGoe(Long minPrice) {
        return minPrice != null ? product.price.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Long maxPrice) {
        return maxPrice != null ? product.price.loe(maxPrice) : null;
    }

    private BooleanExpression statusEq(ProductStatus status) {
        return status != null ? product.status.eq(status) : null;
    }

    private List<OrderSpecifier<?>> getOrderSpecifiers(Pageable pageable) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        if (pageable.getSort().isEmpty()) {
            orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, product.createdAt));
            return orderSpecifiers;
        }

        for (Sort.Order order : pageable.getSort()) {
            Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
            switch (order.getProperty()) {
                case "createdAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, product.createdAt));
                case "price" -> orderSpecifiers.add(new OrderSpecifier<>(direction, product.price));
            }
        }
        return orderSpecifiers;
    }


}
