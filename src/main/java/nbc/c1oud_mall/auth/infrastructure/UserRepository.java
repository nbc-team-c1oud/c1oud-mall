package nbc.c1oud_mall.auth.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import nbc.c1oud_mall.auth.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	//회원가입 - 이메일 중복체크
	boolean existsByEmail(String email);

	//로그인 - 이메일로 유저 조회
	Optional<User> findByEmail(String email);

	// 포인트 잔액 갱신 시 비관적 락 (consistency.md §5: 포인트 잔액)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM User u WHERE u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}
