package nbc.c1oud_mall.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.presentation.dto.UserResponse;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Long userId) {
		UserResponse response = userService.getMe(userId);
		return ApiResponses.ok(response);
	}

}
