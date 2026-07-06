# AI-ADR-007: Refund 신규 컨텍스트 분리 + `refund.domain`은 `payment.domain` 비의존

- 상태: Accepted
- 일자: 2026-06-05
- 관련 Story: `workflows/products/product02.md` Story 1-1 (Refund Aggregate + RefundItem 모델링)
- 관련 ADR: 본 토픽은 일반 ADR로 분리 작성하지 않음 (도메인 모델 구조 결정)
- 관련 룰: `.claude/rules/architecture.md` — 컨텍스트 패키지 분리, 도메인은 다른 BC에 비의존

---

## Context

환불 흐름을 어디에 둘 것인가가 첫 결정.

선택지:
- (a) `payment.domain.Refund`로 결제 BC 안에 둠 (Payment와 한 모듈)
- (b) **신규 컨텍스트** `nbc.c1oud_mall.refund.{presentation, application, domain, infrastructure}`로 분리

환불 도메인은 결제와 강하게 얽혀있다:
- Payment 1개에 Refund N개 (1:N)
- 환불 금액 산정 시 `payment.totalAmount`, `payment.pgAmount`, `payment.pointUsedAmount` 비율이 필요
- PG 취소 호출은 `payment.portonePaymentId`로 PortOne API 호출

그래서 (a)가 직관적이고, "한 모듈에 두면 import 편하다"는 유혹이 강함. 다만:
- 결제 lifecycle과 환불 lifecycle이 **다르다** (결제 1회, 환불은 시간차 + 부분 반복)
- 환불은 운영/CS 영역으로 갈 가능성 (관리자 승인 워크플로우 등 — 1차 Scope 외지만 향후 확장)
- `payment.domain`이 비대해지면 도메인 응집도 ↓

→ 신규 컨텍스트 분리가 적절. 단 분리하면 또 다른 문제: **`refund.domain`이 `payment.domain.Payment`를 직접 import해도 되는가?**

---

## Decisions

### 1. `nbc.c1oud_mall.refund` 신규 컨텍스트 신설
- `presentation` / `application` / `domain` / `infrastructure` 4계층 분리
- Payment와 동등한 컨텍스트 (하위 모듈 아님)

### 2. `refund.domain`은 `payment.domain.Payment`를 import하지 않음
- 도메인 간 직접 의존을 만들면 향후 한쪽 변경이 다른 쪽으로 전파됨 (양방향 결합)
- 대신 **application layer에서 record/DTO로 협력**:
  - `RefundablePayment(paymentId, userId, isCompleted, totalAmount, pgAmount, pointUsedAmount, portonePaymentId)` — refund.domain 안의 record
  - application service(`RefundProcessService`)가 `Payment` → `RefundablePayment` 변환 후 도메인에 주입

### 3. 그러나 application은 `Payment` import OK
- 비의존 룰은 *도메인*에 한정. application은 BC 간 협력의 자연스러운 지점 (consistency.md §4 "약 → 강 zone 결정")
- `RefundProcessService`가 `PaymentJpaRepository`를 직접 주입해서 `Payment`를 로드

### 4. 별도 RefundDomainException 클래스 X
- `BusinessException + ErrorCode.REFUND_*` 통일 (`forbidden.md §4`, `errorhandling.md §1`)
- ErrorCode: `RF001` (수량 초과 / 409), `RF002` (환불 불가 상태 / 409), `RF003` (소유권 / 403)

---

## 프롬프트 내용 (AI 활용시)

> "환불 도메인을 추가하려고 합니다. 결제와 매우 강하게 얽혀있어요:
> - Payment에 Refund N개
> - 환불 금액 산정은 payment.totalAmount/pgAmount/pointUsedAmount 비율 필요
> - PortOne 취소는 payment.portonePaymentId로
>
> 두 옵션:
> (a) `payment.domain.Refund`로 결제 BC 안에 둠 — 직관적, import 편함
> (b) 신규 컨텍스트 `refund/`로 분리
>
> 분리하기로 했을 때, `refund.domain`이 `payment.domain.Payment`를 import해도 되나요?
> 도메인 간 직접 의존이 위험하다는 건 알지만, 환불 산정에 Payment 필드가 꼭 필요해서요.
>
> 우리 컨벤션은 DDD 레이어드 + 도메인은 다른 BC 비의존입니다."

---

## AI 응답 요약

### (a) vs (b)
- (a)의 함정: payment.domain이 비대해짐 + Payment lifecycle과 다른 Refund lifecycle이 한 곳에 섞임
- 환불은 *운영/CS 영역*으로 확장될 가능성이 큼 → 분리가 미래 확장에 유리
- 의존 import의 편함은 트레이드 가능 — application layer record로 해결

### refund.domain의 payment.domain 비의존
- **권장**. 도메인 간 직접 의존은 양방향 결합 시작점.
- Payment를 직접 안 쓰고 record/DTO로 받으면:
  - refund.domain은 "환불 산정에 필요한 정보 = totalAmount/pgAmount/pointUsedAmount/portonePaymentId"라는 *계약*만 요구
  - Payment 모델이 변해도 application 변환 함수만 수정
  - 환불 도메인 단위 테스트도 Payment 없이 가능 (record만 만들면 됨)
- application은 BC 간 협력 자연 지점이므로 Payment import OK

### RefundDomainException 별도 클래스
- ❌ **만들지 마라.** 컨벤션상 `BusinessException + ErrorCode` 통일이 명시 (`errorhandling.md §1`)
- 도메인별로 예외 클래스 늘리는 건 forbidden.md §4의 안티패턴

### ErrorCode prefix
- `RF` (환불) 신설 — 결제 PM과 분리해서 환불 도메인의 의미 코드 명확화

---

## Consequences

### 장점
- 결제 BC 응집도 유지 — Payment는 자기 lifecycle만
- 환불 확장(관리자 승인, CS 도구 등) 시 결제 BC 영향 0
- 도메인 단위 테스트가 Payment 없이 가능 — `RefundablePayment` record 한 줄 생성
- BC 간 결합도 ↓ — payment 모델 변경이 refund 도메인에 직접 파급 안 됨
- ErrorCode 통일 패턴 유지 (RefundDomainException 추가 안 함)

### 단점
- 변환 함수 1개 추가 — `Payment → RefundablePayment` (`toRefundablePayment` in `RefundProcessService:128`)
- "왜 직접 안 쓰지?" 라는 첫 의문 — 룰의 *이유*를 코드만 봐서는 알기 어려움 → README/주석 보강 필요
- Refund→Payment의 정보 흐름만 있고 역방향 없음. 만약 향후 Payment가 Refund 누적을 알아야 한다면(예: "환불 진행 중" 상태) 또 다른 의존 결정 필요

---

## 내가 수정한 부분

- AI는 처음에 (a) "payment.domain.Refund로 같은 BC"를 *간단함 우선*으로 권장. **lifecycle 차이 + 운영 도구 확장 가능성**을 들어 (b)로 재조정.
- refund.domain이 Payment를 import해도 "application에서만 의존하니 큰 문제 없다"는 AI의 완화 제안을 **명시적으로 거부**. application 계약이 record로 명시되어야 미래 BC 변경 비용이 낮음. → `RefundablePayment` record 도입 강제.
- AI는 RefundDomainException을 *도메인별로 두는 게 깔끔하다* 입장. **컨벤션(`errorhandling.md §1`, `forbidden.md §4`)을 명확히 제시**하고 `BusinessException + ErrorCode.REFUND_*` 통일로 변경.
- ErrorCode prefix를 처음엔 `REFUND001` 같은 긴 형태로 AI가 제시. **3자리 번호 + 짧은 prefix(`RF001`)**로 단축 — 컨벤션상 도메인 prefix는 3자리 + 3자리 번호.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `nbc.c1oud_mall.refund.{presentation,application,domain,infrastructure}` 4계층 신설
  - `refund.domain.RefundablePayment` record — 도메인 계약
  - `refund.domain.Refund` Aggregate, `RefundItem` Entity, `RefundBreakdown` VO, `RefundStatus` Enum
  - `RefundProcessService.toRefundablePayment(Payment)` — application 변환 (RefundProcessService:128)
  - `RefundController` 컨트롤러 (presentation)
- ✅ ErrorCode 추가 (`common.exception.ErrorCode:59-61`):
  - `REFUND_QUANTITY_EXCEEDED("RF001", "잔여 환불 가능 수량을 초과했습니다.", CONFLICT)`
  - `REFUND_NOT_REFUNDABLE_STATE("RF002", "환불할 수 없는 결제 상태입니다.", CONFLICT)`
  - `REFUND_OWNERSHIP_FAILED("RF003", "본인 소유의 결제만 환불할 수 있습니다.", FORBIDDEN)`
- ✅ 별도 RefundDomainException 클래스 미생성
- 미래 트리거:
  - Payment가 환불 진행 상태를 알아야 할 경우 의존 방향 ADR
  - 관리자 승인 워크플로우 추가 시 refund 컨텍스트 확장
