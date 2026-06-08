package nbc.c1oud_mall.auth.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.common.domain.BaseEntity;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

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
	private Long pointBalance;

	private LocalDateTime deletedAt;

	public User (String email, String password, String name, String phoneNumber) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.phoneNumber = phoneNumber;
		this.role = UserRole.USER;
		this.pointBalance = 0L;
	}

	public void usePoints(long amount) {
		if (amount <= 0L) {
			throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
		}
		if (this.pointBalance < amount) {
			throw BusinessException.withDetail(
					ErrorCode.POINT_INSUFFICIENT,
					"userId=" + id + ", balance=" + pointBalance + ", requested=" + amount);
		}
		this.pointBalance -= amount;
	}

	public void earnPoints(long amount) {
		if (amount <= 0L) {
			throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
		}
		this.pointBalance += amount;
	}

	/**
	 * 적립 포인트 회수용 leniency 차감 — 잔액이 amount보다 작으면 잔액까지만 차감하고
	 * 실제 차감액을 반환한다. 음수 잔액 방지가 목적 (환불 회수 흐름 전용).
	 *
	 * @return 실제로 차감된 금액. amount보다 작으면 잔액 부족.
	 */
	public long useEarnedPointsLenient(long amount) {
		if (amount <= 0L) {
			throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
		}
		long actualDeduction = Math.min(this.pointBalance, amount);
		this.pointBalance -= actualDeduction;
		return actualDeduction;
	}

}
