# `workflows/review/state/` — 운영 회고 트랙

> Product 트랙(`workflows/products/`)을 진행하면서 **실제로 막혔거나, AI 제안을 뒤집었거나, 우회를 거쳐 정답에 도달한** 사례들의 회고.
> `road/` 트랙(`workflows/review/road/`)이 *주제별 분류*라면, `state/`는 *시간 순서 + 5섹션 가벼운 운영 회고*.

## 양식 (5섹션)

```markdown
# [트러블슈팅] <문제 한 줄 요약>

---
date: YYYY-MM-DD
domain: [payment / refund / cart / infra / ...]
tags: [구체 태그]
related-story: workflows/products/product*.md §...
related-adr: workflows/living-docs/Ai-adr/XXX-...md (있는 경우)
related-topology: workflows/topologys/...-Topology.md (있는 경우)
---

## 1. 문제 (What)
무엇이 막혔나, 어떤 현상이었나. 관찰 사실만, 해석 금지.

## 2. 문제 해결 방법 (How — Resolution)
실제로 어떻게 풀었나. 1단계 → 2단계 → ...

## 3. 방식 (Why this way)
왜 이 방식인가. 기각된 대안 + 채택 근거.

## 4. 결과 (Outcome)
결과적으로 코드/시스템이 어떤 상태가 됐나. 측정 가능하게.

## 5. 좋아진 점 (What got better)
이 트러블슈팅 덕분에 무엇이 더 나아졌나. 단순 fix를 넘어선 가치.
```

## 인덱스

| 날짜 | 도메인 | 토픽 |
|---|---|---|
| 2026-06-09 | cart, fe-integration | [`cartItemId` vs `productId` 혼동 — CT003 디버깅](2026-06-09-cart-item-id-vs-product-id-confusion.md) |
| 2026-06-09 | payment, concurrency | [결제 확정 처리 순서 vs 락 순서 어긋남 — AI 제안 뒤집기](2026-06-09-payment-confirmation-order-vs-lock-order.md) |
| 2026-06-09 | payment, transaction | [self-injection 함정 — 보상 트랜잭션을 별도 컴포넌트로](2026-06-09-payment-compensation-self-injection-trap.md) |
| 2026-06-09 | payment, refund, concurrency | [`MockInventoryService` 제거 + 데드락 cycle 차단 (productId 정렬)](2026-06-09-mock-inventory-removal-and-productid-sort.md) |
| 2026-06-09 | refund | [복합결제 환불 분배 — Round half up의 합계 위반 함정](2026-06-09-refund-split-floor-vs-roundhalfup.md) |
| 2026-06-09 | refund, idempotency | [환불 race condition — 사전검증만으로 안 됨, S+ 이중 검증 도입](2026-06-09-refund-pre-and-post-validation-s-plus.md) |
| 2026-06-09 | infra, security | [운영 배포 직전 CORS 하드코딩 + JWT 시크릿 dev 기본값 발견](2026-06-09-cors-hardcoded-and-jwt-secret-trap.md) |

## 작성 원칙

- **사실 → 해석 → 가치 순서**. 1·2섹션은 사실, 3·4섹션은 결정 근거, 5섹션은 단순 fix를 넘어선 가치.
- **AI 제안을 그대로 받은 사례는 회고가 아니다.** 뒤집거나 보완하거나 우회한 사례만 트러블슈팅으로 가치.
- **"내가 어디서 막혔는가"가 가장 중요.** 결정만 나열하지 말고 *처음 어떤 가설로 갔다가 어떻게 깨졌는지*를 솔직하게.
- **다음에 같은 함정 안 밟을 신호**가 5섹션에 있어야 함.
