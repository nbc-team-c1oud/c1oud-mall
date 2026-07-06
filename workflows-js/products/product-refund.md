# [Product 2] 환불 (Refund)

## Product Vision
> 결제 완료된 주문에 대해 **주문상품+수량 단위**로 부분/전액 환불을 안전하게 처리하고, 포인트+PG 복합결제 환불 시 결제 수단별 금액을 분리하여 정합성 있게 복구한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.refund.*` 컨텍스트는 미존재 — 신규 생성 대상
  - Payment BC(Product 1)에서 결제 완결 흐름은 존재하나, 완결된 결제를 되돌리는 경로 부재
- 발생하는 문제
  - 사용자가 결제 후 취소 요청 시 수동 대응만 가능 → 운영 부담
  - PG+포인트 복합결제의 부분 환불 시 결제 수단별 정확한 금액 분리 규칙 부재
  - 중복 환불 방지 (같은 주문 상품 잔여 수량 초과) 규칙 부재
- 왜 지금 해결해야 하는가
  - Payment Product(선행) 완결 직후 자연스러운 연결
  - 실 결제 트래픽 발생 전 환불 규약 확정 필요

## 목표 (To-Be)
- 신규 컨텍스트 `nbc.c1oud_mall.refund.*` (presentation/application/domain/infrastructure)
- Refund Aggregate + RefundItem 모델링 (환불 단위 = 주문상품 ID + 수량)
- 서버가 결제 시점 스냅샷 × 수량으로 환불 금액 자동 산정 (클라이언트는 금액 입력 X)
- 복합결제 비율 분리 산정 (`floor` + 잔액 흡수 정책 — ADR 009)
- **선검증 → DB 갱신 트랜잭션 커밋 → PG 취소 호출** 순서 (ADR 010)
- PortOne 취소 포트 확장 (기존 재사용, 부분취소·멱등키 지원 — ADR 011)

## 설계 결정 (Design Decisions)

- **환불 단위 = OrderItem + quantity** (ADR 008)
  - 클라이언트는 금액 미입력, 서버가 스냅샷 × 수량으로 산정
- **환불 컨텍스트 분리** (`refund.*`) 
  - `refund.domain`은 `payment.domain` import 금지 (BC 경계 유지)
  - 협력은 application 단에서 record/DTO로
- **환불 금액 분할 = `floor` + 잔액 흡수** (ADR 009)
  - PG는 floor, 포인트가 1원 단위 끝수 흡수 → 사용자 손해 없음
- **DB 커밋 → PG 취소 순서** (ADR 010)
  - DB 락이 PG 응답 대기 안 함
  - PG 실패 시 DB 상태 유지 + 로그 알람만 (자동 재시도 X)
- **PortOne 취소 포트 확장** (ADR 011)
  - 기존 `PortOnePaymentCancelPort` 시그니처 확장 (`amount`, `requestKey` 추가)
- **RefundDomainException 신설 금지** (feedback/product02.md D1)
  - `BusinessException + ErrorCode.RF001~RF003` 사용

## 대안 검토 (Alternatives Considered)

### 환불 단위 결정
**Option A — 결제 트랜잭션 전체 취소만**
- 거부 이유: 부분 환불 불가

**Option B (선택) — OrderItem × quantity**
- 비용: 잔여 수량 추적 로직 필요
- 보상: 세밀한 환불 · UX 우수

**Option C — 금액 직접 입력**
- 거부 이유: 검증 어려움 · 위변조 리스크

### 환불 컨텍스트 위치
**Option A — Payment BC 내부에 `refund` 서브패키지**
- 거부 이유: 도메인 커짐, 트랜잭션 경계 모호

**Option B (선택) — 별도 `refund.*` BC** (feedback D2)
- 비용: BC 협력 코드 추가
- 보상: 도메인 경계 명확, 독립 진화

### 소수점 처리
**Option A (선택) — `floor` + 잔액 흡수**
- 비용: 포인트가 끝수 흡수
- 보상: 사용자 손해 없음, 단순

**Option B — 반올림 (`round`)**
- 거부 이유: 사용자 손해 발생 가능

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
Controller       RefundProcessService     Refund       JpaRepository
- POST refunds   - process()              Aggregate    PortOne Adapter
                                          RefundItem   (기존 포트 확장)
                                          RefundAmountCalculator
                                          InventoryRestorePort
                                          PointRestorePort
```

### 핵심 플로우
**1. 환불 요청 → 처리 (Epic 2 · Story 2-2)**
```
Client → RefundController.request
       → RefundProcessService.process
         ├── (선검증 · read-only TX)
         │   Payment 조회 + 소유권 + 상태(COMPLETED)
         │   잔여 수량 검증
         │   환불 금액 산정 (RefundAmountCalculator)
         │
         ├── (DB TX — REQUIRED)
         │   SELECT ... FOR UPDATE on payments
         │   재계산·재검증 (S+ 패턴)
         │   Refund 저장 (DB_COMMITTED)
         │   재고 복구 (InventoryRestorePort)
         │   포인트 복구 (PointRestorePort)
         │   TX 커밋
         │
         └── (외부 · TX 밖)
             PortOnePaymentCancelPort.cancel(amount, requestKey)
             ├── 성공 → Refund.markPgCancelled (단일 UPDATE)
             └── 실패 → ERROR 로그 + 알람 · Refund는 DB_COMMITTED
       ← 200 (PG 성공) 또는 202 (PG 실패)
```

### Out-of-Process 의존
- **PortOne V2 REST API**: 결제 취소 (`/payments/{id}/cancel`) — 확장된 `PortOnePaymentCancelPort`
- **RDS (MySQL)**: Refund · RefundItem 영속화 + `SELECT ... FOR UPDATE`
- (내부) Inventory · Point BC와의 협력 포트 (`InventoryRestorePort`, `PointRestorePort`)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 잔여 환불 가능 수량 초과 | `RF001` | 409 | 잔여 수량 재조회 후 재요청 |
| 환불 불가 결제 상태 (COMPLETED 아님) | `RF002` | 409 | 결제 상태 확인 |
| 소유권 위반 | `RF003` | 403 | 자동 로그아웃 |
| PortOne 취소 API 실패 | `PORTONE_CANCEL_FAILED` | 202 | "환불 처리 진행 중" 안내 · 운영팀 확인 |
| 미인증 | `C004` | 401 | 로그인 유도 |

### 로깅 정책
- **항상 기록**: `requestId`, `paymentId`, `refundId`, `userId`, `pgRefundAmount`, `pointRefundAmount`, 상태 전이
- **debug**: 환불 금액 산정 중간 단계 (비율·소수점)
- **절대 금지**: 카드번호, PortOne accessToken, 개인정보 원문
- **특수 마커**: `REFUND_PG_CANCEL_FAILED` (운영 알람 트리거)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Payment Product 완료 및 실 결제 데이터 존재
- 신규 컨텍스트라 별도 데이터 마이그레이션 없음
- 일괄 배포

### Product 의존성
- 선행: **Payment Product** (`product-payment.md`)
- 후행: 향후 관리자 환불 워크플로우 (별도 Product)

### Epic·Story 의존성 그래프
```
Epic 1 (금액 산정) ──► Epic 2 (환불 처리 트랜잭션)
   ├── Story 1-1        ├── Story 2-1 (PortOne 취소 포트 확장)
   └── Story 1-2        ├── Story 2-2 (도메인 서비스)
                        └── Story 2-3 (API 엔드포인트)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| DataSource | H2 | RDS endpoint |
| PortOne 채널 | 테스트 채널 | 운영 채널 |
| Refund 로그 레벨 | DEBUG | INFO |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 환불 정합성 (재고+포인트+PG 취소 = 산정값) | 100% | 통합 테스트 · 감사 쿼리 |
| 잔여 환불 가능 수량 초과 요청 차단 | 100% | 통합 테스트 · 프로덕션 로그 카운트 |
| 환불 금액 자동 산정 정확도 (스냅샷 × 수량 = 산정값) | 100% | 프로퍼티 기반 단위 테스트 |
| 복합결제 비율 분리 오차 | 0원 | 단위 테스트: `pgRefund + pointRefund == totalRefund` |

## Scope
**In Scope**:
- 부분 환불 및 전액 환불
- 환불 단위: **주문상품 ID + 수량**
- 서버 측 자동 산정: 결제 시점 가격 스냅샷 × 수량
- 복합결제 환불 비율 분리 (PG floor · 포인트 잔액 흡수)
- 환불 테이블에 `pgRefundAmount`·`pointRefundAmount` 별도 컬럼
- 단일 트랜잭션: 재고 복구 + 포인트 복구 + Refund 저장
- **선검증 → DB 커밋 → PG 취소** 순서 (외부 호출 TX 밖)
- 잔여 환불 가능 수량 초과 요청 거부 (RF001 409)
- 신규 컨텍스트 `nbc.c1oud_mall.refund.*`

**Out of Scope**:
- 자동 환불 (배송 지연·품절 트리거) — 향후
- 환불 사유별 정책 분기 — 사유는 입력만 받고 처리 분기 없음
- 관리자 승인 워크플로우 — 향후 별도 Product
- PG 취소 실패 자동 재시도·보상 — 1차는 로그만
- 결제 도메인 — 별도 Product ([`product-payment.md`](./product-payment.md))
- 정식 `PaymentItem` 스냅샷 — 1차는 `RefundItem`이 `OrderItem.unitPrice` 복사 보관 (v2에서 마이그레이션)

## 대상 사용자
- **구매자**: 부분 환불로 원하는 상품만 취소 · 자동 산정으로 금액 입력 부담 없음
- **운영자**: 정합성 있는 환불 데이터 · PG 취소 실패 시 명확한 알람
- **개발/QA**: 예측 가능한 상태 전이 · BC 경계 명확

## 연결된 Epic 목록
- [ ] Epic 1: 환불 금액 산정 (스냅샷 × 수량 + 복합결제 비율 분리)
- [ ] Epic 2: 환불 처리 트랜잭션 (선검증 → DB 커밋 → PG 취소)

## 관련 문서
- 선행 Product: [`product-payment.md`](./product-payment.md)
- 피드백 문서 (D1~D4 결정 반영): [`feedback/product02.md`](./feedback/product02.md)
- 관련 ADR: 007 (`refund-context-separation`), 008 (`refund-unit-orderitem-quantity`), 009 (`refund-amount-split-floor-policy`), 010 (`refund-transaction-pattern`), 011 (`portone-cancel-port-extension`)
- 위치: `workflows/living-docs/Ai-adr/`
- CLAUDE.md §4 (ApiResponse) · §8 (BusinessException + ErrorCode)
- Topology 참조: `workflows/topologys/idempotency-Design-Topology.md`, `Consistency-Design-Topology.md`
- Fix 이슈: `workflows/task/fix/brainstorming/version/0.0.1v/refund.md`

## 열린 질문 (Open Questions)
- 정식 `PaymentItem` 스냅샷 도입 시점 (v2 트리거)?
- Inventory·Point BC 포트가 실제 재고/포인트 시스템으로 이관되는 시점 (현재 Mock)?
- PG 취소 실패 시 자동 재시도 도입 트리거?

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] PortOne 샌드박스 E2E: "결제 완료 주문 → 부분 환불 → 재고/포인트/PG 부분 취소 반영" 통과
- [ ] "결제 완료 주문 → 전액 환불 → 재고/포인트 전량 복구 + PG 전체 취소" E2E 통과
- [ ] 포인트+PG 복합결제 비율 분리 통합 테스트 통과
- [ ] 잔여 수량 초과 요청 거부 검증 (엣지 테스트)
- [ ] PG 취소 실패 시 DB 유지 + 로그 확인
- [ ] ADR 007~011 발행 완료
- [ ] API 스펙 문서 갱신 (환불 요청 API)

---

# [Epic 1] 환불 금액 산정

## 목표
클라이언트가 환불 대상 **주문상품 ID + 수량**만 입력해도, 서버가 결제 시점 가격 스냅샷과 복합결제 비율을 기반으로 환불 금액(PG·포인트)을 자동 산정하고, 잔여 환불 가능 수량을 누적 추적하여 초과 환불을 차단한다.

## 배경
Product의 도메인 골격. 이후 Epic 2(트랜잭션)의 전제.

## 포함 Story
- Story 1-1: Refund Aggregate + RefundItem 모델링 (잔여 환불 수량 추적)
- Story 1-2: 환불 금액 자동 산정 (스냅샷 × 수량 + 복합결제 비율 분리)

## Epic 인수 시나리오
- Given 환불 요청 (`orderItemId + quantity` 목록)
- When 결제 시점 스냅샷 조회 → 환불 금액 산정 → 잔여 환불 가능 수량 검증
- Then 정확히 산정된 (PG · 포인트) 금액 반환, 부분 환불 누적 시에도 잔여 수량 정합성 유지

## Epic 완료 기준 (DoD)
- [ ] 소속 Story 전부 DoD 통과
- [ ] 부분 환불 누적 테스트에서 잔여 수량 정합성 확인
- [ ] 복합결제 비율 분리 단위 테스트 (소수점 정책 명확)
- [ ] ADR 008 · 009 반영

---

## [Story 1-1] Refund Aggregate + RefundItem 모델링

### User Story
- As a 결제 BC
- I want Refund Aggregate와 RefundItem Entity 설계하여 환불 요청 단위(주문상품 + 수량)를 표현하고 잔여 환불 가능 수량 누적 추적
- so that 부분 환불이 여러 번 누적되어도 초과 환불이 발생하지 않는다

### 설명
- `Refund`는 `nbc.c1oud_mall.refund.domain`에 위치. Payment와 1:N.
- 의존 방향: `refund.domain`은 `payment.domain.Payment` import 금지 — application 단 record/DTO로 협력
- 속성: `id`, `paymentId`, `userId`, `reason`, `status`, `pgRefundAmount`, `pointRefundAmount` (RefundBreakdown VO), `requestedAt`, `dbCommittedAt`, `pgCancelledAt`, `refundItems: List<RefundItem>`
- `RefundItem`: `id`, `orderItemId`, `quantity`, `priceSnapshotAtPayment`, `itemRefundAmount`
- `RefundStatus`: `REQUESTED` / `DB_COMMITTED` / `PG_CANCELLED` / `FAILED`
- 잔여 환불 가능 수량: 같은 `paymentId`의 모든 Refund의 RefundItem 수량 합산을 원 주문 수량에서 차감
- `Refund.of(payment, refundItems, reason)` 정적 팩토리, `@NoArgsConstructor(access=PROTECTED)`
- **`BusinessException(ErrorCode.RF001~003)` 사용, 별도 도메인 예외 클래스 금지** (feedback D1)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.refund.domain.Refund` — Aggregate root
- `nbc.c1oud_mall.refund.domain.RefundItem` — Entity
- `nbc.c1oud_mall.refund.domain.RefundBreakdown` — VO
- `nbc.c1oud_mall.refund.domain.RefundStatus` — enum
- `nbc.c1oud_mall.refund.domain.RefundRepository` — port

### 완료 기준 (AC)
- Given COMPLETED Payment + RefundItem 목록 / When `Refund.of(payment, refundItems, reason)` / Then `status=REQUESTED` Refund 생성 · 스냅샷 보존
- *(엣지 - 초과)* Given 이전 환불 누적 수량이 원 주문 수량과 동일 / When 추가 환불 요청 / Then `BusinessException(RF001)` · HTTP 409
- *(엣지 - 상태)* Given Payment 상태가 COMPLETED 아님 / When Refund 생성 시도 / Then `BusinessException(RF002)` · HTTP 409

### Definition of Done
- [ ] `refund` 컨텍스트 신규 패키지 골격
- [ ] `Refund` Aggregate + `Refund.of(...)`
- [ ] `RefundItem` Entity (패키지 프라이빗 생성자)
- [ ] `RefundBreakdown` VO (`@Embeddable`)
- [ ] `RefundStatus` enum
- [ ] `RefundRepository` (port + impl) — `sumRefundedQuantity(paymentId, orderItemId): long` SUM 쿼리
- [ ] 단위 테스트: 정상 · RF001 · RF002 (3건)
- [ ] `ErrorCode.RF001`·`RF002`·`RF003` 등록

### 스토리 포인트
1d

### 의존성
- 선행: Payment Product (Payment 존재)
- 후행: Story 1-2 · Story 2-2

### [명세 변경 이력]
- 2026-06-XX: 원안의 `RefundDomainException` → `BusinessException(ErrorCode.RF*)` (feedback/product02.md §1-1 반영)
- 2026-06-XX: RF001 응답 400 → 409 (feedback §1-2 반영)

---

## [Story 1-2] 환불 금액 자동 산정 (가격 스냅샷 × 수량 + 복합결제 비율 분리)

### User Story
- As a 결제 BC
- I want 결제 시점 가격 스냅샷 × 수량으로 환불 금액 자동 산정 · 복합결제 시 비율 분리
- so that 클라이언트는 금액 입력 없이 환불 대상만 지정하면 되고, 복합결제도 정합성 유지

### 설명
- 가격 스냅샷: 1차는 RefundItem 생성 시 `OrderItem.unitPrice` 복사 보관 (정식 `PaymentItem` 스냅샷은 v2)
- `RefundAmountCalculator` 도메인 서비스가 산정 책임
- 총 환불: `Σ (priceSnapshotAtPayment × quantity)`
- 비율 분리:
  - `pgRatio = payment.pgAmount / payment.totalAmount`
  - `pointRatio = payment.pointUsedAmount / payment.totalAmount`
  - `pgRefundAmount = floor(totalRefundAmount × pgRatio)`
  - `pointRefundAmount = totalRefundAmount - pgRefundAmount` (잔액 흡수)
- 정책: PG floor + 포인트 잔액 흡수 (ADR 009)
- 산정 결과는 `Refund.of(..., breakdown)` 생성 시 주입 (불변)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.refund.domain.RefundAmountCalculator` — 도메인 서비스 (순수 함수형)

### 완료 기준 (AC)
- Given PG 8000 + 포인트 2000 = 10000 결제, 5000 환불 대상 / When `calculate(payment, refundItems)` / Then `pgRefund=4000, pointRefund=1000` (8:2)
- *(엣지 - 소수점)* Given PG 7500 + 포인트 2500, 환불 3333 / When 산정 / Then `pgRefund=2499(floor), pointRefund=834(잔액)` · 합 3333 일치
- *(엣지 - 포인트 전액)* Given 포인트만 전액 결제 / When 산정 / Then `pgRefund=0, pointRefund=전액`

### Definition of Done
- [ ] `RefundAmountCalculator` 구현
- [ ] 소수점 정책 반영 (floor + 잔액)
- [ ] 단위 테스트: PG-only · 포인트-only · 복합 (정확 / 소수점 / 1원 경계) — 5+건
- [ ] 프로퍼티 기반 테스트: `pgRefund + pointRefund == totalRefund` 항상 보장
- [ ] ADR 009 작성

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1
- 후행: Story 2-2

---

# [Epic 2] 환불 처리 트랜잭션

## 목표
환불 요청을 받았을 때 **선검증 → DB 갱신 트랜잭션 커밋 → PG 취소 호출** 순서로 처리하여, 단일 트랜잭션 안에서 재고 복구·포인트 복구를 일관성 있게 반영하고 외부 PG 호출은 트랜잭션 밖으로 분리함으로써 DB 락이 PG 응답을 기다리지 않도록 한다.

## 배경
Product의 실행 본체. Epic 1의 도메인 모델 위에서 트랜잭션·외부 호출 흐름을 조율.

## 포함 Story
- Story 2-1: PortOne 부분/전체 결제 취소 outbound adapter (기존 포트 확장)
- Story 2-2: 환불 처리 도메인 서비스 (선검증 → DB 커밋 → PG 취소 호출)
- Story 2-3: 환불 요청 API 엔드포인트 (inbound adapter)

## Epic 인수 시나리오
- Given 환불 요청
- When 선검증 → DB 트랜잭션 커밋 (재고·포인트·Refund) → PG 취소 → 응답 기반 Refund 상태 갱신
- Then Refund `PG_CANCELLED` 상태로 마무리

*(엣지)* Given PG 취소 실패 / When 호출 예외 / Then DB 유지 · Refund `DB_COMMITTED` 유지 · 알람

## Epic 완료 기준 (DoD)
- [ ] 소속 Story 전부 DoD 통과
- [ ] PortOne 샌드박스 부분·전액 환불 E2E 통과
- [ ] PG 취소 실패 시 DB 유지 + 알람 통합 테스트
- [ ] 동시 환불 race 통합 테스트 (비관적 락 검증)
- [ ] 멱등 통합 테스트 (동일 요청 2회 → 두 번째 409)
- [ ] ADR 010 · 011 반영

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **PG 취소 포트**: 기존 `PortOnePaymentCancelPort` 확장 (신규 X — feedback D3 → ADR 011)
- **동시성 제어**: `SELECT ... FOR UPDATE on payments` (락 순서 Order → Payment → Point → Inventory)
- **PG 실패 처리**: 자동 재시도 X · 로그 + 알람만

---

## [Story 2-1] PortOne 부분/전체 결제 취소 outbound adapter (기존 포트 확장)

### User Story
- As a 결제 BC
- I want PortOne V2 REST API로 결제 전체 또는 부분 금액 취소
- so that 환불 처리 시 PG 측에서도 결제 취소 반영

### 설명
- **기존 포트 확장** (신규 작성 X — ADR 011, feedback D3)
- 기존: `PortOnePaymentCancelPort.cancel(portonePaymentId, reason)`
- 확장 후: `cancel(portonePaymentId, Long amount, String reason, String requestKey)`
  - `amount`: nullable — null=전체취소, 값=부분취소
  - `requestKey`: 멱등키 (`"refund-{refund.id}"`)
- 부분 취소: PortOne V2 부분취소 API 활용
- 전체 취소: `amount=null` 시 본문에서 `amount` 키 제외
- 멱등성: `requestKey` PortOne 전달로 PG측 중복 취소 방지
- 실패 → `BusinessException(ErrorCode.PORTONE_CANCEL_FAILED)` (별도 예외 클래스 신설 X)
- 결제 확정 보상 흐름과 **동일 포트 공유** — 호출자가 `amount`·`requestKey` 명확 주입

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.payment.domain.PortOnePaymentCancelPort` — 확장
- `nbc.c1oud_mall.payment.infrastructure.PortOnePaymentCancelAdapter` — 확장

### 완료 기준 (AC)
- Given 결제 완료 `portonePaymentId` + 부분 `amount` / When `cancel(id, amount, reason, requestKey)` / Then PortOne 부분 취소 · 2xx · void 반환
- *(엣지 - 멱등)* Given 동일 `requestKey` 재호출 / When PortOne 응답 / Then 두 번째도 정상 · PG 중복 취소 없음
- *(엣지 - 실패)* Given PortOne 4xx/5xx/timeout / When 어댑터 응답 / Then `BusinessException(PORTONE_CANCEL_FAILED)`

### Definition of Done
- [ ] `PortOnePaymentCancelPort` 시그니처 확장 (`amount`, `requestKey`)
- [ ] `PortOnePaymentCancelAdapter` 확장 (본문 직렬화 + null 처리)
- [ ] `PortOneCancelRequest` record에 `amount`·`requestKey` 필드 (`@JsonInclude(NON_NULL)`)
- [ ] 어댑터 단위 테스트: 부분/전체 본문 / 멱등키 전달 — 3건
- [ ] 결제 보상 흐름 기존 호출부 호환성 확인 (nullable 파라미터)
- [ ] ADR 011 작성

### 스토리 포인트
1d (Payment Product 보상 흐름과 공유 시 0.5d)

### 의존성
- 선행: Payment Product Story 2-3 (기존 포트 존재)
- 후행: Story 2-2

---

## [Story 2-2] 환불 처리 도메인 서비스 (선검증 → DB 커밋 → PG 취소)

### User Story
- As a 결제 BC
- I want 환불 요청에 대해 선검증 통과 후 DB 갱신을 단일 트랜잭션으로 커밋하고, 이후 PG 취소 API 호출
- so that DB 락이 PG 응답을 기다리지 않으면서도 도메인 정합성 유지

### 설명
- `RefundProcessService`가 환불 처리 단일 진입점
- 처리 순서 (ADR 010):
  1. **선검증** (짧은 read-only TX 또는 TX 밖)
     - Payment 조회 + 소유권 + 상태(COMPLETED)
     - 잔여 환불 가능 수량 (Story 1-1)
     - 환불 금액 산정 (Story 1-2)
  2. **DB 갱신 TX** (`REQUIRED` 단일 TX, 락 순서 Order → Payment → Point → Inventory)
     - `SELECT ... FOR UPDATE on payments`
     - **락 후 재검증** (S+ 패턴 — 선검증 통과 후 race 차단)
     - Refund 저장 (`DB_COMMITTED`)
     - 재고 복구 (`InventoryRestorePort`)
     - 포인트 복구 + 비례 적립분 차감 (`PointRestorePort`)
     - TX 커밋
  3. **PG 취소** (TX 밖)
     - `PortOnePaymentCancelPort.cancel(portonePaymentId, pgRefundAmount, reason, "refund-"+refund.id)`
     - 성공: 별도 TX(단일 UPDATE)로 `Refund.markPgCancelled(pgCancelTxId)`
     - 실패: `log.error("REFUND_PG_CANCEL_FAILED ...")` + 알람 · Refund는 `DB_COMMITTED` 유지
- 적립률: Payment product 상수/객체 참조 (하드코딩 금지)
- BC 간 통신: 1차 직접 포트 호출 (이벤트는 후속)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.refund.application.RefundProcessService`
- `nbc.c1oud_mall.refund.application.InventoryRestorePort`
- `nbc.c1oud_mall.refund.application.PointRestorePort`

### 완료 기준 (AC)
- Given COMPLETED Payment + 유효 요청 + 본인 소유 / When `process(request, userId)` / Then DB에 Refund(`DB_COMMITTED`) + 재고 + 포인트 커밋 · PortOne 취소 성공 시 `PG_CANCELLED` 전이
- *(엣지 - PG 실패)* Given DB 커밋 후 PG 취소 실패 / When 호출 예외 / Then DB 유지 · Refund `DB_COMMITTED` 유지 · `REFUND_PG_CANCEL_FAILED` 로그 · 알람
- *(엣지 - DB TX 롤백)* Given DB TX 중 포인트 실패 / When 롤백 / Then Refund·재고·포인트 모두 롤백 · PG 미호출

### Definition of Done
- [ ] `RefundProcessService` 구현 (`refund.application`)
- [ ] `Refund.markDbCommitted`·`markPgCancelled`·`markFailed` 도메인 메서드
- [ ] `InventoryRestorePort`·`PointRestorePort` 정의 (1차 Mock 어댑터)
- [ ] 단위 테스트: 정상 · 선검증 실패 · 락 후 재검증 실패 · DB 실패 · PG 실패 — 5건
- [ ] 통합 테스트: 단일 TX 롤백
- [ ] **동시 환불 race 통합 테스트** (같은 paymentId 동시 진입 → 한 건만 성공)
- [ ] `REFUND_PG_CANCEL_FAILED` ERROR 로그 마커
- [ ] Payment 적립률 상수 참조 확인
- [ ] ADR 010 작성

### 스토리 포인트
2d

### 의존성
- 선행: Story 2-1 · Story 1-1 · 1-2
- 후행: Story 2-3

---

## [Story 2-3] 환불 요청 API 엔드포인트

### User Story
- As a 클라이언트(FE)
- I want 결제 완료 주문에 대해 환불 대상 주문상품·수량·사유 입력하여 환불 요청
- so that 부분/전액 환불을 안전하게 처리

### 설명
- `POST /api/v1/orders/{orderId}/refunds`
- 요청: `{ items: [{orderItemId, quantity}], reason }` — 금액 미입력
- 인증 필요 (Spring Security)
- 컨트롤러는 `RefundProcessService.process(...)` 호출만
- 응답은 모두 `ApiResponse<T>` 래퍼 (CLAUDE.md §4):
  - PG 취소까지 완료: **200** + `ApiResponse.success(refundResponse)` — `PG_CANCELLED`
  - PG 실패 (DB 커밋됨): **202** + `ApiResponse.success(refundResponse, warning)` — `DB_COMMITTED`, "PG 취소 진행 중, 운영팀 확인" (feedback §1-3)
  - RF001 (잔여 초과): **409** + `ApiResponse.error("RF001", ...)`
  - RF002 (환불 불가 상태): **409** + `ApiResponse.error("RF002", ...)`
  - RF003 (소유권): **403** + `ApiResponse.error("RF003", ...)`
  - 미인증: 401 (Spring Security)

### 멱등성
- 멱등 키: `(paymentId, items 집합)` — 비즈 식별자 (idempotency.md §2)
- 같은 요청 2회 → 두 번째는 RF001 (409)로 거부
- 동시 race → Story 2-2 `SELECT FOR UPDATE`에서 처리
- PG 호출 멱등: `requestKey="refund-{refund.id}"`

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.refund.presentation.RefundController`
- `RefundRequest`·`RefundResponse` (record)

### 완료 기준 (AC)
- Given 인증 사용자 + 본인 소유 + 유효 요청 / When `POST /api/v1/orders/{orderId}/refunds` / Then 200 · `ApiResponse.success(response)`
- *(엣지 - 초과)* Given 잔여 초과 / When 호출 / Then 409 · `ApiResponse.error("RF001", ...)`
- *(엣지 - 소유권)* Given 타인 주문 / When 호출 / Then 403 · `ApiResponse.error("RF003", ...)`

### Definition of Done
- [ ] `RefundController` + Request/Response DTO (`refund.presentation`)
- [ ] 모든 응답 `ApiResponse<T>` 래퍼 · `ApiResponses` 헬퍼 사용 (`ApiResponses.accepted(...)` 신규 필요 시 확인)
- [ ] Spring Security 인증 통합
- [ ] HTTP 상태는 `ErrorCode.status` 자동 매핑 (GlobalExceptionHandler)
- [ ] PG 결과에 따라 컨트롤러 200/202 분기 (Service 결과 객체에 PG 상태 포함)
- [ ] `@WebMvcTest` 슬라이스 — 성공 200 / PG 실패 202 / RF001 · RF002 · 401 · RF003 (6건)
- [ ] **멱등 통합 테스트**: 동일 요청 2회
- [ ] OpenAPI 문서 갱신
- [ ] E2E 테스트 (샌드박스 부분·전액 환불)

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 2-2
- 후행: 없음

### [명세 변경 이력]
- 2026-06-XX: 원안의 400 → 409로 RF001/RF002 응답 코드 변경 (feedback §1-2)
- 2026-06-XX: PG 실패 시 202 Accepted 응답 추가 (feedback §1-3)
