# 백엔드 회고 토픽 목록

> `[ ]` = 미작성 / `[x]` = 완료
> 토픽 옆 숫자는 권장 작성 순서 (의존성 없으면 원하는 순으로)

---

## 아키텍처 & 설계 원칙

- [ ] **DDD 도입 첫인상** — 도메인 객체와 JPA Entity를 왜 분리했나, 방식 A·B 선택 과정
- [ ] **레이어 경계 유지의 어려움** — 의존성 방향 위반을 잡아낸 순간과 수정 방법
- [ ] **domain 레이어에서 Spring 제거** — `@Transactional` 금지 결정과 트랜잭션 경계를 application에만 두는 이유
- [ ] **VO vs 원시 타입** — `Money`, `Email` 같은 VO를 언제 쓰고 언제 안 쓰는지 기준 정리
- [ ] **Repository port 패턴** — domain의 인터페이스 + infra 구현체 분리로 얻은 것과 비용

---

## 결제 도메인

- [ ] **PortOne 어댑터 설계** — 외부 PG 의존성을 infra에 격리한 방법, 도메인이 PG를 몰라도 되는 이유
- [ ] **멱등성 설계** — 결제 중복 방지를 위해 `requestKey` + DB UNIQUE로 해결한 과정
- [ ] **부분 취소(amount)와 전액 취소 통합** — 취소 port를 확장하면서 생긴 시그니처 변화와 하위 호환 고민
- [ ] **복합 결제 환불 금액 분리 정책** — PG floor + 포인트 잔액 흡수 결정 배경 (ADR-0008)
- [ ] **보상 트랜잭션** — PG cancel 호출이 DB 트랜잭션 밖에 있어야 하는 이유

---

## 예외 처리

- [ ] **`BusinessException` + `ErrorCode` 패턴** — 도메인별 Exception 클래스를 만들지 않는 이유
- [ ] **`ApiResponse<T>` 래퍼 통일** — 팀 전체 응답 포맷을 단일 래퍼로 고정한 결과
- [ ] **보안 민감 에러 메시지 통일** — 로그인 실패 시 `USER_NOT_FOUND`와 `PASSWORD_NOT_MATCHED`를 외부에 동일하게 노출하는 이유

---

## 테스트

- [ ] **`@WebMvcTest` 슬라이스 테스트** — Controller 레이어만 격리해서 테스트할 때 설정과 한계
- [ ] **PortOne 취소 어댑터 body 직렬화 검증** — mock 없이 실제 직렬화 결과를 검증한 방법
- [ ] **테스트 레벨 선택 기준** — 단위 / 슬라이스 / 통합 어디서 무엇을 잡아야 하나

---

## 영속성

- [ ] **방식 A(통합) vs 방식 B(분리) 팀 결정** — 도메인=JPA Entity 통합으로 팀이 합의한 과정과 트레이드오프
- [ ] **`@Embeddable` VO** — JPA Entity 안에 VO를 임베드하는 방법과 주의점
- [ ] **LAZY 로딩 기본값** — N+1 문제를 의식하면서 fetch 전략 선택한 경험

---

## 코드 품질

- [ ] **Lombok 활용 원칙** — `@Data` 금지 이유, `@Getter` + 명시적 메서드의 조합
- [ ] **record vs class** — Command/Query/Response에 record를 쓰고 JPA Entity에 쓰지 않는 이유
- [ ] **명시적 `rehydrate` 팩토리** — 도메인 객체 재구성 시 `new`가 아닌 `rehydrate`로 분리한 이유
