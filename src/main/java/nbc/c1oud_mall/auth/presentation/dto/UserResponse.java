package nbc.c1oud_mall.auth.presentation.dto;

import java.time.LocalDateTime;

import nbc.c1oud_mall.auth.domain.entity.User;

public record UserResponse(
	Long id,
	String email,
	String name,
	String phoneNumber,
	String role,
	Long pointBalance,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getName(),
			user.getPhoneNumber(),
			user.getRole().name(),
			user.getPointBalance(),
			user.getCreatedAt(),
			user.getUpdatedAt()
		);
	}
}
