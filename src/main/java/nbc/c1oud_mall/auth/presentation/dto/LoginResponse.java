package nbc.c1oud_mall.auth.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
	private String accessToken;
	private String tokenType;
}
