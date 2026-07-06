# [Product 1] 결제 (Payment)

## Product Vision
> 사용자가 PortOne SDK로 카드 결제 또는 카드+포인트 복합 결제를 통해 주문을 안전하게 완료할 수 있고, **서버가 결제 확정·취소·재고 반영의 최종 책임자**로서 정합성을 보장한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.payment.*` 컨텍스트가 초기 진입 상태
  - PortOne V2 SDK 연동은 클라이언트만 존재, 서버 검증 로직 부재
  - 주문·포인트·장바구니 BC와의 협력 규약 미정의
- 발생하는 문제
  - 결제 확정 시점의 3대 조건(상태·금액·멱등) 검증 부재 → 외부 성공·내부 실패 리스크
  - PortOne 웹훅 수신·서명 검증·재조회 없이는 클라이언트 응답만으로 결제 상태를 신뢰할 수 없음
  - 다중 진입점(확정 API + 웹훅)의 정합성 미보장 시 결제 이중 처리 리스크
- 왜 지금 해결해야 하는가
  - 결제는 사용자 경험의 종착점이자 매출 발생 지점 — 안정화가 최우선
  - Refund Product(다음 Product)의 전제 조건 (환불은 완결된 결제 위에서만 가능)
  - 실 결제 트래픽 발생 전에 정합성 규약 확정 필요

## 목표 (To-Be)
- `Payment` Aggregate + `PaymentStatus` (PENDING → COMPLETED / FAILED) 상태기계 도입
- 서버 사전 채번 `portonePaymentId` (UUID v4 + DB UNIQUE) — ADR 001
- 결제 확정 도메인 서비스가 확정 API·웹훅 양쪽 진입점의 **단일 진입점** — Story 2-2 / 3-2
- 외부 호출(PortOne 재조회·취소)을 트랜잭션 밖으로 분리 — ADR 002·003·004
- 웹훅 멱등성: `WebhookEvent` Aggregate + UNIQUE 제약 (INSERT-first 패턴) — ADR 005

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **`portonePaymentId` 서버 사전 채번 (UUID v4)**
  - PG 응답 대기 없이 즉시 클라이언트에 반환 → SDK가 바로 결제 진행
  - DB UNIQUE 제약으로 전역 유일성 보장
  - ADR 001 (`portone-payment-id-uuid.md`)
- **외부 호출은 트랜잭션 밖 · DB 상태 전이는 짧은 트랜잭션 안**
  - DB 락이 PG 응답을 기다리지 않음
  - PG 호출 실패 시 DB 상태와 분리해서 로그 보강 가능
  - ADR 002 (`payment-confirmation-side-effects-order.md`) · ADR 004 (`payment-confirmation-validation-order.md`)
- **결제 확정 API와 웹훅은 동일 도메인 서비스(`PaymentConfirmationService`) 재사용**
  - 두 진입점의 정합성 자동 보장
  - 웹훅 본문은 신뢰하지 않고 `portonePaymentId`만 추출해 PortOne 재조회 (본문 비신뢰 원칙)
- **웹훅 멱등성 = INSERT-first + UNIQUE 제약 (A안)**
  - Redisson 분산 락(B안)이나 DB 비관적 락(C안)보다 인프라 의존 적음
  - 감사 로그 부수 효과 획득
  - ADR 005 (`webhook-idempotency-insert-first.md`)
- **BC 간 협력은 직접 호출 (도메인 이벤트 X — 1차)**
  - 초기 규모에서는 이벤트 오버헤드 큼
  - ADR 006 (`bc-collaboration-direct-call.md`)
- **PG 보상 취소 실패 시 자동 재시도 없음 (1차)**
  - 로그 + 운영 알람만
  - ADR 003 (`portone-compensation-transaction-pattern.md`)

## 대안 검토 (Alternatives Considered)

### `portonePaymentId` 채번 위치
**Option A — 클라이언트 채번**
- 장점: 서버 트랜잭션 시작 지연 없음
- 거부 이유: 유일성 보장 어려움, 위변조 가능성

**Option B (선택) — 서버 사전 채번 (UUID v4)**
- 비용: 결제 시작이 서버 API 응답 이후로 지연 (1RTT)
- 보상: 전역 유일성 · 위변조 방지 · 서버가 결제 트랜잭션 시작점 통제

**Option C — PG가 발급한 tid 사용**
- 거부 이유: PG 응답 대기 필요 → SDK 결제 흐름 지연

### 웹훅 멱등성 전략
**Option A (선택) — INSERT-first with UNIQUE(`portonePaymentId + eventType`)**
- 비용: `WebhookEvent` Aggregate 별도 테이블 + 90일 보존 정책 필요
- 보상: 인프라 의존 없음, 감사 로그 자동 확보

**Option B — Redisson 분산 락**
- 거부 이유: Redis 의존 추가, 락 스코프 관리 부담

**Option C — DB 비관적 락 (`SELECT ... FOR UPDATE`)**
- 거부 이유: 커넥션 오래 점유, 데드락 리스크

### BC 간 협력 방식
**Option A (선택) — 직접 UseCase 호출**
- 비용: BC 간 결합도 다소 높음
- 보상: 단순, 디버깅 쉬움

**Option B — Domain Event + `@TransactionalEventListener`**
- 거부 이유: 초기 규모 오버킬, 이벤트 유실 리스크

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
Controller       PaymentService      Payment      JpaRepository
- confirm        - confirm()         Aggregate    PortOne Adapter
- webhook        - initiate()        Status enum  (RestClient)
                                     Domain Service (PaymentConfirmation)
```

### 핵심 플로우
**1. 주문 → 결제 초기화 (Epic 1)**
```
Client → OrderController.create
       → OrderService.create   ─┐
                                ├── (동일 트랜잭션)
       → PaymentInitiationPort  ─┘
       ← Response { orderId, portonePaymentId }
Client → PortOne SDK 결제
```

**2. 결제 확정 (Epic 2)**
```
Client → PaymentController.confirm(portonePaymentId)
       → PortOnePaymentQueryPort.findByPortonePaymentId  (외부 · TX 밖)
       → PaymentConfirmationService.confirm             (도메인 검증)
                                                         │
       (TX 시작)  Payment.markCompleted                   │
                  OrderService.complete                   │
                  PointService.useAndEarn                 │
                  CartService.clearItems                  │
       (TX 커밋)                                         │
       ← 200 OK
```

**3. 웹훅 수신 (Epic 3)**
```
PortOne → WebhookController (raw body 보존, HMAC 검증)
        → WebhookEvent INSERT (UNIQUE 제약)
        → PortOnePaymentQueryPort.findByPortonePaymentId
        → PaymentConfirmationService.confirm  (동일 서비스 재사용)
        ← 200
```

### Out-of-Process 의존
- **PortOne V2 REST API**: 결제 조회(`/payments/{id}`) · 결제 취소(`/payments/{id}/cancel`) — `PortOnePaymentQueryPort` / `PortOnePaymentCancelPort`
- **RDS (MySQL)**: Payment · WebhookEvent 영속화
- **PortOne 웹훅 수신 (인바운드)**: HMAC-SHA256 서명 검증

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 금액 불일치 (`totalAmount ≠ pgAmount + pointUsedAmount`) | `PAY001` | 400 | 재조회 후 새 창 재시도 |
| 결제 승인 실패 (PG 5xx) | `PAY002` | 502 | 재시도 안내 |
| Payment 소유권 위반 | `C003` (ACCESS_DENIED) | 403 | 자동 로그아웃 |
| PortOne 재조회 실패 | `PAY002` | 502 | 재시도 안내 |
| 웹훅 서명 검증 실패 | `C004` (UNAUTHORIZED) | 401 | (PortOne 재시도) |
| 이미 완료된 결제 재확정 요청 | - | 200 | 멱등 응답 (상태 변경 없음) |
| PG 보상 취소 실패 (외부 실패·내부 실패 케이스) | `PAY002` | 202 | 운영 알람 · 수동 조치 필요 |

### 로깅 정책
- **항상 기록**:
  - `requestId` (MDC), `portonePaymentId`, `paymentId`, `userId`, `status 전이`, ErrorCode, HTTP status
- **debug**: 웹훅 raw body (헤더는 masking), PortOne 재조회 응답
- **절대 금지**:
  - 카드번호 · CVC · PortOne accessToken · 웹훅 시크릿 · 개인정보 원문

### 관측 지표 (해당 시 — Metrics 도입 시)
- `payment_confirm_total{status=success|fail}` — counter — 결제 확정 성공/실패 카운트
- `payment_confirm_duration_seconds{outcome}` — histogram — 확정 API 응답 시간
- `webhook_receive_total{result=success|invalid_signature|duplicate}` — counter — 웹훅 수신 결과 분포

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- 트래픽 낮음 (샌드박스·초기 사용자)
- 일괄 배포 (단일 프로파일 전환)

### Product 의존성
- 선행: (Auth · User) — 인증 컨텍스트 필요
- 후행: **Refund Product** — 완결된 결제 위에서만 환불 가능

### Epic·Story 의존성 그래프
```
Epic 1 (주문/결제 동시 생성)
   └─► Epic 2 (결제 확정)
          └─► Epic 3 (웹훅 정합성)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| DataSource | jdbc:h2:mem | RDS endpoint (env) |
| PortOne 채널 | 테스트 채널 키 | 운영 채널 키 (env) |
| 웹훅 시크릿 | 개발용 | 운영용 (Secrets Manager 도입 시) |
| 결제 로그 레벨 | DEBUG | INFO |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 정상 흐름 결제 성공률 | ≥ 99% (샌드박스, PG 장애 제외) | `payment_confirm_total`의 success/전체 비율 |
| 결제·도메인 반영 정합성 | 100% (유실·중복 0건) | DB 감사 쿼리 (결제 완료 vs 주문 완료 조인) |
| 웹훅 멱등성 | 100% (동일 `portonePaymentId` 재수신 시 상태 변경 0건) | 통합 테스트 · 운영 로그 감사 |
| PG 보상 취소 누락 | 0건 | 검증 실패 로그 vs 취소 호출 로그 대조 |

## Scope
**In Scope**:
- PortOne V2 SDK (KG이니시스) 카드 결제
- 포인트+PG 복합 결제 (포인트 일부/전액)
- 주문/결제 동시 생성 + `portonePaymentId` 서버 사전 채번
- 결제 확정 3대 조건 검증 (상태·금액·멱등) + 본인 소유 검증
- 단일 트랜잭션 (주문 완료·Payment 완료·포인트 사용/적립·잔액·장바구니 초기화)
- 검증 실패 시 PortOne 취소 API 보상 호출 + 주문 취소 + 재고 복구
- PortOne 웹훅 수신 (HMAC-SHA256 서명 검증, 본문 비신뢰)
- 결제 확정 API + 웹훅 공통 도메인 서비스화

**Out of Scope**:
- 환불 처리 — 별도 Product ([`product-refund.md`](./product-refund.md))
- 카드 외 결제 수단 (계좌이체·휴대폰 결제) — 향후 확장
- 정기 결제·구독 — 향후 확장
- 등급별 차등 포인트 적립률 — 1차는 PG 실결제 금액 1% 고정
- PG 보상 취소 자동 재시도 — 1차는 로그 + 운영 알람만

## 대상 사용자
- **구매자**: 안전한 결제 경험 · 결제 실패 시 정확한 사유 안내 · 이중 결제 없음
- **운영자**: 정합성 있는 결제 데이터 (감사·정산 가능) · 실패 시 명확한 로그
- **개발/QA**: 단일 도메인 서비스 진입점 · 예측 가능한 상태 전이

## 연결된 Epic 목록
- [ ] Epic 1: 주문/결제 동시 생성 (`portonePaymentId` 사전 채번 + Payment PENDING)
- [ ] Epic 2: 결제 확정 (3대 조건 + 단일 트랜잭션 + 보상 취소)
- [ ] Epic 3: PortOne 웹훅 수신·처리 (서명 검증 + 멱등 공통 서비스 재사용)

## 관련 문서
- 후행 Product: [`product-refund.md`](./product-refund.md)
- 관련 ADR: 001 (`portone-payment-id-uuid`), 002 (`payment-confirmation-side-effects-order`), 003 (`portone-compensation-transaction-pattern`), 004 (`payment-confirmation-validation-order`), 005 (`webhook-idempotency-insert-first`), 006 (`bc-collaboration-direct-call`)
- 위치: `workflows/living-docs/Ai-adr/`
- CLAUDE.md / `.claude/rules/exception.md` §8 (BusinessException + ErrorCode 규범)
- Topology 참조: `workflows/topologys/idempotency-Design-Topology.md`, `tx-topology.md`, `Error-Handling-Topology.md`
- Fix 이슈 트래킹: `workflows/task/fix/brainstorming/version/0.0.1v/payment.md`

## 열린 질문 (Open Questions)
- 웹훅 처리를 동기 → 비동기(Inbox 패턴)로 전환할 트리거 시점은?
- PG 보상 취소 실패 시 자동 재시도 도입 시점 (트래픽·실패율 기준)?
- 결제 로그 → CloudWatch 필터 → 알람 파이프라인 설계 시점?
- Redis 도입 여부 (WebhookEvent UNIQUE로 충분한지 부하 검증 필요)?

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] PortOne 샌드박스 E2E: "주문 → SDK 결제 → 확정 → 완료 / 포인트 적립 / 장바구니 초기화" 통과
- [ ] 웹훅 단독 수신만으로도 동일 결과 달성 (확정 API 미호출 시나리오)
- [ ] 확정 API + 웹훅 동시·역순 수신 시 중복 처리 없음 (멱등성 통합 테스트)
- [ ] 외부 성공·내부 실패 시나리오에서 PG 보상 취소 + 주문 취소 + 재고 복구 확인
- [ ] ADR 6건 발행 완료 (001~006)
- [ ] API 스펙 문서 갱신 (결제 확정 API · 웹훅 엔드포인트)

---

# [Epic 1] 주문/결제 동시 생성

## 목표
주문 도메인이 결제를 트리거할 때, 결제 BC가 Payment Aggregate를 PENDING 상태로 생성하고 `portonePaymentId`를 사전 채번하여 반환함으로써, PortOne SDK가 즉시 결제를 진행할 수 있게 한다.

## 배경
Product의 첫 진입점. 주문 생성과 결제 생성이 동시에 성립해야 이후 확정 흐름의 전제가 성립한다.

## 포함 Story
- Story 1-1: Payment Aggregate 생성 + `portonePaymentId` 사전 채번
- Story 1-2: 결제 BC inbound port 노출 (`PaymentInitiationUseCase`)

## Epic 인수 시나리오
- Given 주문 생성 요청
- When 결제 BC가 `portonePaymentId` 채번 + Payment(PENDING) 생성
- Then 주문 응답에 `portonePaymentId` 포함, 다중 주문 생성 시에도 전역 유일성 유지

## Epic 완료 기준 (DoD)
- [ ] 소속 Story 전부 DoD 통과
- [ ] 주문 생성 통합 테스트에서 Payment(PENDING) + `portonePaymentId` 동시 채번 확인
- [ ] ADR 001 반영 확인 (채번 전략) · Payment 생성 트랜잭션 경계 문서화

---

## [Story 1-1] Payment Aggregate 생성 + `portonePaymentId` 사전 채번

### User Story
- As a 결제 BC
- I want Payment Aggregate를 PENDING 상태로 생성하고 `portonePaymentId`를 사전 채번
- so that 클라이언트가 PortOne SDK로 즉시 결제를 진행할 수 있고, 서버가 결제 트랜잭션의 시작점을 통제할 수 있다

### 설명
- `portonePaymentId`는 UUID v4 기반, DB UNIQUE 제약
- Payment 생성 시 불변식: `totalAmount = pgAmount + pointUsedAmount`
- 초기 상태: `status=PENDING`, `pointEarnedAmount=0`, `confirmedAt=null`, `pgTxId=null`
- 정적 팩토리 `Payment.of(...)`만 사용, `@NoArgsConstructor(access=PROTECTED)`
- 도메인 예외는 `BusinessException(ErrorCode.PAY001)` (금액 불일치)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.domain.Payment` — Aggregate root
- `nbc.c1oud_mall.payment.domain.PaymentStatus` — enum (PENDING/COMPLETED/FAILED)
- `nbc.c1oud_mall.payment.domain.PaymentBreakdown` — VO (`@Embeddable`)

**주요 메서드**:
- `Payment.of(orderId, userId, totalAmount, pgAmount, pointUsedAmount)` — 정적 팩토리 + 불변식 검증

### 완료 기준 (AC)
- Given 유효한 주문 정보 / When `Payment.of(...)` 호출 / Then `status=PENDING`, `portonePaymentId` 채번된 Payment 반환 · DB 저장
- *(예외 - 금액 불일치)* Given `totalAmount ≠ pgAmount + pointUsedAmount` / When `Payment.of(...)` / Then `BusinessException(PAY001)` · HTTP 400 · `ApiResponse.error("PAY001", ...)`
- *(엣지 - 채번 충돌)* Given 동일 `portonePaymentId` 존재 / When 저장 시도 / Then UNIQUE 위반 catch → 재채번 재시도 또는 명확한 예외 변환

### Definition of Done
- [ ] `Payment` Aggregate + `Payment.of(...)` 정적 팩토리 (`src/main/java/nbc/c1oud_mall/payment/domain/Payment.java`)
- [ ] `PaymentBreakdown` VO (`@Embeddable`)
- [ ] `PaymentStatus` enum
- [ ] 단위 테스트 (생성 성공 / 금액 불일치 / 채번 충돌 — 3건)
- [ ] `ErrorCode.PAY001` 등록
- [ ] ADR 001 (`portone-payment-id-uuid.md`) 작성

### 스토리 포인트
1d

### 의존성
- 선행: 없음
- 후행: Story 1-2 (Port가 이 Aggregate를 사용)

---

## [Story 1-2] 결제 BC inbound port 노출 (PaymentInitiationUseCase)

### User Story
- As a 주문 BC
- I want 결제 BC의 `PaymentInitiationUseCase` 포트를 호출
- so that 주문 생성 트랜잭션 안에서 Payment를 생성하고 `portonePaymentId`를 받을 수 있다

### 설명
- Inbound port. 주문 BC는 이 포트만 알고, 결제 BC 내부 구현은 모른다
- 시그니처: `PaymentInitiationResult initiate(PaymentInitiationCommand command)`
  - `command`: orderId, userId, totalAmount, pgAmount, pointUsedAmount
  - `result`: portonePaymentId, paymentId (내부 PK)
- 주문 생성과 동일 트랜잭션 (`REQUIRED` 전파)
- 주문 도메인이 결과의 `portonePaymentId`를 응답에 담아 반환

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.application.PaymentInitiationUseCase` — port
- `nbc.c1oud_mall.payment.application.PaymentInitiationCommand` — record
- `nbc.c1oud_mall.payment.application.PaymentInitiationResult` — record

### 완료 기준 (AC)
- Given 유효한 `PaymentInitiationCommand` / When 주문 BC가 `initiate(command)` 호출 / Then `PaymentInitiationResult` 반환
- Given Payment 생성 후 주문 저장이 실패 / When 트랜잭션 롤백 / Then Payment 레코드도 롤백 (동일 TX 검증)

### Definition of Done
- [ ] `PaymentInitiationUseCase` port + 구현체 (`PaymentInitiationService`)
- [ ] `PaymentInitiationCommand`·`PaymentInitiationResult` DTO (record)
- [ ] 슬라이스 테스트: 주문 BC mock에서 포트 호출 결과 검증
- [ ] 통합 테스트: 주문+결제 동시 생성 롤백 시 양쪽 롤백 (`@SpringBootTest`)

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1
- 후행: Order BC의 주문 생성 흐름 (외부 Product)

---

# [Epic 2] 결제 확정

## 목표
클라이언트가 PortOne SDK 결제 완료 후 확정 API를 호출했을 때, 서버가 PortOne API로 결제 정보를 재조회하여 3대 조건(상태·금액·멱등)을 검증하고, 단일 트랜잭션으로 주문 완료·포인트 사용/적립·장바구니 초기화를 처리한다. 검증 실패 시 PG 보상 취소 + 주문 취소 + 재고 복구를 수행한다.

## 배경
Product의 핵심. 외부 결제 성공을 내부 도메인 정합성으로 연결짓는 유일한 경로.

## 포함 Story
- Story 2-1: PortOne 결제 조회 outbound adapter (`PortOnePaymentQueryPort`)
- Story 2-2: 결제 확정 도메인 서비스 (정상 흐름 · 3대 조건 + 단일 트랜잭션)
- Story 2-3: 결제 확정 실패 보상 흐름 (PG 취소 + 주문 취소 + 재고 복구)
- Story 2-4: 결제 확정 API 엔드포인트 (inbound adapter)

## Epic 인수 시나리오
- Given 클라이언트 결제 확정 API 호출
- When 서버가 PortOne 재조회 → 3대 조건 통과
- Then 주문 완료·포인트·장바구니 초기화가 단일 트랜잭션 실행

*(엣지)* Given 검증 실패 / When 보상 트리거 / Then PG 취소 호출 + 주문 취소 + 재고 복구

## Epic 완료 기준 (DoD)
- [ ] 소속 Story 전부 DoD 통과
- [ ] PortOne 샌드박스 정상 결제 확정 E2E 통과
- [ ] 금액 불일치 시나리오 통합 테스트에서 보상 흐름 확인
- [ ] ADR 002·003·004 반영 확인

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **BC 간 협력**: 직접 호출 (도메인 이벤트 X) — ADR 006 확정
- **검증 순서**: 소유권 → 멱등 가드 → PortOne 재조회 → 상태 → 금액 — ADR 004

---

## [Story 2-1] PortOne 결제 조회 outbound adapter

### User Story
- As a 결제 BC
- I want PortOne V2 REST API로 `portonePaymentId`에 해당하는 결제 정보 조회
- so that 본문 데이터를 신뢰하지 않고 서버가 직접 결제 상태·승인 금액 확인 가능

### 설명
- Outbound port: `PortOnePaymentQueryPort`
- 구현체: PortOne V2 REST API (`/payments/{paymentId}`) 호출
- 응답 매핑: `status`, `amount.total`, `pgProvider`, `transactionId` → `PortOnePaymentInfo` VO
- HTTP 클라이언트: Spring `RestClient`
- 인증 토큰: 환경변수 주입, 만료/갱신은 어댑터 내부 캡슐화
- 실패 → `BusinessException(ErrorCode.PAY002)` (기존 PortOneIntegrationException 대신 통일 · CLAUDE.md §8)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.domain.PortOnePaymentQueryPort` — port
- `nbc.c1oud_mall.payment.infrastructure.PortOnePaymentAdapter` — impl
- `nbc.c1oud_mall.payment.domain.PortOnePaymentInfo` — VO

### 완료 기준 (AC)
- Given 유효한 `portonePaymentId` / When `findByPortonePaymentId(id)` / Then `PortOnePaymentInfo` 반환
- *(엣지 - PG 4xx)* Given PortOne 4xx / When 어댑터 응답 처리 / Then `BusinessException(PAY002)` · 확정 진행 안 됨
- *(엣지 - PG 5xx/timeout)* Given PortOne 5xx 또는 timeout / When 어댑터 응답 처리 / Then `BusinessException(PAY002)` · 클라이언트 재시도 안내

### Definition of Done
- [ ] `PortOnePaymentQueryPort` + `PortOnePaymentAdapter`
- [ ] `PortOnePaymentInfo` VO
- [ ] 어댑터 단위 테스트 (MockWebServer)
- [ ] 환경변수 기반 인증 토큰 주입 + dev/prod 프로파일 분리
- [ ] `ErrorCode.PAY002` 등록

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1 (Payment 존재)
- 후행: Story 2-2 (도메인 서비스가 이 port를 사용)

---

## [Story 2-2] 결제 확정 도메인 서비스 (정상 흐름)

### User Story
- As a 결제 BC
- I want 3대 조건 + 소유권 검증 후 단일 트랜잭션으로 결제 확정 부수효과 처리
- so that 외부 PG 성공이 내부 도메인 정합성으로 안전하게 반영

### 설명
- `PaymentConfirmationService`가 확정 API와 웹훅 양쪽 진입점의 **단일 진입점**
- 검증 순서 (ADR 004):
  1. Payment 조회 + 소유권 검증 (`verifyOwnership(userId)`)
  2. 멱등 가드: `payment.isCompleted()` → true면 부수효과 없이 동일 응답
  3. PortOne 재조회 (트랜잭션 밖)
  4. 상태 검증 (PortOne 응답이 성공 상태)
  5. 금액 검증 (`PortOne 승인 금액 == payment.pgAmount`)
- 단일 트랜잭션에서:
  - `Payment.markCompleted(...)` → `COMPLETED`, `pointEarnedAmount = pgAmount × 0.01`, `confirmedAt=now()`
  - Order 완료 전이 (직접 호출 — ADR 006)
  - Point 사용/적립
  - Cart 초기화

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.application.PaymentConfirmationService`
- `nbc.c1oud_mall.payment.domain.Payment.markCompleted(...)`
- `nbc.c1oud_mall.payment.domain.Payment.verifyOwnership(userId)`
- `nbc.c1oud_mall.payment.domain.Payment.verifyAmount(pgApprovedAmount)`

### 완료 기준 (AC)
- Given PENDING + PortOne 성공·금액 일치 + 본인 소유 / When `confirm(portonePaymentId, userId)` / Then Payment `COMPLETED` · 주문 완료 · 포인트 사용/적립 · 장바구니 초기화 · 단일 TX
- Given 이미 COMPLETED / When 동일 요청 재수신 / Then 상태 변경 없이 동일 성공 응답 (멱등성)
- *(예외 - 소유권 위반)* Given 다른 사용자 Payment / When 확정 요청 / Then `BusinessException(C003)` · HTTP 403 · 부수효과 없음

### Definition of Done
- [ ] `PaymentConfirmationService` 구현
- [ ] `Payment.verifyOwnership`·`verifyAmount`·`markCompleted` 도메인 메서드
- [ ] Order·Point·Cart BC 협력 인터페이스 (직접 호출 UseCase)
- [ ] 슬라이스 테스트 (정상 · 멱등 · 소유권 위반 — 3건)
- [ ] 통합 테스트: 단일 TX 보장 (중간 실패 시 전체 롤백)
- [ ] ADR 002·004·006 반영

### 스토리 포인트
2d

### 의존성
- 선행: Story 2-1
- 후행: Story 2-3 (보상 흐름) · Story 2-4 (컨트롤러)

---

## [Story 2-3] 결제 확정 실패 보상 흐름

### User Story
- As a 결제 BC
- I want 외부 성공·내부 실패 시 PG 보상 취소 + 주문 취소 + 재고 복구 수행
- so that PortOne 성공했지만 서버 검증 실패한 경우에도 정합성 유지

### 설명
- 보상 대상: 금액 불일치, 소유권 위반, 주문 이미 취소 등 검증 실패 케이스
- 처리 순서 (ADR 003):
  1. **PortOne 취소 API 호출** (트랜잭션 밖)
  2. 단일 트랜잭션: Payment `FAILED` + 주문 `CANCELLED` + 재고 복구
- 별도 서비스 `PaymentCompensationService` (Bean 분리 → 프록시 경유 · fix/payment.md Issue 2)
- 장바구니 유지 (사용자 재시도 위해)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.domain.PortOnePaymentCancelPort` — port
- `nbc.c1oud_mall.payment.application.PaymentCompensationService` — 별도 Bean
- `nbc.c1oud_mall.payment.domain.Payment.markFailed(reason)`

### 완료 기준 (AC)
- Given PortOne 성공 · 서버 금액 불일치 / When 보상 트리거 / Then PortOne 취소 호출 · Payment `FAILED` · 주문 `CANCELLED` · 재고 복구
- *(엣지)* Given 보상 시 PortOne 취소 실패 / When 응답 처리 / Then DB 상태 유지 · 에러 로그 · 운영자 식별 가능 마커
- *(엣지)* Given 보상 DB TX 실패 / When 롤백 / Then PG는 이미 취소 · DB는 원상 · 운영 알람 필요 상태 기록

### Definition of Done
- [ ] `PortOnePaymentCancelPort` + 어댑터
- [ ] `PaymentCompensationService` 별도 Bean (프록시 경유 확인)
- [ ] `Payment.markFailed(reason)`
- [ ] 슬라이스 테스트 (금액 불일치 · 소유권 위반 → 보상 호출)
- [ ] 통합 테스트: 샌드박스 금액 위조 시도 E2E
- [ ] PG 취소 실패 시 별도 ERROR 로그 마커
- [ ] ADR 003 반영

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 2-2
- 후행: 없음

---

## [Story 2-4] 결제 확정 API 엔드포인트

### User Story
- As a 클라이언트(FE)
- I want PortOne SDK 결제 완료 후 서버 확정 API 호출
- so that 결제가 서버 도메인에 안전하게 반영되었는지 확인 가능

### 설명
- Inbound HTTP: `POST /api/v1/payments/confirm`
- 요청: `PaymentConfirmRequest { orderId, portonePaymentId }` (@Valid)
- 인증 필요 (Spring Security)
- 컨트롤러는 `PaymentConfirmationService.confirm(...)` 호출만
- 응답: `ResponseEntity<ApiResponse<PaymentConfirmResponse>>` — CLAUDE.md §4

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.presentation.PaymentController`
- `nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmRequest` (record)
- `nbc.c1oud_mall.payment.presentation.dto.PaymentConfirmResponse` (record)

### 완료 기준 (AC)
- Given 인증 사용자 · 본인 소유 · 유효 요청 / When `POST /api/v1/payments/confirm` / Then 200 · `ApiResponse.success(response)`
- *(예외 - 미인증)* Given 미인증 / When 호출 / Then 401 · `ApiResponse.error("C004", ...)`
- *(예외 - 소유권)* Given 타인 `portonePaymentId` / When 호출 / Then 403 · `ApiResponse.error("C003", ...)` · 보상 미트리거

### Definition of Done
- [ ] `PaymentController` + Request/Response DTO
- [ ] Spring Security 인증 통합
- [ ] `GlobalExceptionHandler` 매핑 확인
- [ ] `@WebMvcTest` 슬라이스 테스트
- [ ] OpenAPI 문서 갱신
- [ ] E2E 테스트 (샌드박스 결제 → 확정 → 반영)

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 2-2·2-3
- 후행: 없음

---

# [Epic 3] PortOne 웹훅 수신·처리

## 목표
PortOne이 비동기로 전송하는 웹훅을 수신하여 HMAC-SHA256 서명을 검증하고, `portonePaymentId`만 추출하여 PortOne API로 재조회한 뒤, 결제 확정 API와 **동일한 도메인 서비스**를 멱등하게 호출함으로써 두 진입점의 정합성을 보장한다.

## 배경
PortOne 웹훅은 클라이언트가 확정 API를 호출하지 못한 경우(네트워크 실패·앱 종료 등)의 최후 정합성 보정 수단.

## 포함 Story
- Story 3-1: 웹훅 엔드포인트 + raw body 보존 + HMAC 서명 검증
- Story 3-2: 웹훅 핸들러 (재조회 + 공통 결제 확정 서비스 호출)
- Story 3-3: 웹훅 멱등성 보장 (확정 API와 동시·역순 수신 처리)

## Epic 인수 시나리오
- Given PortOne 웹훅 수신 · 유효 서명
- When 서명 검증 → `portonePaymentId` 추출 → PortOne 재조회 → 공통 확정 서비스 호출
- Then 결제 확정 완료 · 200 응답

*(엣지)* Given 확정 API + 웹훅 동시·역순 수신 / When 두 요청 처리 / Then 정확히 1회만 확정 · WebhookEvent UNIQUE 위반 시 멱등 응답

## Epic 완료 기준 (DoD)
- [ ] 소속 Story 전부 DoD 통과
- [ ] 샌드박스에서 웹훅 단독으로 결제 확정 E2E 통과
- [ ] 확정 API + 웹훅 동시·역순 수신 시 중복 처리 없음 확인
- [ ] ADR 005 반영 확인

---

## [Story 3-1] 웹훅 엔드포인트 + raw body 보존 + HMAC 서명 검증

### User Story
- As a 결제 BC
- I want PortOne 웹훅 요청의 raw body 보존 및 HMAC-SHA256 서명 검증
- so that 위변조 요청을 차단하고 신뢰 가능한 웹훅만 처리

### 설명
- `POST /api/v1/payments/webhooks/portone`
- raw body 보존: `ContentCachingRequestWrapper` 또는 커스텀 필터
- HMAC-SHA256: PortOne 시크릿 + raw body vs `X-PortOne-Signature` 헤더 · `MessageDigest.isEqual` 상수 시간 비교
- 검증 실패 → 401 · 보안 로그
- 본문 파싱은 `portonePaymentId` 추출 전용 DTO만
- (선택) 타임스탬프 replay 방지 (5분 이내)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.presentation.PortOneWebhookController`
- `nbc.c1oud_mall.payment.infrastructure.WebhookSignatureVerifier` — util

### 완료 기준 (AC)
- Given 유효 서명 / When `POST .../webhooks/portone` / Then 검증 통과 · Story 3-2 진행
- *(엣지 - 위변조)* Given 잘못된 서명 / When 호출 / Then 401 · 도메인 서비스 미호출 · `ApiResponse.error("C004", ...)`
- Given raw body 파싱 이후에도 보존 / When 서명 검증 / Then 성공 (파싱 부수효과 없음)

### Definition of Done
- [ ] `PortOneWebhookController`
- [ ] raw body 보존 필터/래퍼
- [ ] `WebhookSignatureVerifier` (상수 시간 비교)
- [ ] 환경변수 기반 시크릿 주입
- [ ] 검증 실패 시 보안 로그 마커
- [ ] 단위 테스트 (유효 · 위조 · 빈 body · 시크릿 누락 — 4건)

### 스토리 포인트
0.5d

### 의존성
- 선행: 없음
- 후행: Story 3-2

---

## [Story 3-2] 웹훅 핸들러 — 재조회 + 공통 결제 확정 서비스 호출

### User Story
- As a 결제 BC
- I want 검증된 웹훅에서 `portonePaymentId`만 추출 → PortOne 재조회 → 확정 API와 동일 서비스 호출
- so that 두 진입점이 동일 로직으로 정합성 있게 처리

### 설명
- 웹훅 본문에서 추출: **오직 `portonePaymentId`만**. 나머지 본문 데이터 신뢰 X (본문 비신뢰 원칙)
- `PaymentConfirmationService.confirm(portonePaymentId, userId)` — Story 2-2와 동일
- 웹훅에는 인증 컨텍스트 없음 → `userId`는 Payment 레코드에서 조회
  - 소유권 검증은 의미 약화되나 PortOne 재검증 + 멱등 가드로 커버
- 응답: 처리 완료 200, 일시적 실패 5xx (PortOne 재시도 유도)
- 1차는 동기 처리 (Inbox 패턴은 향후)

### 완료 기준 (AC)
- Given 서명 통과 웹훅 / When 핸들러가 `portonePaymentId` 추출 / Then PortOne 재조회 + `confirm(...)` 호출
- Given PortOne 재조회 결과가 본문과 다른 상태 / When 핸들러 처리 / Then PortOne 재조회 결과가 신뢰 기준 (본문 값 미사용)
- *(엣지)* Given PortOne 재조회 실패 / When 응답 / Then 5xx (PortOne 재시도 유도)

### Definition of Done
- [ ] 웹훅 핸들러 (`PaymentConfirmationService` 재사용)
- [ ] 본문 파싱은 `PortOneWebhookRequest { portonePaymentId }` 전용
- [ ] 슬라이스 테스트 (정상 · 재조회 실패 · 본문 비신뢰 — 3건)
- [ ] E2E: 샌드박스 웹훅 → 확정 완료

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 3-1 · Story 2-2
- 후행: Story 3-3 (멱등성 강화)

---

## [Story 3-3] 웹훅 멱등성 보장

### User Story
- As a 결제 BC
- I want 확정 API + 웹훅이 어떤 순서·중복으로 도착해도 결제가 정확히 1회만 확정
- so that 외부 재시도·진입점 경쟁 상황에서도 도메인 정합성 유지

### 설명
- 멱등성은 도메인 서비스의 `isCompleted()` 가드에 있지만, Race Condition 방지 위한 동시성 제어 별도 필요
- 선택: **A안 INSERT-first + UNIQUE 제약** (ADR 005)
- `WebhookEvent` Aggregate: `portonePaymentId + eventType` UNIQUE, `signature`, `receivedAt`, `processedAt`, `processStatus`
- 90일 후 아카이브 (운영 단계)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.domain.WebhookEvent`
- `nbc.c1oud_mall.payment.infrastructure.WebhookEventJpaRepository` — UNIQUE 제약

### 완료 기준 (AC)
- Given 확정 API 먼저 처리되어 COMPLETED / When 동일 `portonePaymentId` 웹훅 수신 / Then 200 · 상태 변경 없음
- Given 웹훅 처리 중 / When 동일 `portonePaymentId` 확정 API 동시 요청 / Then 한쪽만 실 처리, 정확히 1회 COMPLETED 전이
- *(엣지 - 중복 웹훅)* Given 동일 웹훅 중복 수신 / When INSERT 두 번 시도 / Then UNIQUE 위반 → 거부된 쪽은 부수효과 없이 200

### Definition of Done
- [ ] `WebhookEvent` Aggregate + UNIQUE 제약
- [ ] 웹훅 핸들러에서 INSERT-first 패턴
- [ ] `Payment.isCompleted()` 멱등 가드 재확인
- [ ] 통합 테스트: 동시·역순 수신 멀티스레드 시나리오
- [ ] 단위 테스트: UNIQUE 위반 → 멱등 응답
- [ ] ADR 005 반영

### 스토리 포인트
1d

### 의존성
- 선행: Story 3-2
- 후행: 없음
