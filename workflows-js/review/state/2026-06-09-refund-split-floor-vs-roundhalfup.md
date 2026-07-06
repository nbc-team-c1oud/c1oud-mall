# [트러블슈팅] 복합결제 환불 분배 — Round half up은 합계 위반, Ceil to PG는 외부 거부

---
date: 2026-06-09
domain: [refund]
tags: [split-policy, floor, sum-invariant, external-rejection, single-source]
related-story: workflows/products/product02.md Story 1-2
related-adr: workflows/living-docs/Ai-adr/009-refund-amount-split-floor-policy.md, src/main/java/.../refund/docs/adr/0008-refund-amount-split-policy.md
related-topology: workflows/topologys/Consistency-Design-Topology.md
---

## 1. 문제 (What)

복합결제(PG + 포인트) 주문의 환불 금액을 두 결제 수단에 어떻게 분배할지.

엣지 케이스가 골치아픔:
- 결제: PG 7,500 + 포인트 2,500 (총 10,000)
- 환불: 3,333원
- 비율 그대로 적용: PG 2,499.75 / 포인트 833.25

소수점이 안 떨어짐. 두 가지를 동시에 만족해야 함:
- **합계 보장**: `pgRefund + pointRefund == totalRefund`가 *항상* 정확히 일치 (정합성 지표 100%)
- **무손해**: 끝수 처리로 사용자가 손해 보면 안 됨

후보 정책:
- (A) **Round half up (양쪽 반올림)**: PG 2,500 / 포인트 833 → 합 3,333 ✓ ... 그런데 3,332원 환불이면 PG 2,499 / 포인트 833 → 합 3,332 ✓ ... 다른 케이스 PG 2,500 / 포인트 832 → 합 3,332 ✓ ... 이 *둘 다 가능*하다는 게 문제. 어느 한쪽이 더 큰 *근거*가 없음. 그리고 일부 케이스에서 *합계가 정확히 안 떨어짐*.
- (B) **Ceil to PG (PG가 끝수 흡수)**: PG 2,500 / 포인트 833. PG 결제(7,500)보다 환불(2,500)이 작으니 일단 OK. 그러나 누적 환불에서 *PG 환불 합이 PG 결제 금액을 초과*할 위험 — PortOne이 거부 가능.
- (C) **PG floor + 포인트 잔액**: PG 2,499 / 포인트 834. 합계 보장 자명 (`pointRefund = total - pgRefund`).
- (D) **사용자 선택**: UX 복잡 + 보안 위험 (사용자가 한쪽으로 몰아 정합성 깨기 시도).

## 2. 문제 해결 방법 (How)

1. **합계 보장을 *제약*으로 못박음** — 산정 정합성 지표 100%이 우선.
2. **(A) Round half up 기각** — 합계가 정확히 안 떨어지는 케이스가 *수학적으로 존재*. 보정 로직을 추가하면 정책이 더 복잡해짐.
3. **(B) Ceil to PG 기각** — *외부 거부 위험*. PortOne이 PG 결제액 초과 취소 요청을 받으면 거부 가능. 외부 시스템 거부는 *복구 비용이 큼*.
4. **(C) PG floor + 포인트 잔액 채택**:
   ```
   pgRefund    = floor(totalRefund × pgAmount / totalAmount)
   pointRefund = totalRefund - pgRefund        ← 잔액 흡수
   ```
   - 합계는 *정의상* 일치 (`pointRefund` 정의 자체가 잔액)
   - 끝수가 포인트로 흡수 — 사용자 무손해 (오히려 포인트로 1원 더 많이 환원)
5. **단일 수단 분기 명시** — PG=0 또는 포인트=0이면 산식 우회. 산식 적용해도 같은 결과지만 *코드 가독성·테스트 케이스 명확화* 위해 명시 분기.
6. **PG=0 케이스에서 PortOne 호출 자체 skip** — `RefundProcessService:82-86`. 0원 취소 호출의 PortOne 동작이 불명확 → 호출 자체 빼고 `PG_CANCELLED`로 직행.

## 3. 방식 (Why this way)

### Round half up 기각의 정확한 이유
- 단순 반올림이 *항상 합계를 만족*시킨다고 착각하기 쉬움. 실제론 합계가 어긋나는 케이스가 존재.
- 보정 로직(*"합계 맞을 때까지 한쪽 조정"*)을 추가하면 정책이 *조건 분기*로 변함 — 결정적이지 않음.
- 결정적이지 않은 산정은 *테스트가 어려움*. 입력이 같으면 출력이 같아야 검증 가능.

### Ceil to PG 기각의 정확한 이유
- 일회성 환불에선 안전. 누적 환불에서 위험.
- 예: PG 100, 포인트 0.5 비율의 결제 + 여러 번 작은 환불 누적 → PG 환불 합이 PG 결제액 초과 가능
- "PortOne이 거부할 가능성"은 *복구 비용이 크다*. 거부 시 우리 DB는 환불 처리됐는데 PG는 그대로 → 정합성 깨짐.

### 잔액 흡수를 *포인트*로 한 이유 (PG로 가지 않음)
- 포인트는 *우리 시스템 내부*. 잔액 흡수의 부담이 외부로 안 나감.
- PG는 *외부*. PG 환불액에 끝수가 붙으면 PG 결제액과 비교 검증이 한 단계 더 필요.
- 사용자 입장에선 끝수가 포인트로 더 환원되니 *오히려 작은 이득*.

### 단일 수단 분기 명시
- 산식 적용해도 같은 결과 (`floor(total × 0 / total) = 0`)인데 굳이 분기를 두는 이유:
  - 코드를 처음 읽는 사람이 *"포인트만 결제한 경우 어떻게 되지?"* 묻기 전에 답이 보임
  - 단위 테스트 케이스가 *명시적으로 분리* (PG-only / 포인트-only / 복합)
  - 미래에 분기마다 다른 동작이 필요해질 때(예: PG=0이면 PortOne 호출 skip — 실제로 도입됨) 분기 추가 비용 0

## 4. 결과 (Outcome)

- 코드 반영: `RefundAmountCalculator.calculate` (`refund.domain.RefundAmountCalculator.java`)
  - 단일 수단 분기 (`:39-46`)
  - 복합결제 산식 (`:47-51`)
  - 순수 함수 — 외부 의존 없음, 상태 없음
- DB 컬럼 분리: `refund.pg_refund_amount`, `refund.point_refund_amount`
- `RefundProcessService:82-86` — PG=0이면 PortOne 호출 skip + 즉시 PG_CANCELLED
- 단위 테스트:
  - PG-only / 포인트-only / 복합 정확히 떨어짐 / 복합 끝수 발생 (3,333원 케이스)
  - **합계 보장 프로퍼티 테스트** — 임의 입력에 대해 `pgRefund + pointRefund == totalRefund` 항상 성립
- 운영 ADR(`ADR-0008`) + AI-ADR-009로 정책·회고 분리 기록

## 5. 좋아진 점 (What got better)

- **"합계 보장은 *정의로* 만족시켜라"는 사고법.** `pointRefund = total - pgRefund`라는 *정의* 자체가 합계 보장 → 보정 로직 0. 산식이 *수학적으로 자명*하면 테스트도 단순. 이후 다른 금액 계산(주문 합계, 포인트 적립 등)에서도 *정의로 만족시키기*를 우선 검토.
- **"외부 시스템 거부 위험"을 1차 결정 기준으로.** Ceil to PG 기각의 핵심 이유. 같은 사고로 PG=0 케이스에서 *PortOne 0원 취소 호출 자체 skip* 결정 — 외부 동작이 불명확하면 호출 자체를 빼는 게 안전.
- **순수 함수 도메인 서비스의 가치 체감.** `RefundAmountCalculator`가 외부 의존 0, 상태 0, 입력→출력 결정적. 단위 테스트 30줄 + 프로퍼티 테스트 1개로 *완전 검증*. 환불 산정 변경 시 코드 수정 비용이 가장 낮은 영역.
- **단일 수단 분기 명시 패턴.** *산식이 같은 결과를 낸다고 분기를 안 두면, 미래에 분기마다 다른 동작이 생길 때 비용이 큼.* 처음부터 분기 두는 게 *최소 비용*. 이 사고는 결제 확정에서도 적용 — `payment.isCompleted()` 멱등 가드를 *조기 반환*으로 명시.
