# [백엔드] PortOne 취소 port 확장 — null=키 제외, 부분취소+멱등키 통합 시그니처

---
date: 2026-06-07
tags: [payment, portone, port-interface, idempotency, json-serialization, testing]
sprint: refund-story-2-1
related-code: payment/application/PortOnePaymentCancelPort.java, payment/infrastructure/portone/PortOneCancelRequest.java
---

---

## 1. 상황 (Context)

환불 흐름(Story 2-2 `RefundProcessService`)에서 PortOne에 부분 취소를 호출해야 했다.
기존 port 시그니처는 `cancel(portonePaymentId, reason)` — 전액 취소 전용이었다.

추가로 필요한 것 두 가지:
- `amount`: 부분 취소 금액. 전액 취소 시엔 PortOne API가 body에서 **키 자체**를 빼야 한다 (`"amount": null` 전달 시 동작 미정의).
- `requestKey`: PG 측 멱등키. 없으면 PortOne이 `paymentId` 기반 dedup을 자체 처리. 단, 환불 흐름에서 쓸 키(`refund-{id}`)와 보상 트랜잭션의 키가 충돌하면 안 된다.

---

## 2. 선택지와 결정 (Options & Decision)

### 선택지 A — 메서드 오버로드
```java
void cancel(String portonePaymentId, String reason);
void cancel(String portonePaymentId, Long amount, String reason, String requestKey);
```
- 장점: 기존 호출부 무수정
- 단점: port 인터페이스에 메서드 두 개, 실제 전달 규칙(언제 amount가 null인가)이 두 시그니처에 흩어짐

### 선택지 B — 단일 시그니처, null=전체취소 의미론 ⭐
```java
void cancel(String portonePaymentId, Long amount, String reason, String requestKey);
```
- `amount=null` → 전액 취소 (JSON body에서 키 제외)
- `requestKey=null` → PortOne 자체 dedup (JSON body에서 키 제외)
- 장점: port 계약이 하나, null의 의미가 JavaDoc에 명시되어 명확
- 단점: 기존 호출처(`PaymentCompensationService`) 시그니처 갱신 필요

**결정**: B — port는 단일 계약이어야 호출처마다 다른 규칙이 생기지 않는다.

### JSON 직렬화 문제 해결

`PortOneCancelRequest`가 record이기 때문에 `null` 필드를 body에서 제외하려면
커스텀 직렬화 없이 `@JsonInclude(NON_NULL)` 하나로 해결 가능했다.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneCancelRequest(String reason, Long amount, String requestKey) {}
```

`amount=null`이면 JSON 직렬화 시 `"amount"` 키 자체가 빠진다.
PortOne API 명세에서 `amount` 키가 없는 것 = 전체 취소로 처리한다.

---

## 3. 구현하면서 마주친 것 (What I Hit)

**기존 호출처 갱신 비용이 예상보다 컸다.**
`PaymentCompensationService`(보상 트랜잭션)가 `cancel`을 이미 쓰고 있었고,
테스트 두 곳(`PaymentCompensationServiceTest`, `PaymentConfirmationServiceIntegrationTest`)도
`verify(port).cancel(id, reason)` 형태로 인자를 고정하고 있었다.
시그니처를 바꾸는 순간 테스트가 전부 컴파일 에러 — 갱신해야 하는 파일이 예상보다 3개 더 있었다.

**`requestKey` 충돌 위험을 뒤늦게 인식했다.**
`PaymentCompensationService`는 전액 취소 + `requestKey=null`로 호출해야 한다.
만약 `requestKey`를 아무 값이나 넣었다면 나중에 환불 흐름이 같은 키로 부분 취소를 시도할 때
PortOne이 "이미 처리된 요청"으로 튕겨낼 수 있었다.
커밋 메시지에 `(환불 흐름 refund-{id} 키와 충돌 방지)`를 명시한 이유가 이것이다.

---

## 4. 배운 것 (Learnings)

- **HTTP body에서 "키 없음"과 "키=null"은 다르다.** PortOne처럼 외부 API가 이 둘을 구분하는 경우, record에 `@JsonInclude(NON_NULL)`을 붙이는 것이 가장 단순한 해법이다. 커스텀 serializer나 `Optional` 필드보다 훨씬 적은 코드로 같은 효과를 낸다.
- **port 시그니처가 바뀌면 테스트의 `verify` 인자도 전부 바꿔야 한다.** 통합 테스트에 mock이 들어가 있으면 시그니처 변경 비용이 숨어 있다. port를 확장하기 전에 호출처를 먼저 grep하는 습관이 필요하다.
- **멱등키 충돌은 키 네임스페이스 설계 문제다.** 보상 트랜잭션용 키(`paymentId`)와 환불 흐름용 키(`refund-{id}`)를 처음부터 다른 형식으로 약속해두면 충돌을 구조적으로 막을 수 있다.

---

## 5. 잘 된 것 (Went Well)

- **직렬화 검증 테스트를 별도 `@Nested` 그룹으로 분리한 것.** 4가지 조합(전체/부분, 멱등키 있음/없음)을 각각 독립 케이스로 만들어서 어느 조합이 실패해도 바로 식별 가능하다. `jsonPath("$.amount").doesNotExist()`로 "키가 없음"을 단언하는 패턴이 깔끔했다.
- **port JavaDoc에 null 의미를 명시한 것.** 다음에 이 port를 구현하는 사람이 코드만 보고도 `null=전체취소`, `null=PortOne 자체 dedup`을 이해할 수 있다.

---

## 6. 아쉬운 것 (To Improve)

- 시그니처 변경 전에 `grep -r "cancel("` 같은 호출처 탐색을 먼저 했다면 기존 테스트 수정이 한 번에 계획될 수 있었다. 구현 → 컴파일 에러 발견 → 수정 순서로 진행해서 불필요한 왕복이 생겼다.
- `PortOneCancelRequest`에 필드가 늘어나면 `@JsonInclude(NON_NULL)` 하나로는 의미가 모호해질 수 있다. 필드 수가 5개를 넘어가면 커스텀 serializer나 별도 builder 패턴을 고려할 시점이다.

---

## 7. 다음 행동 (Next Actions)

- [ ] Story 2-2 `RefundProcessService`에서 이 port를 실제로 호출할 때, `requestKey` 형식을 `refund-{refundId}` 로 고정하는 상수나 팩토리 메서드를 domain에 두기 (키 형식이 흩어지지 않도록)
- [ ] port 시그니처 변경이 예상될 때 사전에 `Grep("cancel(")` 으로 호출처 목록 확인하는 습관 정착
