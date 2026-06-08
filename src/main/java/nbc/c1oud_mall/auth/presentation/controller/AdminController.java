package nbc.c1oud_mall.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.AdminService;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;

	@PatchMapping("/users/{userId}/role")
	public ResponseEntity<ApiResponse<Void>> promoteToAdmin(
		@PathVariable Long userId) {
		adminService.promoteToAdmin(userId);
		return ApiResponses.noContent();
	}

}
