package nbc.c1oud_mall.point.presentation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.point.application.PointService;
import nbc.c1oud_mall.point.application.dto.PointBalanceResponse;
import nbc.c1oud_mall.point.application.dto.PointHistoryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getPointBalance(
            @AuthenticationPrincipal Long userId
    ) {
        PointBalanceResponse response = pointService.getPointBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PointHistoryResponse>>> getPointHistories(
            @AuthenticationPrincipal Long userId
    ) {
        List<PointHistoryResponse> responses = pointService.getPointHistories(userId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
