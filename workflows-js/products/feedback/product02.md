# product02(환불) 설계 피드백

> product02.md를 현재 코드·컨벤션(.claude/rules/) 기준으로 검토하면서 발견한 수정/보강 지점.
> 1차 결정은 아래 §0에 정리. 명세 수정·보강은 §1~§2, 진행 중 결정거리는 §3, 영향도는 §4.

---

## 0. 결정 사항 (사용자 답)

| # | 결정 포인트 | 선택 | 비고 |
|---|---|---|---|
| D1 | Story 2-1 PortOne 취소 어댑터 | **기존 포트 확장** | `PortOnePaymentCancelPort` 시그니처에 `amount`·`requestKey` 추가, 어댑터도 동일 수정. Story 2-1은 "신규 작성"이 아니라 "확장 + 부분취소 테스트"로 축소. |
| D2 | Refund 패키지 위치 | **별도 `nbc.c1oud_mall.refund.*`** | 결제와 다른 트리거(환불 API)로 lifecycle 구분. consistency.md zone은 같이 묶이지만 패키지는 분리. |
| D3 | 가격 스냅샷 출처 | **1차 OrderItem 단가 조회** | Order·Payment 모델 변경 없음. 단가 변경/할인 변경 시 정합성 약함 → 후속 Story로 PaymentItem 스냅샷 도입 검토. |
| D4 | RF001(잔여수량 초과) 응답코드 | **409 Conflict** | idempotency.md §5 "Explicit reject = 409"와 일치. |

---

## 1. 명세 수정 필요 (컨벤션 충돌 — 그대로 두면 위반)

### 1-1. `RefundDomainException.of(ErrorCode)` 패턴 삭제
- **위치**: Story 1-1 "설명" 마지막 줄, DoD "ErrorCode 정의" 항목
- **위반**: forbidden.md §4 "도메인별 별도 Exception 클래스 남발 금지", errorhandling.md §1 "모든 커스텀 예외는 BusinessException로 통일"
- **수정안**: 다음 문구로 교체
  ```
  - 도메인 예외: 우리 컨벤션대로 `BusinessException(ErrorCode.REFUND_*)` 사용.
    별도 RefundDomainException 클래스는 만들지 않는다.
  ```
- **ErrorCode prefix**: `RF` 줄임형 OK (기존 `PAY` 사례 있음). 신규 항목:
  - `REFUND_QUANTITY_EXCEEDED("RF001", "잔여 환불 가능 수량을 초과했습니다.", CONFLICT)`
  - `REFUND_NOT_REFUNDABLE_STATE("RF002", "환불할 수 없는 결제 상태입니다.", CONFLICT)`
  - `REFUND_OWNERSHIP_FAILED("RF003", "본인 소유의 결제만 환불할 수 있습니다.", FORBIDDEN)` (Story 2-3)
  - `REFUND_PORTONE_CANCEL_FAILED("RF004", "PG 취소 처리에 실패했습니다.", ACCEPTED 또는 INTERNAL)` (상태 표시용, 실제 응답은 §1-3 참고)

### 1-2. RF001 응답코드 400 → **409**
- **위치**: Story 2-3 "설명" 응답 표 ("잔여 수량 초과: 400 + 에러 코드 RF001")
- **수정안**: `잔여 수량 초과: 409 + 에러 코드 RF001`
- **위치2**: Story 2-3 "인수 조건" 두 번째 시나리오 "400"도 함께 409로 수정
- **근거**: idempotency.md §5 카탈로그

### 1-3. PG 취소 실패 시 응답 — 200 → **202 Accepted**
- **위치**: Story 2-3 "설명" 응답 표 ("PG 취소 호출 실패: 200")
- **수정안**:
  ```
  PG 취소 호출 실패: 202 Accepted
    + 응답 body에 refundStatus="DB_COMMITTED" 명시
    + warning 필드로 "PG 취소 처리 진행 중. 운영팀 확인 필요" 안내
  ```
- **근거**: errorhandling.md §6 "커밋 후 외부 실패는 예외 아닌 보상 처리". 200은 사용자에게 완료로 비침 → 202로 "수락됐으나 처리 진행 중" 명시.

### 1-4. Story 2-1 범위 축소
- **위치**: Story 2-1 전체
- **현 상태 명시**: 이미 존재하는 코드:
  - `payment/application/PortOnePaymentCancelPort.java` — `cancel(portonePaymentId, reason)`
  - `payment/infrastructure/portone/PortOnePaymentCancelAdapter.java` — RestClient 기반, 4xx/5xx → `BusinessException(PORTONE_CANCEL_FAILED)`
  - `payment/infrastructure/portone/PortOneCancelRequest.java`
- **수정안**:
  - "PortOne V2 부분취소 API 호출을 위해 **기존 포트를 확장**한다." 명시
  - 시그니처 변경 명세: `cancel(portonePaymentId, amount, reason, requestKey)` — `amount`는 nullable(전체취소시 null), `requestKey`는 멱등키(Refund ID 등).
  - PortOne 부분취소 API 본문 스펙: `{"amount": ..., "reason": ..., "requestKey": ...}` (PortOne V2 문서 기준 확인 필요)
  - DoD에서 "신규 어댑터 작성" → "기존 어댑터에 부분취소·멱등키 분기 추가 + 단위 테스트 보강"으로 변경

---

## 2. 설계 보강 필요 (명세에 누락된 race/멱등/락)

### 2-1. 동시 환불 race 가드 (Epic 2 / Story 2-2)
- **현 명세**: "선검증 → DB 트랜잭션 커밋 → PG 호출". 선검증과 DB 트랜잭션 사이에 동시 환불 두 건이 통과 가능.
- **요구 사항** (consistency.md §5 + idempotency.md §4-S+ 패턴):
  1. 선검증(read-only 트랜잭션)에서 잔여 수량 계산은 빠른 차단용
  2. **DB 갱신 트랜잭션 진입 즉시 `SELECT … FOR UPDATE` on `payments`** (paymentId 단위 락) — consistency.md §5 락 순서 `Order → Payment → Point → Inventory`와 일치
  3. 락 획득 후 잔여 수량 **재계산**, 초과면 `BusinessException(REFUND_QUANTITY_EXCEEDED)` 던지고 롤백
- **명세 추가 문구** (Story 2-2 "설명" 처리 순서 2단계 첫 줄에):
  ```
  - paymentId에 대해 비관적 락(SELECT ... FOR UPDATE) 획득 후 잔여 환불 가능 수량 재검증
  ```
- **DoD 추가**: "동시 환불 두 건 race 통합 테스트 — 한 건만 성공, 다른 건 RF001로 실패"

### 2-2. 멱등 키 / 응답 정책 명시 (Story 2-3)
- **현 명세**: 멱등 키 개념 자체가 없음.
- **idempotency.md §2 카탈로그 기준 환불 API**:
  - 키 출처: 비즈 식별자 (`paymentId + orderItemId + quantity` 집합)
  - 감지 메커니즘: A (트랜잭션 + 비관적 락 + 잔여 수량 검증)
  - 응답 정책: Explicit reject (잔여 초과 시 409)
  - 윈도우: 영구
- **명세 추가 문구** (Story 2-3 "설명" 끝에 별도 섹션):
  ```
  ### 멱등성
  - 멱등 키: (paymentId, items 집합) — 별도 헤더 없음, 비즈 식별자 기반.
  - 같은 요청을 두 번 보내도: 잔여 수량 검증에서 두 번째는 409로 거부 (별 환불 두 건 생성 X).
  - 동시 진입 race는 §2-1 비관적 락으로 차단.
  ```
- **DoD 추가**: "동일 요청 2회 호출 시 두 번째는 409 + 첫 환불은 정상 반환되는 통합 테스트"

### 2-3. PG 취소 호출 멱등키 = Refund ID
- **위치**: Story 2-2 처리 순서 3단계 / Story 2-1 "설명" 멱등성 문단
- **확정**: PortOne `requestKey`(또는 동등 멱등 헤더)에 `refund.id` 또는 `"refund-{refund.id}"` 전달
- **명세 추가**: Story 2-1 DoD에 "Refund ID를 멱등키로 전달, 동일 키 재시도 시 PG 측 중복 취소 0건 검증"

### 2-4. 적립 포인트 회수율 명시 (Story 2-2)
- **현 명세**: "적립분 차감(`pgRefundAmount × 0.01`)" — 0.01 하드코딩
- **고민거리**: 결제 시 적립률이 어디서 결정되는지(현재 코드: `Payment.pointEarnedAmount` 필드만 있고 정책은 결제 흐름 어딘가). 동일 정책 객체/상수 재사용 필요.
- **명세 수정안**: "결제 시 적립률과 동일 상수·정책 객체를 참조하여 비례 차감 — 1차는 결제 product 적립 정책 클래스/상수 위치 확인 후 공유."
- **TODO**: 결제 흐름의 적립 정책 확인 → 위치/이름 명세에 명시

### 2-5. Refund 별도 컨텍스트로 가져갈 때 의존 방향
- **결정 D2 영향**: `refund` 컨텍스트가 `payment` 코드를 어떻게 호출할지
- **권장 구조**:
  - `refund/application` → `payment/application/PaymentQueryService`(혹은 readonly 메서드) 호출로 Payment 정보 조회
  - `refund/application` → `payment/application/PortOnePaymentCancelPort` 호출 (포트는 payment에 있고 refund가 소비)
  - `refund/domain`은 `payment.domain.Payment`를 import하지 않음 — Payment 정보가 필요하면 `RefundContext`(record) 같은 입력 DTO로 들고와서 도메인 계산만 수행
- **명세 추가**: Epic 1/2 "설명" 앞에 패키지/의존 방향 한 줄 명시

### 2-6. Refund 자체의 ApiResponse 응답 포맷
- **현 명세**: "200 + Refund 상태 + 산정된 PG·포인트 환불 금액" — 우리 표준 `ApiResponse<RefundResponse>` 래핑 필요 명시 없음
- **수정안**: Story 2-3 응답 명세에 "모든 응답은 `ApiResponse<T>` 래퍼" 한 줄 추가 (CLAUDE.md §4)

---

## 3. 진행 중 결정거리 (Story 들어가서 결정)

### 3-1. 잔여 수량 계산 구현 위치 (Story 1-1)
- 옵션 A: `RefundRepository.sumRefundedQuantity(paymentId, orderItemId): long` 같은 쿼리 메서드
- 옵션 B: 도메인 서비스 `RefundQuantityCalculator`가 모든 Refund 로드 후 자바에서 합산
- **권장**: A. DB SUM이 단순하고 FOR UPDATE 락과 결합도 자연스러움. 통합 테스트로 검증.

### 3-2. Calculator 결과 주입 시점 (Story 1-2)
- 옵션 A: `Refund.of(paymentBreakdown, refundItems, reason)` — 생성자 안에서 RefundAmountCalculator를 부르지 않음. 외부(서비스)가 calculator 결과를 인자로 주입.
- 옵션 B: `Refund.of(...).applyBreakdown(pgAmount, pointAmount)` setter형
- **권장**: A. 불변 유지. application service가 calculator 호출 → 결과 묶어서 `Refund.of(...)`.

### 3-3. 가격 스냅샷 후속 마이그레이션 (D3 후속)
- 1차는 OrderItem 단가 조회로 진행. 단가 변경/할인 변경 가능성이 생기면 즉시 위험.
- **별도 Story로 트래킹**: "PaymentItem 스냅샷 도입" — Payment 모델 변경, ADR 작성, 환불 산정 로직을 스냅샷 우선으로 변경.
- 1차 작업 끝나고 사용자와 우선순위 협의.

### 3-4. 포인트/재고 협력 인터페이스 (Story 2-2)
- 현재 결제 흐름은 `payment.infrastructure.mock.MockPointService`, `MockInventoryService`를 사용.
- 1차 환불도 같은 mock 재사용 (인터페이스를 application에 노출하고 mock 어댑터가 구현).
- 결정: Refund가 새 컨텍스트(D2)이므로 `refund/application`에서 `PointRestorePort` / `InventoryRestorePort` 같은 포트를 정의하고, 기존 MockPointService/MockInventoryService를 어댑터로 재사용 또는 wrapper 생성. — Story 2-2 진입 시 코드 보고 최종 결정.

### 3-5. PG 취소 실패 시 운영 추적 메트릭
- ERROR 로그 + 메트릭 명시 (Story 2-2 DoD).
- 구체적 메트릭 이름·태그는 운영 표준이 따로 있는지 확인 필요 — 없으면 단순 `log.error("REFUND_PG_CANCEL_FAILED ...")` 마커로 시작.

### 3-6. 트랜잭션 안 외부 호출 금지 재확인 (Story 2-2)
- 명세 처리 순서는 "DB 커밋 → PG 호출 → 별도 트랜잭션으로 `markPgCancelled`"로 외부 호출이 트랜잭션 밖에 위치 ✅
- 다만 두 번째 트랜잭션도 짧게 — `PortOneCancelResult` 받고 단일 UPDATE만 수행하도록 명세 강조 (Story 2-2 "설명"에 한 줄 추가)

---

## 4. 영향도 / 후속 작업

### 4-1. 명세 수정 작업 (확정 사항만 product02.md에 반영)
- 1-1 ~ 1-4, 2-1 ~ 2-3, 2-6 → product02.md 직접 수정
- 2-4(적립률 정책 위치 확인), 2-5(의존 방향 한 줄) → 짧은 코드 확인 후 수정
- 작업 단위: 명세 수정만 묶어서 1 commit 추정. PR은 명세가 gitignore라서 코드 PR엔 포함 X.

### 4-2. 신규 도메인 패키지 추가
- `nbc.c1oud_mall.refund.{presentation,application,domain,infrastructure}` 패키지 생성
- 패키지 추가만 따로 commit (Story 진행 전 골격)

### 4-3. ErrorCode 추가
- `common.exception.ErrorCode`에 `// ─── 환불 ───` 섹션 + RF001~RF004 추가
- **공통 인프라 수정** — Domain ownership 파일은 비어있어 명시적 권한 없음. 환불 도메인 작업의 일부로 보고 진행. 사용자가 다르게 보면 stop & ask.

### 4-4. Story 진행 순서 권장
1. Story 1-1: Refund / RefundItem / RefundBreakdown 모델 + Repository(SUM 쿼리 포함) + ErrorCode 추가
2. Story 1-2: RefundAmountCalculator + 단위 테스트
3. Story 2-1: 기존 `PortOnePaymentCancelPort` 확장 + 어댑터 부분취소 지원 + 멱등키
4. Story 2-2: RefundProcessService — 락·트랜잭션·보상 흐름
5. Story 2-3: Controller / DTO / 예외 매핑 / E2E

### 4-5. ADR 후보
- 환불 트랜잭션 경계 (선검증 / DB 트랜잭션 / PG 호출 3단계 분리) — Epic 2 완료 시
- 복합결제 환불 비율 분리 (floor + 잔액 흡수) — Epic 1 완료 시
- (후속) PaymentItem 가격 스냅샷 도입 — D3 마이그레이션 시
- (후속) Refund 별도 컨텍스트 분리 결정 — D2 결정 ADR
- ADR 작성은 사용자 명시 시에만 (Domain ownership 비어있음, ADR 권한 미정)

---

## 5. 빠뜨린 거 / 더 묻고 싶은 거

- 결제 product(product.md)의 보상 흐름이 PG 취소 포트와 어떻게 결합돼 있는지 — Story 2-1 어댑터 확장 시 다른 호출자에 영향 가는지 확인 필요. (product.md 빠르게 훑고 진행)
- Order BC 측에서 OrderItem 단가 필드명·접근 경로 확인 (Story 1-1/1-2에서 RefundItem 생성 시 필요)
- 재고 복구 호출 단위가 OrderItem ID 기준인지 productId 기준인지 — `MockInventoryService` 시그니처 확인 필요 (Story 2-2 진입 시)
