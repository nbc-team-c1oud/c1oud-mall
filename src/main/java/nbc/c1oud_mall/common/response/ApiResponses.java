package nbc.c1oud_mall.common.response;

import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * {@link ResponseEntity}<code>&lt;{@link ApiResponse}&lt;T&gt;&gt;</code> 작성을 줄여주는 헬퍼.
 * 컨트롤러에서 {@code return ApiResponses.ok(data);} 형태로 사용한다.
 */
public final class ApiResponses {

    private ApiResponses() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    public static ResponseEntity<ApiResponse<Void>> noContent() {
        return ResponseEntity.ok(ApiResponse.successNoContent());
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, URI location) {
        return ResponseEntity.created(location).body(ApiResponse.success(data));
    }
}
