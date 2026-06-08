# ADR-0009: 적립 포인트 정책 (적립률·산정 기준·회수)

- 상태: Accepted
- 일자: 2026-06-08
- 범위: 결제·환불 BC + 포인트 BC

## Context

`PaymentConfirmationService.confirm()`는 적립 포인트 산정 자리가 비어있는 채로 `long pointEarnedAmount = 0L;`로 하드코딩돼 있었다. 결과적으로 모든 결제에서 `accruePoints` 분기가 한 번도 실행되지 않아 적립이 실질적으로 작동하지 않음. 추가로 환불 시 적립 포인트의 회수 처리도 정의되지 않은 상태였다.

운영 진입 전에 다음 4가지를 정해야 한다:
1. 적립 시점
2. 적립률 산정 기준 (어떤 금액의 몇 %)
3. 회수 시점·산식
4. 회수 시 잔액 부족 처리

## Decision

### 1. 적립 시점
`PaymentConfirmationService.confirm()` 내부, PG 검증 통과 후 `payment.markCompleted` 직전. 결제 확정과 동일 트랜잭션이므로 결제 성공·적립이 원자성 보장.

### 2. 적립률 및 산정 기준
- **기준액**: `Payment.breakdown.totalAmount` (PG + 포인트 결제 합계). 포인트 결제 사용자도 차별 없음.
- **적립률**: `1.00%` (basis points 100)
- **산식**: `pointEarnedAmount = floor(totalAmount × basisPoints / 10_000)`

### 3. 정책 클래스
`nbc.c1oud_mall.common.config.PointPolicy`(record + `@ConfigurationProperties(prefix="points")`)로 외부화. `application.yml`의 `points.accrual-rate-basis-points` 키로 운영 단계에서 코드 변경 없이 조정 가능.

basis points(1bp = 0.01%)로 표현해 0.5%·1.25% 등 미세 조정 시에도 정수 산술로 정확.

### 4. 환불 시 회수 산식
ADR-0008의 PG floor + 포인트 잔액 흡수 패턴과 동일하게 **비례 회수**.

`pointEarnedRecoverAmount = floor(totalRefundAmount × payment.pointEarnedAmount / payment.totalAmount)`

이 값은 `RefundAmountCalculator`에서 산정해 `RefundBreakdown.pointEarnedRecoverAmount`로 영속, `RefundTxOp.executeRefund`에서 `PointService.cancelEarnedPoints`를 호출해 차감.

### 5. 회수 시 잔액 부족 처리 — Lenient
사용자가 이미 적립 포인트를 사용해 잔액이 회수액보다 작은 경우:
- **잔액 한도까지만 차감** (음수 잔액 방지)
- 부족분은 `log.warn("[POINT_EARNED_RECOVER_SHORT] ...")` 마커로 회계 모니터링용 기록
- **환불 자체는 진행** — 차단 시 사용자 불편 + PG 취소 race 위험

도메인 메서드 `User.useEarnedPointsLenient(amount)`이 실제 차감액을 반환하고, `PointService.cancelEarnedPoints`가 부족 시 warn 로깅 + 실제 차감액으로만 PointHistory(EARN_CANCEL) 저장.

## Alternatives

- **`pgAmount × 1%` (PG 결제분만)**: 포인트 결제 사용자가 적립을 못 받아 차별. 거절.
- **정액 적립 (주문당 100p)**: 주문 규모 무시. 거절.
- **회원 등급별 차등 (VIP 2%, 일반 1%)**: 현 시점 등급 시스템 미구현. `PointPolicy`에 정의된 단일 basis points로 시작하고 등급 도입 시 확장.
- **회수 시 잔액 부족 → 환불 거부 (strict)**: 환불 차단은 사용자 경험 저해 + PG는 이미 취소 흐름에 들어가 race 위험. 거절.
- **회수 시 음수 잔액 허용**: 회계 추적 복잡 + 다음 결제 시 음수 처리 분기 필요. 거절.

## Consequences

### 장점
- 단일 yml 설정으로 적립률 변경 가능 — 재빌드 없이 운영 정책 조정
- ADR-0008 환불 산정 정책과 일관된 비례 분리 패턴
- 회수 lenient로 환불 흐름의 견고성 ↑
- PointHistory(EARN/EARN_CANCEL)에 모든 적립·회수 흔적 영속 → 회계 감사 가능

### 단점
- `RefundBreakdown`에 컬럼 추가 → 기존 환불 row가 null 갖지 않도록 ddl 갱신 필요 (dev: create-drop, 운영: 마이그레이션 스크립트)
- 회수 부족분이 log 마커로만 남음 → 운영 대시보드에서 별도 집계 필요

## References

- ADR-0008 — 환불 금액 비율 분리 정책
- consistency.md §5 — 락 순서 (Order → Payment → Point → Inventory)
- `nbc.c1oud_mall.common.config.PointPolicy`
- `nbc.c1oud_mall.refund.domain.RefundAmountCalculator`
- `nbc.c1oud_mall.point.application.PointService` — `accruePoints` / `cancelEarnedPoints`
