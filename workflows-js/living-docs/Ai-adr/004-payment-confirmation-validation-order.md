# AI-ADR-004: 결제 확정 7단계 검증 순서 — 외부 호출 우선, 멱등 가드는 DB 조회 직후

- 상태: Accepted
- 일자: 2026-06-02
- 관련 Story: `workflows/product.md` Story 2-2 (결제 확정 도메인 서비스)
- 관련 ADR:
  - `payment/docs/adr/0003-bc-collaboration-mock-classes.md`
  - `payment/docs/adr/0004-payment-compensation-transaction-pattern.md`
- 관련 룰: `.claude/rules/idempotency.md §4` (S+ 등급 — DB 유니크 + 사전조회 이중)

---

## Context

`POST /api/v1/payments/confirm`는 다음을 차례로 검증해야 한다.

후보 검증 항목 (순서 무관 나열):
- (a) PortOne 재조회 — 외부 호출, 비용·latency 큼
- (b) DB Payment 조회 — 빠름
- (c) 이미 COMPLETED인가 (멱등 가드)
- (d) JWT userId == payment.userId (소유권)
- (e) 요청 orderId == payment.orderId
- (f) PortOne 상태 == PAID
- (g) PortOne 승인 금액 == payment.pgAmount

순서를 잘못 잡으면:
- **외부 호출을 먼저 했는데 멱등 재호출이면** → PortOne API 비용 낭비 + latency
- **금액 검증을 먼저 했는데 소유권 위반이면** → 다른 사용자 결제 정보 노출
- **DB 조회를 가장 나중에 두면** → PortOne 호출했는데 결제 자체가 우리 DB에 없는 경우 헛수고

→ 순서가 검증의 **비용/안전성/정확성** 모두에 영향.

---

## Decisions

### 검증 7단계 (실제 코드 순서)

| 단계 | 검증 | 실패 시 ErrorCode | 비고 |
|---|---|---|---|
| 1 | PortOne 재조회 | `PM004`(5xx/타임아웃) / `PM005`(4xx/파싱 실패) | TX 시작 전 |
| 2 | DB Payment 조회 (`portonePaymentId`) | `PM008` (404) | |
| 3 | 이미 COMPLETED? (`payment.isCompleted()`) | — (즉시 200 + `alreadyCompleted=true` 반환) | **멱등 가드** |
| 4 | 소유권 (`payment.verifyOwnership(userId)`) | `PM006` (403) | |
| 5 | orderId 일치 (`payment.orderId == request.orderId`) | `PM010` (400) | |
| 6 | PortOne 상태 == PAID | `PM007` (400) — **보상 자동** | |
| 7 | 금액 (`portone.amount.total == payment.pgAmount`) | `PM001` (400) — **보상 자동** | |

### 핵심 결정 4가지

1. **PortOne 재조회를 1순위로** (DB 조회 전): "본문 비신뢰" 원칙. 외부에서 받은 키만 신뢰하고 나머지는 PortOne API 응답으로 검증.
2. **멱등 가드는 DB 조회 직후 (3단계)**: 멱등 재호출이면 PortOne 호출이 이미 1단계에서 일어났지만, 그 후 모든 부수효과를 건너뛰고 즉시 동일 응답. PortOne 호출은 짧은 read-only라 멱등 호출도 안전.
3. **소유권을 멱등 가드 후로** (4단계): 멱등 응답은 본인이 다시 부른 경우라 안전. 다른 사용자 호출은 4단계에서 차단.
4. **보상 자동 발동 단계는 6, 7만** (PM007, PM001): 3~5단계는 사용자 식별·경로 실수 → 원본 결제는 그대로 둠. 6~7은 PG/금액 위변조 의심 → PortOne 결제 자체 취소 필요.

---

## 프롬프트 내용 (AI 활용시)

> "결제 확정 API에서 검증해야 할 게 7가지 정도 있습니다:
> - PortOne 재조회 (외부 호출, 비싸요)
> - DB Payment 조회
> - 이미 COMPLETED인가 (멱등 가드)
> - 소유권 (JWT vs Payment)
> - orderId 일치
> - PortOne status == PAID
> - 금액 일치
>
> 어떤 순서가 맞나요? 세 가지 고민:
> 1) 외부 호출(PortOne 재조회)를 먼저 vs 나중? 비용은 아끼고 싶은데 본문 비신뢰 원칙도 있고
> 2) 멱등 가드를 어디에 두면 PortOne 호출까지 헛수고 안 하게 되나요?
> 3) 어떤 실패가 PortOne 보상 취소를 자동 발동해야 하나요?"

---

## AI 응답 요약

### Q1: PortOne 재조회 위치
- **1순위로 두는 게 맞음.** 본문 비신뢰가 핵심 원칙이고, PortOne 응답이 모든 검증의 신뢰 기반.
- 비용을 아끼려면 **DB 조회를 먼저** 두고 멱등 재호출이면 PortOne 안 부르는 패턴도 가능. 그러나 그러면 *외부에 검증 안 된 키로 DB 조회*하게 되고, 멱등 재호출이 아닌 첫 호출의 경우 PortOne 호출은 어차피 필요.
- 트레이드오프: 멱등 재호출 비율이 낮으면 **PortOne 먼저** OK. 매우 높으면 **DB 먼저 + 멱등 가드 + 미완료면 PortOne** 패턴 고려.
- 본 프로젝트: 정상 흐름에서 멱등 재호출은 더블클릭·재시도 정도라 비율 낮음 → **PortOne 먼저** 권장.

### Q2: 멱등 가드 위치
- **DB 조회 직후가 표준.** 이미 COMPLETED면 부수효과 한 줄도 추가 실행 안 하고 동일 응답.
- 소유권 검증은 멱등 가드 *뒤*에 둬도 안전 (멱등 재호출은 결국 같은 결제, 같은 사용자가 다시 부른 케이스라 가정 가능).
- 그러나 **금액 검증을 멱등 가드 앞에 두면 안 됨** — 멱등 재호출인데 금액 다시 검증할 이유 없음.

### Q3: 보상 발동 단계
- 사용자 식별 실수(401/403/404) → **보상 X.** 원본 결제는 다른 사용자의 정상 결제일 수 있음.
- **금액 위변조(PM001) + status 불일치(PM007)** → 보상 O. PortOne은 결제 처리됐는데 우리 검증이 실패한 케이스.
- → **6, 7단계 실패만 보상 자동** (AI-ADR-003 isCompensable 화이트리스트와 일치).

### 외부 호출(PortOne 재조회) 위치 — 트랜잭션 관점
- **트랜잭션 시작 전**에 수행. 트랜잭션 안에서 외부 호출하면 DB 락이 외부 latency 동안 hold (consistency §6).

---

## Consequences

### 장점
- "본문 비신뢰" 원칙이 1순위에 명확히 박힘 — 코드 읽는 사람이 의도 파악 즉시
- 멱등 가드가 DB 조회 직후라 부수효과는 단 한 번도 실행 안 됨
- 보상 자동 발동 단계가 명확 — `isCompensable` 화이트리스트와 1:1 매핑
- 모든 외부 호출이 트랜잭션 밖 → 락 hold time 짧음

### 단점
- 멱등 재호출 시에도 PortOne 재조회가 한 번 발생 — 외부 API 비용 약간 더 듬 (재호출 빈도가 낮아 수용)
- 검증 순서가 7단계로 길어 첫 코드 읽을 때 인지 부담 ↑ — README/주석으로 보강 필요
- DB 조회 전 PortOne 호출하므로, 존재하지 않는 `portonePaymentId`에도 PortOne 호출 1회 발생 (PM005 처리)

---

## 내가 수정한 부분

- AI는 처음에 "DB 조회 → 멱등 가드 → 미완료시에만 PortOne" 순서를 제시했음. 이렇게 하면 멱등 재호출 비용은 아끼지만, **"외부에서 받은 키 검증 없이 DB 조회"가 의미적으로 어색.** 본문 비신뢰 원칙 명확화를 위해 **PortOne 먼저**로 변경.

- 멱등 재호출 비용 우려: AI는 "비용 비교 후 결정" 입장이었는데, **본 프로젝트는 멱등 재호출 빈도가 낮음**(더블클릭/네트워크 재시도 정도) 가정 → 비용보다 의도 명확성 우선.

- **소유권 검증을 멱등 가드 뒤에 두는 게 안전한지** 추가 확인. 멱등 응답은 *같은 결제에 대한 재요청*이므로 본인 결제 가정 OK. 다만 **공격자가 다른 사람의 portonePaymentId로 멱등 응답 받아 정보 노출** 위험은 있음 — 응답 데이터에 `paymentId/status/alreadyCompleted`만 포함, 사용자 정보·금액 등은 미포함으로 한정. 응답 DTO 최소화로 결정.

- **orderId 일치 검증(PM010)을 5단계로 추가**: AI는 처음엔 이 검증을 빠뜨림. orderId가 다르면 `OrderFacade.completeOrder`가 잘못된 주문을 완료 처리할 수 있어 명시적 검증 추가.

- **보상 자동 발동 단계 명시**: 처음엔 모든 4xx에 보상 발동 가능성 두려 했음. PM006(소유권)은 보상 X로 명시적 결정 (다른 사용자의 정상 결제일 수 있음).

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `PaymentConfirmationService.confirm` — 위 1~7단계 순서대로
  - PortOne 호출은 `PortOnePaymentQueryPort` (RestClient 동기)
  - 멱등 가드: `payment.isCompleted()` 즉시 200 + `alreadyCompleted=true`
  - 보상 분기: `isCompensable(ErrorCode)` 화이트리스트 (PM001, PM007)
- ✅ ErrorCode 추가:
  - `PORTONE_QUERY_FAILED("PM004", "...", BAD_GATEWAY)`
  - `PORTONE_RESPONSE_INVALID("PM005", "...", BAD_GATEWAY)`
  - `PAYMENT_AUTHORIZATION_FAILED("PM006", "...", FORBIDDEN)`
  - `PORTONE_PAYMENT_NOT_PAID("PM007", "...", BAD_REQUEST)`
  - `PAYMENT_NOT_FOUND("PM008", "...", NOT_FOUND)`
  - `PAYMENT_ORDER_MISMATCH("PM010", "...", BAD_REQUEST)`
- ✅ 응답 DTO 최소화: `PaymentConfirmResponse(paymentId, portonePaymentId, status, alreadyCompleted)` — 소유권 멱등 우회 시 추가 정보 노출 방지
- ✅ FE 가이드 §8.1 (v1·v2·v3 공통) — "7단계 검증" 표로 노출
- 미래 트리거: 멱등 재호출 비율 측정 후 "DB 먼저" 패턴 재검토
