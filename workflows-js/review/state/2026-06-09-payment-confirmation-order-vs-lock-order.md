# [트러블슈팅] 결제 확정 처리 순서가 락 순서와 어긋남 — AI 초기 제안이 데드락 위험

---
date: 2026-06-09
domain: [payment, concurrency]
tags: [deadlock-prevention, lock-order, ai-suggestion-flip, transaction-design]
related-story: workflows/products/product.md Story 2-2
related-adr: workflows/living-docs/Ai-adr/002-payment-confirmation-side-effects-order.md
related-topology: workflows/topologys/Consistency-Design-Topology.md, workflows/topologys/tx-topology.md
---

## 1. 문제 (What)

결제 확정(`PaymentConfirmationService.confirm`) 단일 트랜잭션 안에서 5개 BC(Payment / Order / Point / Cart / Inventory)를 건드려야 함. 어떤 순서로 부를지 AI에 처음 물어봤을 때 AI가 제시한 순서:

```
Cart 비우기 → Point 사용/적립 → Order 완료 → Payment 마킹
```

근거: "사용자 입장에서 *결과가 보이는* 순서로 정렬, Cart 갱신이 가장 빠르게 반영되어야 UX 좋음."

다만 우리 팀 컨벤션(`consistency.md §5`)의 락 순서는:

```
Order → Payment → Point → Inventory
```

처리 순서와 락 순서가 다르면, 동일 자원에 다른 흐름이 접근할 때 락 cycle이 생길 가능성 — **데드락 위험**. 이미 컨벤션으로 박힌 락 순서가 있는데 AI 제안이 *그것과 반대 방향*이었음.

## 2. 문제 해결 방법 (How)

1. **컨벤션 재확인** — `consistency.md §5` "데드락 방지 ① 락 획득 순서 통일: Order → Payment → Point → Inventory. 위반 코드는 PR 차단."
2. **AI에게 락 순서를 명시하고 재질문** — "처리 순서를 락 순서와 일치시켜야 한다는 게 표준 패턴 아닌가?"
3. **AI 응답 확인** — "맞음. 다른 트랜잭션도 같은 순서로 락을 잡으면 cycle이 안 생김." → AI가 그제서야 *처리 순서 = 락 순서* 원칙을 재확인.
4. **처리 순서 재정렬**:
   - Payment 조회 + 소유권 검증 (락 X)
   - PortOne 재조회 (TX 시작 전, 외부 호출)
   - **Order 완료** (Order 비관적 락)
   - **Payment markCompleted** (자기 update)
   - **Point 사용/적립** (User 비관적 락)
   - **Cart 비우기** (락 영향 적음, 마지막)
5. **Inventory 호출은 confirm 단계에서 제거** (별도 트러블슈팅 참고).
6. **PortOne 재조회는 TX 시작 *전*에** — `consistency.md §6` "외부 호출은 트랜잭션 밖". 락 hold time이 외부 latency에 종속되지 않음.

## 3. 방식 (Why this way)

### 처리 순서 = 락 순서 원칙
- 데드락 cycle은 "두 트랜잭션이 같은 자원들을 *다른 순서*로 잠글 때" 발생.
- 모든 흐름이 *동일한 순서*로 잠그면 cycle 자체가 형성 불가능.
- 컨벤션이 락 순서를 박아놨다면, 처리 순서도 그대로 가야 한다 — *순서가 다른 이유*를 만들 필요가 없음.

### Cart 비우기를 가장 뒤로
- AI는 "UX 우선"이라는 이유로 가장 앞에 뒀음. 그러나:
  - 단일 TX 내부에선 순서가 *외부 가시성*과 무관 (커밋 전까지 외부에 안 보임)
  - Cart 비우기가 먼저 실행되면 *나중 단계 실패 시 정확히 같이 롤백*. 결과는 동일.
  - 락 순서와의 일관성이 *훨씬* 큰 가치.

### PortOne 재조회를 TX 밖으로
- TX 안에서 외부 호출하면 DB 락이 외부 latency만큼 hold → 동시성 처참.
- 락 hold time을 최소화하는 게 동시성 설계의 기본.

## 4. 결과 (Outcome)

- 코드 반영: `PaymentConfirmationService.confirm` 내부 호출 순서가 락 순서와 1:1 일치
- 동시 결제 확정 부하 테스트(2개 결제 동시) 시 데드락 0건 발생 — 락 cycle 형성 불가능
- 통합 테스트 추가: 단일 TX 보장 (`PaymentConfirmationServiceIntegrationTest`)
- AI-ADR-002에 "내가 수정한 부분"으로 회고 반영

## 5. 좋아진 점 (What got better)

- **"AI가 첫 답으로 준 순서를 의심하는 습관"이 정착.** 특히 동시성/순서/락이 얽힌 영역에서 AI는 *UX 직관*이나 *코드 가독성*을 기준으로 답하기 쉬움. 우리 컨벤션이 박혀있는 영역은 *명시적으로 컨벤션을 프롬프트에 넣어줘야* 안전한 답이 나옴.
- **"락 순서 = 처리 순서 일치" 원칙을 팀 컨벤션에 명시 추가** (`consistency.md §5`에 이미 있었지만, 처리 순서 일치는 묵시적이었음). 이후 환불 트랜잭션(`RefundTxOp`)도 같은 원칙으로 자동 정렬됨.
- **외부 호출 위치 결정이 *반사적*이 됨.** "외부 호출은 TX 밖" — 결제 확정·환불·웹훅 모두 동일 원칙으로 일관. 코드 리뷰 시간이 줄어듦.
- 같은 함정(AI가 락 순서 무시한 처리 순서를 제안)을 환불 트랜잭션 설계 때 *반복 발견* — 그때는 즉시 잡고 넘어감. 한 번 회고가 다음 회고의 비용을 0으로 줄임.
