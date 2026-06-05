# ADR-0008: 복합결제 환불 금액 분리 정책

- 상태: Accepted
- 일자: 2026-06-05
- 범위: 환불 BC (Story 1-2)

## Context

Story 1-1에서 도입한 `RefundBreakdown` VO는 PG 환불 금액(`pgRefundAmount`)과 포인트 환불 금액(`pointRefundAmount`)을 별도 컬럼으로 보관한다.
포인트+PG 복합결제 주문의 환불 시, 총 환불 금액을 결제 비율에 따라 두 결제 수단에 어떻게 분배할지 결정이 필요하다.

### 제약
- **합계 보장**: `pgRefundAmount + pointRefundAmount`가 항상 총 환불 금액과 정확히 일치해야 한다 (정합성 100% 지표).
- **무손해 원칙**: 소수점/끝수 처리로 사용자가 손해를 보면 안 된다.
- **자동 산정**: 클라이언트는 금액을 입력하지 않고 서버가 자동 산정 — 정책이 단순·결정적이어야 한다.

## Decision

**PG는 `floor`, 포인트는 잔액 흡수.**

```
totalRefundAmount   = Σ (priceSnapshotAtPayment × quantity)
pgRefundAmount      = floor(totalRefundAmount × pgAmount / totalAmount)
pointRefundAmount   = totalRefundAmount - pgRefundAmount
```

- 결제 비율(`pgAmount / totalAmount`)을 PG 환불에 적용하고 정수 나눗셈(floor)으로 1원 단위 절삭
- 끝수는 포인트가 흡수 — 합계는 항상 일치
- 단일 결제 수단(PG=0 또는 포인트=0)은 분기로 단축 (산식 적용 시 동일 결과)

### 처리 흐름

```
RefundAmountCalculator.calculate(payment, items)
  → totalRefundAmount 산출
  → 단일 수단 분기 (PG=0 / 포인트=0)
  → 복합: floor 산식 + 잔액 흡수
  → RefundBreakdown(pgRefundAmount, pointRefundAmount)
```

## Alternatives

### Round half up
- 한 쪽에서 반올림 → 합계가 1원 어긋날 가능성
- 추가 보정 로직 필요 → 정책 복잡도 ↑
- **기각**: 합계 보장 위반 가능성

### Ceil to PG (PG가 끝수 포함)
- PG 환불이 약간 많아짐 → PG 환불 금액이 결제 금액보다 클 위험 (PG가 환불 거부할 수 있음)
- 사용자 입장에서는 PG 결제 금액보다 많이 환불되는 것은 불가능 → 외부 거부 위험 ↑
- **기각**: 외부 시스템(PortOne) 거부 위험

### 등급별 가중치 / 사용자 선택
- 사용자가 환불을 어느 결제 수단으로 받을지 선택
- UX 복잡도 ↑, 정합성 검증 복잡도 ↑
- **기각**: 1차 단순 정책이 우선, 후속 확장 옵션

## Consequences

### 장점
- 합계 보장 100% (수학적으로 자명)
- 사용자 무손해 (포인트가 끝수 흡수 — 0.5원 손해 대신 1원 흡수)
- 산식 1단계, 분기 최소 — 검증·디버깅 용이
- `RefundAmountCalculator`가 순수 함수 — 외부 의존 없음, 단위 테스트로 완전 검증

### 단점 / 알려진 한계
- 포인트가 끝수를 흡수하므로 단일 환불에서 포인트 환불 금액의 비율은 결제 비율과 정확히 일치하지 않을 수 있음 (예: 8000+2000 결제, 5000 환불 → pg=4000(80%), point=1000(20%)로 정확하나, 3333 환불 → pg=2499(74.97%), point=834(25.03%))
- 누적 환불에서 포인트 비율이 미세하게 증가할 수 있음 (각 환불마다 끝수가 포인트 측에 누적) — 환불 합계는 결제 금액을 초과할 수 없으므로(Story 1-1 RF001) 운영상 무해

## References

- `workflows/products/product02.md` — [Story 1-2] 환불 금액 자동 산정
- `nbc.c1oud_mall.refund.domain.RefundAmountCalculator`
- `nbc.c1oud_mall.refund.domain.RefundBreakdown`
- ADR-0007 (이전 도메인 결정) — 패턴 참고용
