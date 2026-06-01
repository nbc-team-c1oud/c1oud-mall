package nbc.c1oud_mall.auth.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import nbc.c1oud_mall.auth.domain.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	//회원가입 - 이메일 중복체크
	boolean existsByEmail(String email);

	//로그인 - 이메일로 유저 조회
	Optional<User> findByEmail(String email);
}
