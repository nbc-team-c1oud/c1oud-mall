# AI-ADR-011: `PortOnePaymentCancelPort` 확장 — 결제 보상과 환불 공유, 부분취소 + 멱등키, PG 실패 시 202 응답

- 상태: Accepted
- 일자: 2026-06-05
- 관련 Story: `workflows/products/product02.md` Story 2-1 + Story 2-3
- 관련 ADR:
  - `payment/docs/adr/0004-payment-compensation-transaction-pattern.md` (보상 흐름의 기존 포트)
  - AI-ADR-003 (보상 패턴) + AI-ADR-010 (환불 트랜잭션)

---

## Context

PortOne 결제 취소 호출은 **두 곳**에서 일어난다:
1. **결제 확정 보상 흐름** — PM001/PM007 검증 실패 시 PortOne 전체 취소 (AI-ADR-003)
2. **환불 흐름** — 사용자 환불 요청 시 PortOne 부분 또는 전체 취소

두 곳 모두 *같은 PortOne API*를 호출하는데, 결제 보상은 *전체 취소*만 필요하고 환불은 *부분 취소*도 지원해야 한다.

결정 사항:
- **포트 공유 vs 분리** — 두 흐름이 같은 인터페이스를 쓸지, 각자 가질지
- **시그니처 확장** — 기존 보상 호출부에 영향을 주지 않으면서 부분취소·멱등키 추가
- **멱등키 전달** — PortOne API의 멱등키 파라미터 활용
- **PG 실패 시 응답** — 환불은 DB 커밋 완료 후 PG 실패 가능 → API 응답을 어떻게 구분할지

선택지(포트 공유):
- (a) 동일 포트 `PortOnePaymentCancelPort` 시그니처 확장 (`amount`, `requestKey` 추가)
- (b) 보상용 / 환불용 포트 분리
- (c) 보상용은 그대로, 환불용 포트 신설

선택지(PG 실패 응답):
- (i) 500 또는 502 → 사용자 입장에서 "환불 실패" (DB는 이미 처리)
- (ii) 200 + status 필드 → 성공/실패가 status로만 구분
- (iii) **202 Accepted + warning 필드** → "환불 접수 완료, PG 취소 처리 진행 중"

---

## Decisions

### 1. 단일 포트 시그니처 확장 (옵션 a)
```java
void cancel(String portonePaymentId,
            Long amount,           // null = 전체취소
            String reason,
            String requestKey);    // 멱등키
```

### 2. `amount` nullable
- `null` → 어댑터가 PortOne 요청 본문에서 `amount` 키 *제외* → 전체 취소
- 값 있음 → 부분 취소

### 3. 어댑터 구현 (`PortOneCancelRequest`)
- `@JsonInclude(NON_NULL)` — null 필드 직렬화 제외
- `amount`·`requestKey`도 nullable, 보상 호출부와 호환

### 4. 멱등키 = `"refund-{refund.id}"`
- Refund ID 기반 — PortOne 측 중복 취소 방지
- 보상 호출부는 멱등키 null로 호출 가능 (기존 호환)
- 환불은 항상 멱등키 전달

### 5. PG 실패 시 응답 (옵션 iii)
- PG 취소 성공 → **200** + `RefundResponse` (`refundStatus=PG_CANCELLED`)
- PG 취소 실패 (DB는 커밋됨) → **202 Accepted** + `RefundResponse` (`refundStatus=DB_COMMITTED`) + `warning` 필드
- 잔여 수량 초과 → **409** + `ApiResponse.error("RF001", ...)`
- 환불 불가 상태 → **409** + `ApiResponse.error("RF002", ...)`
- 소유권 위반 → **403** + `ApiResponse.error("RF003", ...)`

### 6. 결제 보상 호출부 (`PaymentCompensationService`) 호환성
- `cancel(portonePaymentId, null, reason, null)` — 기존 동작 그대로
- 새 파라미터 nullable이라 동작 변화 없음

---

## 프롬프트 내용 (AI 활용시)

> "결제 보상과 환불 둘 다 PortOne 취소 API를 호출합니다.
> 결제 보상은 전체 취소만, 환불은 부분 취소도 지원.
>
> 옵션:
> (a) 단일 포트 PortOnePaymentCancelPort 시그니처 확장 (amount, requestKey 추가)
> (b) 보상용 / 환불용 분리
> (c) 보상은 그대로, 환불 신설
>
> 그리고 환불 흐름 특성상 *DB는 이미 commit됐는데 PG 호출만 실패*하는 케이스가 가능합니다.
> 이때 HTTP 응답을 어떻게 보내는 게 맞을까요?
> - 500/502 (실패)
> - 200 + status 필드
> - 202 Accepted + warning"

---

## AI 응답 요약

### 포트 공유 vs 분리
- **(a) 단일 포트 시그니처 확장** 권장.
- 분리 시 어댑터 코드 중복 (PortOne API 호출은 결국 같음). 변경 시 두 곳 수정.
- 시그니처 확장이 부담되는 케이스는 *호출부가 많을 때*인데, 보상은 1곳·환불은 1곳뿐 → 영향 최소.
- nullable 파라미터를 활용하면 기존 호출부는 그대로 동작.

### PG 실패 시 응답
- **(iii) 202 Accepted + warning 필드** 권장.
- 202 의미: "요청 수락됨, 처리 진행 중" — DB 환불 처리는 완료, PG 취소는 운영 보강 대기 중이라는 정확한 의미.
- 500/502는 *환불 자체가 실패*했다는 의미라 사용자가 혼란 (DB는 이미 처리됨)
- 200 + status 필드는 HTTP 상태와 응답 내용이 불일치 (FE가 200 받으면 "성공"으로 처리)
- 202를 FE가 받으면 *"환불 접수 완료, PG 진행 중"* 안내 가능

### 멱등키 형식
- `"refund-{refund.id}"` — Refund PK가 unique이므로 멱등키도 unique
- PortOne은 같은 멱등키로 재호출 시 첫 응답과 동일하게 반환 (PG 측 중복 취소 방지)
- 결제 보상에서는 멱등키 null로 가능 (PortOne이 무시) — 보상은 1회성 가정

### `PortOneCancelRequest` 직렬화
- `@JsonInclude(NON_NULL)` 필수. null 필드를 JSON으로 보내면 PortOne이 *명시적 0*으로 해석할 수 있음.
- amount가 null이면 키 자체가 빠져야 PortOne이 *전체취소*로 해석.

---

## Consequences

### 장점
- **포트 1개로 두 흐름 통합** — 코드 중복 0, 변경 시 1곳 수정
- 보상 호출부 영향 없음 (nullable 추가 파라미터)
- PortOne 멱등키로 PG 측 중복 취소 차단
- 202 응답으로 *"DB는 완료, PG는 보강 대기"* 의미 정확히 전달
- FE는 200/202/409/403 분기로 명확한 UX 분기 가능

### 단점 / 알려진 한계
- 시그니처에 nullable 파라미터 2개 추가 → 호출부에서 "왜 null이지?" 의문 가능 → 주석 또는 호출부 컨벤션 명시 필요
- 202 응답을 FE가 항상 정확히 처리해주리라는 가정 — FE 가이드에 명시 (FE 통합 가이드 §10에 환불 추가 시 보강)
- 멱등키가 Refund.id 기반이라 *같은 환불에 대한 재시도*는 멱등이지만, *같은 paymentId에 새 환불*은 다른 멱등키 → PortOne에서 같은 결제에 여러 취소 호출 발생 (의도된 동작)
- PG 취소 실패 시 자동 재시도 없음 — 운영 보강 필요

### 운영 진입 전 필수
- 202 응답에 대응하는 FE UX 정의 (대기/안내 메시지)
- PG 취소 실패 → 운영 알람 + reconciliation 잡

---

## 내가 수정한 부분

- AI는 처음에 **(c) 보상은 그대로, 환불 신설**을 *책임 분리* 명목으로 제시. 어댑터 코드 중복 + 변경 비용을 들어 **(a) 단일 포트 확장**으로 변경. 영향 받는 호출부가 2곳뿐이라 분리의 가치 없음.
- AI는 *환불 실패 시 5xx 반환*도 옵션으로 열어둠. **5xx는 의미 어긋남** (DB는 commit됐는데 사용자가 "환불 실패" 메시지 받음). 202로 강하게 결정.
- AI는 멱등키를 `UUID.randomUUID()`로 매번 새로 발급 제안. **Refund.id 기반**으로 변경 (`"refund-{id}"`). 재시도 시 멱등성이 살아야 의미 있음. UUID 매번 발급은 멱등키 의미 자체를 무효화.
- AI 응답에 *`amount=0` 호출 시 PortOne 동작*에 대한 명확한 답이 없음. **PortOne 0원 취소 호출 자체를 빼고** RefundProcessService에서 skip 분기로 처리 (AI-ADR-010과 일관) → 포트는 0이 들어오는 케이스 자체 없음 (안전).
- `@JsonInclude(NON_NULL)` 직렬화 정책을 AI 응답에서 명시적으로 안 언급 → ADR에 박아둠. null 직렬화가 PortOne 본문 해석 오류로 이어질 수 있음 (전체취소 의도가 0원 취소로 오해되는 등).
- `warning` 필드를 처음엔 별도 응답 DTO로 분리하자는 AI 제안. **`RefundResponse`에 nullable warning 필드 추가**로 단순화. DTO 1개로 200/202 모두 사용.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `payment.application.PortOnePaymentCancelPort.cancel(portonePaymentId, amount, reason, requestKey)`
  - `payment.infrastructure.PortOnePaymentCancelAdapter` — `@JsonInclude(NON_NULL)`
  - `PortOneCancelRequest` 레코드 — `amount`·`requestKey` nullable
  - `RefundProcessService.process` — `cancel(..., breakdown.getPgRefundAmount(), command.reason(), "refund-" + refund.getId())`
  - 보상 흐름 (`PaymentCompensationService`) — 기존 nullable 호출 유지
- ✅ HTTP 응답:
  - `RefundController` — PG 성공 200 / PG 실패 202 분기 (RefundResult.refundStatus 기준)
  - `RefundResponse` — nullable `warning` 필드
- ✅ 어댑터 단위 테스트: 부분취소 본문 / 전체취소 본문(amount 키 제외) / 멱등키 전달
- ✅ 결제 보상 호환성 회귀 테스트 통과
- 미래 트리거:
  - FE 통합 가이드에 환불 섹션 추가 (현재 v3 §10은 "Refund 사용자 API 없음" — 컨트롤러 노출 인지 후 갱신 필요)
  - PG 취소 자동 재시도 ADR
  - 202 응답 FE UX 가이드 추가
