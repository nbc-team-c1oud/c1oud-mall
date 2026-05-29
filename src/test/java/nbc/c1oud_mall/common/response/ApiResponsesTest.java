package nbc.c1oud_mall.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponsesTest {

    @Test
    @DisplayName("ok(data)는 200 OK 응답에 ApiResponse.success 본문")
    void ok_returns_200() {
        ResponseEntity<ApiResponse<String>> response = ApiResponses.ok("hi");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("hi");
        assertThat(response.getBody().message()).isEqualTo("Success");
    }

    @Test
    @DisplayName("ok(data, message)는 200 OK + 지정 메시지")
    void ok_with_message() {
        ResponseEntity<ApiResponse<String>> response = ApiResponses.ok("hi", "조회 완료");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("조회 완료");
    }

    @Test
    @DisplayName("noContent는 200 OK + data=null")
    void no_content_returns_200_with_null_data() {
        ResponseEntity<ApiResponse<Void>> response = ApiResponses.noContent();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("created(data, location)는 201 Created + Location 헤더 + 본문")
    void created_returns_201_with_location() {
        URI location = URI.create("/orders/1");
        ResponseEntity<ApiResponse<String>> response = ApiResponses.created("created", location);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(location);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo("created");
    }
}
