package nbc.c1oud_mall.point.presentation;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.point.application.PointFacade;
import nbc.c1oud_mall.point.application.dto.PointReconciliationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointFacade pointFacade;

    @GetMapping("/reconciliation/users/{userId}")
    public ResponseEntity<ApiResponse<PointReconciliationResponse>> reconcileUserPoint(
            @PathVariable Long userId
    ) {
        PointReconciliationResponse response = pointFacade.reconcileUserPoint(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}