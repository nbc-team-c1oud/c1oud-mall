package nbc.c1oud_mall.product.presentation;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import nbc.c1oud_mall.product.application.ProductService;
import nbc.c1oud_mall.product.application.dto.ProductDetailResponse;
import nbc.c1oud_mall.product.application.dto.ProductListResponse;
import nbc.c1oud_mall.product.application.dto.ProductSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable Long productId) {
        ProductDetailResponse response = productService.getProduct(productId);
        return ApiResponses.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductListResponse>>> getProducts(
            @ModelAttribute ProductSearchCondition condition,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
            ) {
        Page<ProductListResponse> response = productService.getProducts(condition, pageable);
        return ApiResponses.ok(response);
    }
}
