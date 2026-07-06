# AI-ADR-009: 복합결제 환불 금액 분리 — PG는 `floor`, 포인트가 잔액 흡수

- 상태: Accepted
- 일자: 2026-06-05
- 관련 Story: `workflows/products/product02.md` Story 1-2 (환불 금액 자동 산정)
- 관련 ADR: `refund/docs/adr/0008-refund-amount-split-policy.md`

---

## Context

포인트+PG 복합결제(예: PG 8,000원 + 포인트 2,000원 = 총 10,000원)의 환불 시, 총 환불 금액을 두 결제 수단에 어떻게 분배할지가 문제.

제약:
- **합계 보장**: `pgRefundAmount + pointRefundAmount`가 총 환불 금액과 *정확히* 일치 (정합성 지표 100%)
- **무손해**: 소수점/끝수 처리로 사용자 손해 없음
- **자동 산정**: 클라이언트는 금액 미입력, 정책 단순·결정적
- **외부 거부 방지**: PG 환불 금액이 결제 금액보다 크면 PortOne이 거부

산정 후보:
- **Round half up (반올림)**: 한 쪽 반올림 시 합계가 1원 어긋남 → 보정 로직 추가
- **Ceil to PG (PG가 끝수 포함)**: PG 환불액이 결제액보다 클 수 있음 → 외부 거부 위험
- **PG floor + 포인트 잔액 흡수**: PG는 절삭, 포인트가 끝수 흡수
- **사용자 선택**: 사용자가 환불 받을 결제수단 선택 → UX 복잡 + 정합성 검증 복잡

엣지 케이스:
- PG=0 (포인트 전액 결제) → 산식 무관, 환불 전액 포인트로
- 포인트=0 (PG 전액 결제) → 산식 무관, 환불 전액 PG로
- 1원 단위 끝수 발생 케이스: 비율이 깔끔하지 않은 경우 (예: 7,500+2,500 결제 + 3,333원 환불)

---

## Decisions

**PG는 `floor`, 포인트가 잔액 흡수.**

```
totalRefundAmount = Σ (priceSnapshotAtPayment × quantity)

pgRefundAmount    = floor(totalRefundAmount × pgAmount / totalAmount)
pointRefundAmount = totalRefundAmount − pgRefundAmount      ← 잔액 흡수
```

### 단일 수단 분기 (조기 반환)
- `payment.pgAmount == 0` → `pgRefundAmount=0, pointRefundAmount=totalRefundAmount`
- `payment.pointUsedAmount == 0` → `pgRefundAmount=totalRefundAmount, pointRefundAmount=0`
- 복합결제만 산식 적용

### 검증 케이스 (단위 테스트)
| Payment | Refund 대상 | pgRefundAmount | pointRefundAmount | 비고 |
|---|---|---|---|---|
| PG 8,000 + Point 2,000 (=10,000) | 5,000 | 4,000 | 1,000 | 정확히 떨어짐 |
| PG 7,500 + Point 2,500 (=10,000) | 3,333 | 2,499 (floor) | 834 (잔액) | 합 3,333 ✓ |
| PG 0 + Point 5,000 | 1,000 | 0 | 1,000 | 단일 수단 분기 |
| PG 10,000 + Point 0 | 3,000 | 3,000 | 0 | 단일 수단 분기 |

---

## 프롬프트 내용 (AI 활용시)

> "복합결제 환불 산정 정책을 결정해야 합니다.
> PG 8,000 + 포인트 2,000 결제 + 5,000 환불 → 80:20 비율로 PG 4,000 / 포인트 1,000은 정확히 나뉩니다.
>
> 문제는 끝수 발생 케이스: PG 7,500 + 포인트 2,500 결제 + 3,333 환불.
> 75:25 비율로 PG 2,499.75 / 포인트 833.25 — 정수가 아닙니다.
>
> 옵션:
> - 반올림 (양쪽) → 합계 어긋남
> - PG ceil → PG 환불액이 결제액보다 클 위험 (외부 거부)
> - PG floor + 포인트 잔액 → 합계 보장, 사용자 무손해
> - 사용자 선택 → UX 복잡
>
> 어떻게 가야 하나요? 합계는 무조건 정확히 맞아야 하고, 사용자가 손해 보면 안 됩니다."

---

## AI 응답 요약

### 결론
- **PG floor + 포인트 잔액 흡수가 가장 안전.**
- 합계 보장은 수학적으로 자명: `pointRefund = total − pgRefund`이므로 sum = total.
- 사용자 무손해: 끝수가 *포인트로* 흡수되므로 사용자가 0.5원 손해 보는 일이 없음 (오히려 사용자는 포인트가 1원 더 많이 환원되는 셈).

### 기각 사유
- **Round half up**: 합계 보장 위반 — 정합성 지표 100% 불가능. 보정 로직을 추가하면 정책이 복잡해짐.
- **Ceil to PG**: PG 환불액 > PG 결제액 가능성 → PortOne이 *결제액 초과 취소 요청*을 거부할 수 있음. 외부 시스템 거부 위험.
- **사용자 선택**: UX 복잡 + 정합성 검증 복잡 + 보안 위험(사용자가 한쪽으로 몰아넣고 다른 결제수단 정합성 깨기 시도). 1차 단순 정책이 우선.

### 단일 수단 분기
- 산식만 적용해도 같은 결과 (PG=0이면 `pgRefund = floor(total × 0 / total) = 0`)
- 그래도 **명시적 분기**를 두는 게 가독성·테스트 용이성 ↑

### 누적 환불의 정밀도
- 단일 환불에서 비율은 항상 정확하지 않음 (예: 3,333 환불에서 PG 비율 74.97% — 결제 75%와 0.03%p 차이)
- 누적 환불에서 끝수가 포인트 측에 누적될 수 있음
- 단, *환불 합계는 결제 금액을 초과할 수 없다*는 RF001 가드가 있어 운영상 무해

---

## Consequences

### 장점
- **합계 보장 100%** (수학적 자명) — 정합성 지표 충족
- **사용자 무손해** — 끝수가 포인트로 흡수 (0.5원 손해 대신 1원 더 환원)
- 산식 1단계 + 단일 수단 분기 — 검증·디버깅 용이
- `RefundAmountCalculator`가 순수 함수 — 외부 의존 없음, 단위 테스트로 완전 검증 (`RefundAmountCalculator.java`)
- 외부 거부 위험 없음 — PG 환불액 < PG 결제액 보장

### 단점 / 알려진 한계
- 단일 환불 비율이 결제 비율과 정확히 일치하지 않을 수 있음 (예: 3,333 환불에서 PG 비율 74.97% vs 결제 75%)
- 누적 환불에서 포인트 비율이 미세하게 증가 (끝수 누적). 단 RF001 가드(환불 합 ≤ 결제 금액)가 있어 운영 무해
- 사용자가 환불 받을 수단을 선택할 수 없음 (1차는 자동 산정만)

---

## 내가 수정한 부분

- AI는 처음에 *Round half up*과 *PG floor* 둘을 거의 동격 옵션으로 제시. **합계 보장 위반 가능성**을 들어 Round half up을 명확히 기각.
- AI는 정수 나눗셈으로 floor가 자연스럽게 일어남을 언급했지만, **단일 수단 분기를 명시적으로 두는 결정**은 안 했음. 가독성·테스트 case 명확화를 위해 분기 추가 (`RefundAmountCalculator:39-46`).
- "사용자 무손해" 표현이 AI 응답에 없어서, 정책 정당화 차원에서 명시적 추가. ADR에 *왜 이 정책이 사용자에게 안전한가*가 보이도록.
- 산식 정밀도 비교 케이스(75% vs 74.97%)를 ADR에 *명시*하기로 결정. 단점에 정확히 적어 두지 않으면 미래에 "이거 비율 틀린 거 아닌가?" 라는 의문이 반복됨 — 사전에 답을 박아둠.
- **PG=0 케이스에서 PortOne 호출 자체 skip** 분기 추가 (`RefundProcessService:82-86`). AI는 PortOne이 0원 취소 요청을 어떻게 처리하는지 명확히 모름 → 안전하게 호출 자체를 빼고 즉시 `PG_CANCELLED`로 마킹.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `RefundAmountCalculator.calculate(payment, items)` (`refund.domain.RefundAmountCalculator.java`)
  - 단일 수단 분기 (`:39-46`)
  - 복합결제 산식 (`:47-51`)
  - 순수 함수 — 외부 의존 없음
- ✅ `RefundBreakdown(pgRefundAmount, pointRefundAmount)` VO — 분리 보관
- ✅ DB 컬럼 분리: `refund.pg_refund_amount`, `refund.point_refund_amount`
- ✅ `RefundProcessService:82-86` — PG=0 즉시 PG_CANCELLED (PortOne 호출 skip)
- ✅ 일반 ADR: `refund/docs/adr/0008-refund-amount-split-policy.md`
- ✅ 단위 테스트: PG-only / 포인트-only / 복합 정확히 떨어짐 / 복합 끝수 발생 / 합계 보장 프로퍼티 테스트
- 미래 트리거:
  - 등급별 차등·반올림 정책 도입 시 별도 ADR
  - 사용자가 환불 받을 수단 선택 UX 시 별도 ADR
