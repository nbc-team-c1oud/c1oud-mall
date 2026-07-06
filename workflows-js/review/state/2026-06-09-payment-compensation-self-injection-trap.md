# [트러블슈팅] self-injection 함정 — 메인 TX 롤백 + 보상 TX 커밋을 한 클래스에서 못 푼다

---
date: 2026-06-09
domain: [payment, transaction]
tags: [requires-new, self-injection, proxy-aop, transaction-boundary]
related-story: workflows/products/product.md Story 2-3
related-adr: workflows/living-docs/Ai-adr/003-portone-compensation-transaction-pattern.md, src/main/java/.../payment/docs/adr/0004-payment-compensation-transaction-pattern.md
related-topology: workflows/topologys/tx-topology.md, workflows/topologys/Error-Handling-Topology.md
---

## 1. 문제 (What)

결제 확정 보상 흐름의 요구:
- 메인 TX(검증 단계)는 검증 실패 시 **롤백** — markCompleted/포인트 적립 등 부수효과 미반영 보장
- 보상 TX(Payment markFailed + Order cancel + 재고 복구)는 **commit 보존** — 메인 롤백이 보상까지 휩쓸면 무의미
- PortOne 취소 호출은 TX 종료 후

한 호출 안에서 *메인 롤백 + 보상 커밋*이 공존해야 함.

처음에 AI가 제시한 패턴:

```java
@Service
public class PaymentConfirmationService {
    @Autowired
    private PaymentConfirmationService self;   // self-injection
    
    @Transactional
    public void confirm(...) { ... }
    
    @Transactional(propagation = REQUIRES_NEW)
    public void compensateDb(...) { ... }
    
    // confirm 안에서 self.compensateDb(...) 호출
}
```

Spring `@Transactional`은 AOP 프록시 기반 — *같은 클래스 내부 호출*은 프록시를 거치지 않음. 그래서 `self.compensateDb(...)`로 자기 자신을 다시 주입받아 호출하는 패턴.

이게 동작은 하지만 코드를 읽는 사람은:
- "왜 자기 자신을 주입하지?"
- "self.X()와 this.X()가 다른가?"
- "REQUIRES_NEW는 진짜 새 TX인가?"

라는 질문을 매번 다시 함. 게다가 self-injection은 *순환 참조 의심*도 키움.

## 2. 문제 해결 방법 (How)

1. **self-injection 대신 별도 컴포넌트로 분리**:
   - `PaymentCompensationService` — 비-트랜잭션 메서드 (오케스트레이션만)
   - `PaymentCompensationTxOp` — 별도 `@Component`, `@Transactional(REQUIRES_NEW)` 메서드 보유
2. **흐름 분리**:
   ```
   PaymentConfirmationService.confirm   ← 메인 TX
     try { 검증 단계 }
     catch (BusinessException ex) {
         if (isCompensable(ex)) {
             paymentCompensationService.compensate(...)
               → paymentCompensationTxOp.compensateDb(...)   ← REQUIRES_NEW 별도 TX 커밋
               → portOnePaymentCancelPort.cancel(...)         ← TX 밖 외부 호출
         }
         throw ex;   ← 메인 TX 롤백
     }
   ```
3. **isCompensable 화이트리스트**:
   - `PM001` (금액 불일치) → 보상 O
   - `PM007` (PortOne PAID 아님) → 보상 O
   - `PM006` (소유권 위반) → 보상 X (다른 사용자의 정상 결제일 수 있음)

## 3. 방식 (Why this way)

### 별도 컴포넌트 vs self-injection
- self-injection은 *동작*은 하지만 *의도가 코드 표면에 안 보임*. "왜 self?"라는 질문이 매번 반복.
- 별도 컴포넌트(`PaymentCompensationTxOp`)는 *클래스 이름만으로* 의도가 드러남:
  - "Tx"가 붙어있으니 트랜잭션 경계 분리용
  - "Op"가 붙어있으니 단일 책임 (compensateDb)
- 클래스가 1개 늘어나는 비용 vs 의도 가독성. 후자가 압도적.

### isCompensable 화이트리스트
- 처음엔 *모든 검증 실패에 일괄 보상*도 검토. 단순하지만 PM006(소유권 위반)이 문제:
  - 공격자가 다른 사용자의 portonePaymentId를 알아내 confirm 호출 시도
  - 보상 발동 시 *정상 결제*가 취소됨 → 2차 피해
- 화이트리스트로 *보상이 안전한 케이스*만 발동.

### PortOne 호출 위치 (TX 밖)
- consistency.md §6: "DB 먼저 커밋, 외부 호출은 TX 밖"
- TX 안에서 외부 호출하면:
  - DB 락이 PG latency만큼 hold
  - 외부 실패 시 DB도 롤백 → 보상 의도 자체가 사라짐
- compensate 메서드 자체는 비-트랜잭션. 내부에서 `compensateDb` (REQUIRES_NEW 커밋) → PortOne 호출 순서.

### PortOne 취소 실패는 log만
- 호출 실패는 `log.error` + 마커(`[PORTONE_CANCEL_FAILED]`)만, 호출자에 예외 전파 X
- 보상은 *운영 보강 작업*으로 간주 — 사용자 응답엔 영향 X (사용자는 원 실패의 4xx 받음)

## 4. 결과 (Outcome)

- 코드 반영:
  - `PaymentCompensationService.compensate` — 비-트랜잭션 메서드
  - `PaymentCompensationTxOp` — 별도 `@Component`, `compensateDb` (REQUIRES_NEW)
  - `PaymentConfirmationService.confirm` — try-catch + isCompensable + compensate 호출 + 원 예외 재 throw
- 단위 테스트: `PaymentCompensationTxOpTest` (3 케이스 — 정상 보상 / 일부 실패 시 동작 / PG 호출 실패 시 log)
- 통합 테스트: `PaymentConfirmationServiceIntegrationTest`에서 금액 불일치 → 보상 흐름 E2E
- 메인 TX 롤백 + 보상 TX 커밋이 한 호출 안에서 명확히 분리됨

## 5. 좋아진 점 (What got better)

- **"클래스 1개 추가 비용 vs 의도 명확성"의 trade-off 판단이 정착.** Self-injection을 *완전히* 금지하기보단, "self-injection이 코드 의도를 흐리면 별도 컴포넌트로 분리"라는 기준이 생김. 이후 환불 트랜잭션(`RefundTxOp`)도 같은 패턴 즉시 채택.
- **`isCompensable` 화이트리스트 사고법.** "모든 케이스에 같은 동작" 패턴이 *방어 코드를 줄이지만 2차 피해를 키운다*는 인식. 같은 사고로 환불에서도 `RF003`(소유권)은 보상 안 함 결정.
- **`[PORTONE_CANCEL_FAILED]` 같은 로그 마커 패턴이 운영 관측 도구화 됨.** `grep`/alert 등록이 쉬워서, 운영 진입 후에도 "검색 가능한 로그"가 의식적으로 짜이게 됨. 환불 흐름도 `[REFUND_PG_CANCEL_FAILED]` 마커 채택.
- **AI가 처음에 *Spring의 표준 트릭*(self-injection)을 자연스럽게 제시한다는 것을 학습.** 표준 패턴이 *우리 코드 가독성 기준*에 맞지 않으면 뒤집을 수 있어야 함. AI 답을 "기술적 정답"으로 받아들이지 않고 "코드 가독성·의도 측면에서 더 나은 대안이 있는지" 한 번 더 확인하는 습관.
