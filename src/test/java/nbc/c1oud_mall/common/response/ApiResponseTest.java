package nbc.c1oud_mall.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Nested
    @DisplayName("정적 팩토리 메서드")
    class FactoryMethods {

        @Test
        @DisplayName("success(data)는 success=true·code=OK·기본 메시지·data 포함")
        void success_with_data() {
            ApiResponse<String> response = ApiResponse.success("hello");

            assertThat(response.success()).isTrue();
            assertThat(response.code()).isEqualTo("OK");
            assertThat(response.message()).isEqualTo("Success");
            assertThat(response.data()).isEqualTo("hello");
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("success(data, message)는 사용자 지정 메시지 적용")
        void success_with_custom_message() {
            ApiResponse<Integer> response = ApiResponse.success(42, "custom message");

            assertThat(response.success()).isTrue();
            assertThat(response.message()).isEqualTo("custom message");
            assertThat(response.data()).isEqualTo(42);
        }

        @Test
        @DisplayName("successNoContent는 success=true·data=null")
        void success_no_content() {
            ApiResponse<Void> response = ApiResponse.successNoContent();

            assertThat(response.success()).isTrue();
            assertThat(response.code()).isEqualTo("OK");
            assertThat(response.data()).isNull();
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("error는 success=false·코드/메시지 지정·data=null")
        void error_response() {
            ApiResponse<Void> response = ApiResponse.error("USER001", "사용자를 찾을 수 없습니다.");

            assertThat(response.success()).isFalse();
            assertThat(response.code()).isEqualTo("USER001");
            assertThat(response.message()).isEqualTo("사용자를 찾을 수 없습니다.");
            assertThat(response.data()).isNull();
            assertThat(response.timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Jackson 직렬화")
    class JsonSerialization {

        @Test
        @DisplayName("success(data) 직렬화 시 data 필드 포함")
        void serialize_success_includes_data() throws Exception {
            String json = objectMapper.writeValueAsString(ApiResponse.success("hello"));

            assertThat(json)
                    .contains("\"success\":true")
                    .contains("\"code\":\"OK\"")
                    .contains("\"message\":\"Success\"")
                    .contains("\"data\":\"hello\"")
                    .contains("\"timestamp\":");
        }

        @Test
        @DisplayName("successNoContent 직렬화 시 data 필드 제외 (@JsonInclude(NON_NULL))")
        void serialize_no_content_excludes_data() throws Exception {
            String json = objectMapper.writeValueAsString(ApiResponse.successNoContent());

            assertThat(json)
                    .doesNotContain("\"data\"")
                    .contains("\"success\":true")
                    .contains("\"code\":\"OK\"");
        }

        @Test
        @DisplayName("error 직렬화 시 data 필드 제외")
        void serialize_error_excludes_data() throws Exception {
            String json = objectMapper.writeValueAsString(
                    ApiResponse.error("USER001", "사용자 없음"));

            assertThat(json)
                    .doesNotContain("\"data\"")
                    .contains("\"success\":false")
                    .contains("\"code\":\"USER001\"")
                    .contains("\"message\":\"사용자 없음\"");
        }
    }
}
