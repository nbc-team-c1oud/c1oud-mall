package nbc.c1oud_mall.auth.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.AuthService;
import nbc.c1oud_mall.auth.presentation.dto.LoginRequest;
import nbc.c1oud_mall.auth.presentation.dto.LoginResponse;
import nbc.c1oud_mall.auth.presentation.dto.SignupRequest;
import nbc.c1oud_mall.common.response.ApiResponse;
import nbc.c1oud_mall.common.response.ApiResponses;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
		authService.signup(request);
		return ApiResponses.noContent(); //201대신 200 + 빈 data
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ApiResponses.ok(response);
	}

}
