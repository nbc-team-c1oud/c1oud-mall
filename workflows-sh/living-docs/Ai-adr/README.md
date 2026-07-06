# AI-ADR (`workflows/product.md` 트랙)

> AI(Claude)와 함께 진행한 설계 결정의 회고 기록.
> 일반 ADR(`src/main/java/.../docs/adr/`)은 *최종 결정*만 담고,
> 이 폴더는 **결정에 도달하기까지의 협업 과정**(프롬프트·AI 응답·내가 수정한 부분·반영 여부)을 남긴다.

## 양식

```markdown
# AI-ADR-XXX: <제목>

- 상태: Accepted / Proposed / Superseded
- 일자: YYYY-MM-DD
- 관련 Story: workflows/product.md §...
- 관련 ADR: src/main/java/.../docs/adr/000X-...

## Context
무엇이 문제였고, 어떤 제약이 있었나.

## Decisions
어떤 선택을 했나. 1줄 결론 + 핵심 항목.

## 프롬프트 내용 (AI 활용시)
실제로 AI에게 던진 질문·맥락·요구사항.

## AI 응답 요약
AI가 제시한 옵션·근거·트레이드오프.

## Consequences
### 장점
### 단점

## 내가 수정한 부분
AI 제안을 그대로 받아들이지 않고 손본 부분.

## 최종 반영 여부
- 코드 반영 위치 / 커밋 / 일반 ADR 작성 여부
```

## `products/product.md` 트랙 — 결제

| # | 토픽 | 관련 Story | 관련 일반 ADR |
|---|---|---|---|
| 001 | `portonePaymentId` UUID v4 채번 | Story 1-1 | ADR-0001 |
| 002 | 결제 확정 부수효과 처리 순서 (Order → Point → Cart) | Story 2-2 | ADR-0003 |
| 003 | PortOne 보상 트랜잭션 패턴 (DB 커밋 후 외부 호출) | Story 2-3 | ADR-0004 |
| 004 | 결제 확정 7단계 검증 순서 | Story 2-2 | ADR-0003 부속 |
| 005 | 웹훅 멱등성 — INSERT-first WebhookEvent (A/B/C 비교) | Story 3-3 | ADR-0007 |
| 006 | BC 간 협력 — Mock 구체 클래스 + 사후 의존성 재정비 | Story 2-2 | ADR-0003 |

## `products/product02.md` 트랙 — 환불

| # | 토픽 | 관련 Story | 관련 일반 ADR |
|---|---|---|---|
| 007 | Refund 신규 컨텍스트 분리 + `refund.domain`의 `payment.domain` 비의존 | Story 1-1 | — |
| 008 | 환불 단위 = orderItemId + 수량 (서버 금액 자동 산정) | Story 1-1/1-2/2-3 | — |
| 009 | 복합결제 비율 분리 — PG `floor` + 포인트 잔액 흡수 | Story 1-2 | ADR-0008 |
| 010 | 환불 트랜잭션 — 선검증 → 비관적 락 + 재검증 → DB 커밋 → TX 밖 PG 취소 | Story 2-2 | — |
| 011 | `PortOnePaymentCancelPort` 확장 (포트 공유 + 부분취소 + 멱등키, PG 실패 202) | Story 2-1/2-3 | — |

## 작성 원칙

- **결정한 것뿐 아니라 *기각한 것*도 같이** — Alternatives는 다음에 같은 토픽이 다시 올라올 때 시간 절약
- **프롬프트는 의도가 보일 정도만** — 토큰 그대로 복붙하지 않고, "AI에게 어떤 맥락을 줬고 무엇을 물었는가"가 보이게
- **AI 응답 요약은 옵션과 근거** — 그대로 받아들였는지 / 무엇을 수정했는지가 다음 칸의 핵심
- **"내가 수정한 부분"이 비어있는 ADR은 의심** — AI 제안을 비판 없이 통과시켰다는 신호. 회고 시 점검 대상
