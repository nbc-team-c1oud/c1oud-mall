package nbc.c1oud_mall.product.presentation;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;
import nbc.c1oud_mall.product.application.ProductService;
import nbc.c1oud_mall.product.application.dto.ProductResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        ProductResponse response = productService.getProduct(productId);
        return ApiResponses.ok(response);
    }
}
