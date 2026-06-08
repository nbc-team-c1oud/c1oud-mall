# ADR-0001: portonePaymentId 채번 전략

- 상태: Accepted
- 일자: 2026-06-01
- 범위: 결제 BC (Story 1-1)

## Context

결제 진입 시 클라이언트가 PortOne SDK로 결제를 시작할 수 있도록 **서버가 결제 ID(`portonePaymentId`)를 사전 채번**해야 한다. 이 ID는 두 가지 역할을 동시에 수행한다.

1. **외부(PortOne)와의 키**: 클라이언트 SDK가 결제 요청 시 함께 보내는 식별자.
2. **내부 멱등 키**: 결제 확정 API와 웹훅이 동일 결제임을 식별하는 단일 키.

채번 전략은 다음 제약을 만족해야 한다.

- 전역 유일성: 동시 주문 생성 시에도 충돌이 사실상 발생하지 않아야 함.
- 외부 노출 안전성: enumeration 공격(연속 ID 추측)에 노출되지 않아야 함.
- 의존성 최소화: 별도 ID 생성 서비스·라이브러리에 의존하지 않아야 함.

## Decision

1. **UUID v4 채택**: `java.util.UUID.randomUUID()`로 `Payment.of(...)` 내부에서 자가 생성.
2. **DB 레벨 UNIQUE 제약 강제**: `payments.portone_payment_id`에 `uk_payments_portone_payment_id` UNIQUE 인덱스.
3. **저장 컬럼 타입**: `VARCHAR(36)` (UUID 문자열 표준 길이).
4. **채번 충돌 처리(인수 조건 #3) 1차 정책**: 1차에선 자동 재시도 도입하지 않는다. 동시 채번 race가 발생해 UNIQUE 제약 위반이 생기면 JPA가 `DataIntegrityViolationException`을 자연 발생시키고, 이는 application 레이어(Story 1-2의 `PaymentInitiationService`)에서 도메인 의미를 가진 `BusinessException(ErrorCode.PAYMENT_DUPLICATE_PAYMENT_ID, PM002, 409)`로 번역한다.
   - 도메인 layer는 인프라 예외를 모르므로(`errorhandling.md` §3) Repository에서 try-catch 하지 않는다.
   - 재시도가 필요해질 경우 별도 ADR로 결정한다.

## Alternatives

- **ULID / KSUID**: 시간 정렬성과 짧은 길이의 장점이 있으나 표준 라이브러리 외 의존성이 필요. 결제 ID는 노출 전제이고 정렬성은 결제 도메인 핵심 가치가 아니라 채택하지 않음.
- **DB 시퀀스 / auto-increment**: 외부에 노출되면 enumeration 공격이 가능. 외부 키 후보로 부적합.
- **Snowflake**: 분산 ID 생성기. 현 단계는 단일 인스턴스라 분산 ID 필요성이 없음. 인프라 비용만 증가.

## Consequences

- UUID v4 충돌 확률은 약 10⁻³⁶ 수준으로 사실상 0. 우연 충돌은 운영 인시던트로 다루면 충분.
- `portone_payment_id`는 `CHAR/VARCHAR(36)` 인덱스라 정수 PK보다 인덱스 페이지가 커진다. 결제 건수가 매우 커지면 `BINARY(16)` 변환을 검토할 수 있으며, 이는 별도 ADR로 결정한다.
- 인수 조건 #3의 "재채번 후 재시도 또는 도메인 예외 변환" 중 **변환** 옵션을 선택. 재시도는 도입하지 않음.
- 팀 컨벤션상 도메인 = JPA Entity 통합 방식이므로 `Payment` 클래스가 직접 `@Entity`로 매핑된다. Repository는 `PaymentJpaRepository`(Spring Data JPA) 하나만 infrastructure에 존재하고 application이 직접 사용한다. 영속 실패의 도메인 번역 책임은 application layer에 집중된다.

## References

- workflows/product.md — [Story 1-1] Payment Aggregate 생성 + `portonePaymentId` 사전 채번
- .claude/rules/errorhandling.md §2~§3 — 번역 지점은 External Adapter와 Application 두 곳, Repository는 try-catch 금지.
- .claude/rules/exception.md — `BusinessException + ErrorCode` 단일 패턴.
- .claude/rules/persistence.md 방식 (A) — 도메인 = JPA Entity 통합 (팀 컨벤션 합의).
