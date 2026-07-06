## producty- 결제 topic

결제(PortOne 연동) Product에서 다뤄지는 학습 주제들을 영역별로 묶어 정리했습니다. 이 Product는 인프라가 아니라 **도메인 + 외부 시스템 통합 + 동시성**이 핵심이라 결이 다릅니다.

## 1. 헥사고날 아키텍처 (Ports & Adapters)

- Inbound port vs Outbound port 구분
- UseCase 인터페이스로 BC 경계 노출 (`PaymentInitiationUseCase`)
- 도메인 서비스(`PaymentConfirmationService`)가 inbound adapter 무관하게 재사용되는 패턴
- HTTP 컨트롤러는 얇은 어댑터, 검증·트랜잭션은 도메인 서비스에 위임
- 외부 API 어댑터 (`PortOnePaymentQueryPort`, `PortOnePaymentCancelPort`)
- 어댑터 단위 테스트 (MockWebServer / WireMock)
- 어댑터 내부 캡슐화 (HTTP 클라이언트, 인증 토큰, 토큰 갱신)
- 외부 예외를 도메인 예외로 변환 (`PortOneIntegrationException`)

## 2. DDD Aggregate 설계

- Payment Aggregate Root와 정적 팩토리(`Payment.of(...)`)
- 기본 생성자 `protected`로 외부 생성 차단
- 도메인 불변식 검증 (`totalAmount = pgAmount + pointUsedAmount`)
- VO 분리 (`PaymentBreakdown`, `PortOnePaymentInfo`)
- Aggregate 상태 전이 메서드 (`markCompleted`, `markFailed`, `verifyOwnership`, `verifyAmount`)
- 상태 Enum (`PENDING` / `COMPLETED` / `FAILED`)
- 도메인 예외 패턴 (`PaymentDomainException.of(ErrorCode)`)
- ErrorCode 체계 (`PM001` 등)

## 3. Bounded Context 간 협력

- BC 경계 명확화 (주문 BC는 결제 BC 내부 모름, 포트만 앎)
- BC 간 협력 방식 trade-off: **직접 UseCase 호출 vs 도메인 이벤트(`@TransactionalEventListener`)**
- 1차 직접 호출, v2 이벤트 기반 분리
- `PaymentConfirmed` 도메인 이벤트 후보
- 주문/결제/포인트/장바구니 BC 간 트랜잭션 경계

## 4. 외부 결제 게이트웨이(PG) 통합

- PortOne V2 SDK 클라이언트 결제 흐름
- 카드 결제 + 카드+포인트 복합 결제
- PG 인증 토큰 환경변수 주입 + 만료/갱신 정책 캡슐화
- Spring `RestClient` vs `WebClient` 선택
- PG 응답 4xx vs 5xx vs 타임아웃 처리 분기
- 재시도 정책 (1차: 단발 호출, 재시도는 클라이언트 책임)
- 샌드박스 vs 운영 프로파일 분리

## 5. 결제 확정 3대 조건 (이 Product의 시그니처)

- **상태 검증** — PortOne 응답이 성공 상태인가
- **금액 검증** — PG 승인 금액 == `payment.pgAmount`
- **멱등 가드** — `payment.isCompleted()` 검사
  - **소유권 검증** — `verifyOwnership(userId)` (추가 4번째)
- **검증 순서의 의도성** (소유권 → 멱등 → 재조회 → 상태 → 금액)
- 본문 비신뢰 원칙 (PortOne API 재조회 결과만 신뢰)

## 6. 멱등성(Idempotency) 보장 (핵심 시그니처)

- 멱등 키 설계 (`portonePaymentId` UUID v4 + DB UNIQUE)
- 도메인 레벨 멱등 가드 (`Payment.isCompleted()`) → 동일 응답 반환
- 외부 시스템 재시도 + 진입점 경쟁(확정 API vs 웹훅) 시나리오
- 결제 확정 API + 웹훅 동시·역순 수신 처리
- 정확히 1회 확정 (exactly-once semantics)

## 7. 동시성 제어 (멱등성 보강)

- **A안: INSERT-first + UNIQUE 제약** (`WebhookEvent` Aggregate, `portonePaymentId + eventType` UNIQUE)
- **B안: Redisson 분산 락** (`portonePaymentId` 키)
- **C안: DB 비관적 락** (`SELECT ... FOR UPDATE`)
- 1차 A안 선택 사유 (인프라 의존 적음 + 감사 로그 부수효과)
- 다중 인스턴스 환경에서의 동시성 trade-off
- 멀티 스레드 통합 테스트로 검증

## 8. 트랜잭션 경계 설계

- **외부 호출은 트랜잭션 밖**, DB 변경은 트랜잭션 안 (DB 락이 PG 응답 대기 금지)
- 단일 트랜잭션 부수효과 (Payment 완료 + 주문 완료 + 포인트 사용/적립 + 장바구니 초기화)
- `REQUIRED` 전파로 주문+결제 동시 생성 트랜잭션 통합
- 트랜잭션 롤백 시 Payment·주문 양쪽 모두 롤백
- PortOne 재조회는 **트랜잭션 시작 전** 수행
- 단일 트랜잭션 보장 통합 테스트

## 9. 보상 트랜잭션 (Compensating Transaction)

- 외부 성공 · 내부 실패 시나리오 방어 (가장 위험한 케이스)
- 보상 순서: PortOne 취소 API 호출(트랜잭션 밖) → 단일 트랜잭션(Payment FAILED + 주문 CANCELLED + 재고 복구)
- `PaymentCompensationService` 도메인 서비스 분리
- 선차감 재고 복구 패턴
- 장바구니는 **유지** (재시도 가능성)
- PG 취소 호출 자체 실패 시 정책 (운영자 수동 조치 + 별도 ERROR 로그 마커 + 메트릭)
- 자동 재시도 vs 배치 보강 — v2
- 보상 흐름의 멱등성 가정 (1회성)

## 10. Saga 패턴 / 분산 트랜잭션 관점

- 외부 PG와 내부 도메인 사이의 분산 트랜잭션 문제
- "PG는 성공, DB는 실패" / "DB는 성공, PG 취소 실패" 시나리오
- 보상 트랜잭션이 Saga의 핵심 구성요소
- 정합성 보장 vs 가용성 trade-off

## 11. 웹훅(Webhook) 처리

- 웹훅 엔드포인트 (`POST /api/v1/payments/webhooks/portone`)
- 비동기 외부 시스템 통보 수신
- 본문 비신뢰 원칙 — `portonePaymentId`만 추출
- PortOne 재조회로 신뢰 기준 확보
- 인증 컨텍스트 부재 → `userId`는 Payment 레코드에서 조회
- 5xx 응답으로 PortOne 자동 재시도 유도
- **동기 처리 vs Inbox 패턴(비동기)** — 1차 동기, v2 Inbox

## 12. HMAC 서명 검증 (웹훅 보안)

- HMAC-SHA256 서명 알고리즘
- **raw body 보존 필수** — 파싱 전 원본 바이트
- `ContentCachingRequestWrapper` 또는 커스텀 필터
- **상수 시간 비교** (`MessageDigest.isEqual`) — 타이밍 공격 방어
- 시크릿 키 환경변수 주입
- 서명 검증 실패 시 401 + 보안 로그
- 검증 위치 trade-off: 필터 vs 컨트롤러
- Replay 방지: 타임스탬프 비교 (5분 이내)

## 13. WebhookEvent Aggregate

- 별도 Aggregate로 웹훅 이력 보존
- 필드: `portonePaymentId`, `signature`, `receivedAt`, `processedAt`, `processStatus`
- UNIQUE 제약이 멱등성 도구 + 감사 로그 역할 겸함
- 무한 증가 → 보존 정책 (90일 후 아카이브)
- Aggregate 아카이브 배치 — v2

## 14. 진입점 통합 (이 Product의 시그니처)

- 결제 확정 API와 웹훅이 **동일한 도메인 서비스 호출**
- `PaymentConfirmationService` 단일 진입점
- 두 진입점 정합성 보장
- 진입점이 늘어나도 검증 로직 1곳에서 관리

## 15. 식별자 채번 전략

- **UUID v4 + DB UNIQUE 제약** (1차 선택)
- 대안: ULID, snowflake ID
- 서버 측 사전 채번 (클라이언트가 생성 못함)
- 채번 충돌 시 재채번 또는 도메인 예외 변환
- 글로벌 유일성 보장

## 16. API 설계 (HTTP 응답 매핑)

- 성공: 200
- 검증 실패 + 보상 완료: 400
- 미인증: 401
- 소유권 위반: 403 (보상 미트리거 — 단순 거부)
- PortOne 외부 호출 실패: 502 (재시도 가능)
- 글로벌 예외 핸들러로 도메인 예외 → HTTP 매핑
- Swagger / OpenAPI 스펙 갱신

## 17. 테스트 전략

- 단위 테스트 (Aggregate, 서명 검증 유틸)
- 슬라이스 테스트 (`@WebMvcTest`, 도메인 서비스)
- 통합 테스트 (트랜잭션 롤백, 단일 트랜잭션 보장)
- 멀티 스레드 통합 테스트 (동시·역순 수신)
- E2E (PortOne 샌드박스 결제 → 확정 → 도메인 반영)
- MockWebServer / WireMock으로 외부 API 가짜화
- 금액 위조 시도 시나리오 (보상 흐름 검증)

## 18. ADR 후보 (이 Product의 결정 밀도가 높음)

- `portonePaymentId` 채번 전략 (UUID v4 + UNIQUE)
- 결제 도메인 트랜잭션 경계
- 웹훅 멱등 키 설계 (A/B/C 비교 + A 선택)
- 외부 호출 트랜잭션 분리
- BC 간 협력 방식 (직접 호출 vs 이벤트)
- PortOne 클라이언트 선택 + 인증 토큰 관리
- 3대 조건 검증 순서
- raw body 보존 전략 + 서명 검증 위치
- 웹훅 동기 처리 vs Inbox 패턴
- PG 취소 실패 시 처리 정책
- WebhookEvent 보존 정책

## 19. v2 후보 (이번 범위 제외)

- 환불 처리 (완성된 결제 대상)
- 카드 외 결제 수단 (계좌이체, 휴대폰)
- 정기 결제 / 구독 결제
- 등급별 차등 포인트 적립률 (현재 1% 고정)
- PG 보상 취소 자동 재시도
- Inbox 패턴(비동기 처리)
- 도메인 이벤트 기반 BC 분리
- Redisson 분산 락 도입

---

가장 시그니처가 강한 학습 주제 3개를 꼽으라면:

1. **본문 비신뢰 + 3대 조건 검증 + PortOne 재조회** — "외부에서 받은 데이터는 트리거일 뿐, 진실은 서버가 직접 확인한다"는 결제 도메인의 정수. 클라이언트 검증과 서버 검증의 본질적 차이를 보여주는 가장 강한 사례.
2. **결제 확정 API와 웹훅이 동일 도메인 서비스 호출 + INSERT-first UNIQUE로 멱등성 보장** — 두 진입점·재시도·진입점 경쟁이라는 가장 까다로운 시나리오를 *코드 1벌로* 방어. Aggregate에 멱등 가드, 동시성 가드는 인프라(UNIQUE)로 분리한 책임 배분이 면접 서사로 매우 강함.
3. **외부 호출은 트랜잭션 밖 + 보상 트랜잭션으로 외부 성공·내부 실패 방어** — "DB 락이 외부 응답을 기다리면 안 된다"는 원칙과, "PG는 성공했는데 우리는 실패한" 가장 위험한 시나리오를 명시적으로 다룬다는 점.

한 가지 짚자면, 앞선 Product들(인프라 중심)과 이 Product(도메인 중심)는 **학습 결이 완전히 다릅니다**. 인프라는 "재현 가능성"이 시그니처라면 결제는 "정합성 + 멱등성"이 시그니처입니다. 면접 서사로 묶을 때 *"인프라에서는 격리·자동화, 도메인에서는 정합성·멱등성"* 두 축으로 정리하면 본인의 영역이 *풀스택 백엔드*임을 보여주기 좋습니다. 그리고 Story 2-3(보상 흐름)의 PG 취소 실패 시 처리는 학습 가치가 가장 높은 부분이니, ADR을 가장 공들여 써둘 만한 항목입니다 — "성공도 실패도 아닌 상태"를 어떻게 처리하느냐가 시니어 백엔드의 척도이기도 하니까요.