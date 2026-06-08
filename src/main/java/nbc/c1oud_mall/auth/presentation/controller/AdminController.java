package nbc.c1oud_mall.auth.presentation.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.AdminService;
import nbc.c1oud_mall.auth.presentation.dto.UserResponse;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;

	//user 권한 승격
	@PatchMapping("/users/{userId}/role")
	public ResponseEntity<ApiResponse<Void>> promoteToAdmin(
		@PathVariable Long userId) {
		adminService.promoteToAdmin(userId);
		return ApiResponses.noContent();
	}

	//user 전체 조회
	@GetMapping("/users")
	public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
		@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponses.ok(adminService.getAllUsers(pageable));
	}

}
