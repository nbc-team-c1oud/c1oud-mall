package nbc.c1oud_mall.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 모든 HTTP 응답을 감싸는 공통 응답 래퍼.
 * <p>생성자 직접 호출 금지 — 반드시 정적 팩토리 메서드를 사용한다.
 * <p>{@code data}가 {@code null}인 경우 Jackson 직렬화 시 필드가 제외된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp
) {

    private static final String SUCCESS_CODE = "OK";
    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, DEFAULT_SUCCESS_MESSAGE, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, SUCCESS_CODE, message, data, Instant.now());
    }

    public static ApiResponse<Void> successNoContent() {
        return new ApiResponse<>(true, SUCCESS_CODE, DEFAULT_SUCCESS_MESSAGE, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
