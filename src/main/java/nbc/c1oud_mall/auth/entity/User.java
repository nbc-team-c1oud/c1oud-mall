package nbc.c1oud_mall.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.c1oud_mall.auth.UserRole;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Column(nullable = false)
	private int pointBalance;

	public User (String email, String password, String name, String phoneNumber) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.phoneNumber = phoneNumber;
		this.role = UserRole.USER;
		this.pointBalance = 0;
	}

}
