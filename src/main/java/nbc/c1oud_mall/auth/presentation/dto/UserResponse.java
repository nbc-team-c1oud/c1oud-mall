package nbc.c1oud_mall.auth.presentation.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
	private Long id;
	private String email;
	private String name;
	private String phoneNumber;
	private String role;
	private int pointBalance;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
