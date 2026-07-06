# [Product · Done] BC 도메인 지도 — c1oud-mall 도메인 규범 정립

## Product Vision
> c1oud-mall의 6개 BC(Auth/User · Product · Cart · Order · Payment · Point)의 역할·엔티티·비즈니스 규칙·API·상태 머신을 코드 기반으로 문서화하여, 신규 팀원·리뷰어·향후 SDD 작성자가 참조하는 단일 지도(single map)로 확립한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - 각 BC의 도메인 지식이 코드에 흩어져 있어 신규 팀원이 진입 시 파악에 시간 소요
  - BC 간 협력 규칙(어느 BC가 어느 BC를 어떻게 호출하는지)이 리뷰 대화에만 남음
- 발생하는 문제
  - PR 리뷰 시 도메인 규칙 확인이 코드 grep에 의존
  - 신규 SDD 작성 시 "지금 코드 상태"를 매번 재조사
- 왜 지금 해결해야 했는가
  - 결제·환불 도메인 진입 전에 BC 경계 명확화 필요
  - 향후 자동화된 리뷰(ADR·SDD 크로스 참조)의 기초

## 목표 (To-Be)
- 6개 BC 각각의 엔티티 · 상태 머신 · 비즈니스 규칙 · API · 현재 상태를 표로 정리
- ErrorCode를 각 규칙에 매핑
- BC 간 협력 관계(누가 누구를 호출하는지)를 명시
- Payment BC의 결제 확정 흐름·보상 규칙을 단일 진실로 문서화

## 설계 결정 (Design Decisions)
- **엔티티 필드 표 형식** — 각 BC마다 엔티티당 필드 표 (필드명·타입·설명)
- **비즈니스 규칙 표 형식** — 규칙·시행 위치·ErrorCode 3열
- **상태 머신은 ASCII** — Order · Payment · Product 각각의 상태 전이 다이어그램
- **미구현 BC도 포함** — Point BC는 도메인만 있고 API/Service 없음을 명시 (진실성 확보)

## 대안 검토 (Alternatives Considered)
- Option A (선택) — 단일 참조 문서 (living-doc 형태)
  - 보상: 한눈에 파악 가능
  - 비용: 코드 변경 시 갱신 필요
- Option B — 각 BC 패키지 내 README
  - 거부 이유: 흩어져서 BC 간 관계 파악 어려움
- Option C — 자동 생성 (코드 어노테이션 파싱)
  - 거부 이유: 초기 오버킬

## 전체 아키텍처 (High-Level Architecture)

### BC 간 협력 관계 (Final State)
```
Auth/User (Real) ◀── UserService.findById 참조
                     ▲
    ┌────────────────┼────────────────┐
Order ──▶ CartService (실)         Payment
   │                                   │
   ├─▶ ProductJpaRepository (실)       ├─▶ PortOnePaymentQueryPort (실)
   │                                   ├─▶ OrderService.completeOrder (실 · PR #30)
   ├─▶ PaymentInitiationService (실) ──┤
   │   (portonePaymentId 채번)         ├─▶ MockCartService (mock — 이관 예정)
   │                                   ├─▶ MockPointService (mock — Point BC 미구현)
   └─▶ CartService (직접 삭제)         └─▶ MockInventoryService (mock)

Product BC (Real · 재고 차감만 내부 처리)
Point BC (도메인만 · Service/Repository 미구현)
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)
> 이 문서 자체는 관측 대상이 아니지만, 각 BC의 규칙 위반 시 ErrorCode → HTTP 매핑을 정리:

| BC | 대표 ErrorCode | HTTP |
| --- | --- | --- |
| User | `U002` (INVALID_CREDENTIALS) | 401 |
| Product | `PRODUCT_NOT_FOUND` / `INSUFFICIENT_STOCK` | 404 / 409 |
| Cart | `CT003` (CART_EMPTY) / `CART_ACCESS_DENIED` | 400 / 403 |
| Order | `INVALID_ORDER_STATUS` (OD002) | 400 |
| Payment | `PAYMENT_AMOUNT_MISMATCH` (PM001) · `PORTONE_PAYMENT_NOT_PAID` | 400 · 400 |

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- 코드가 이미 안정된 시점에 문서화
- 코드 변경 시 문서 갱신은 PR 체크리스트에 포함

### Product 의존성
- 선행: 각 BC 도메인 초기 구현
- 후행: 향후 SDD 작성 시 참조 · [`sdd-bc-integration-status.md`](./sdd-bc-integration-status.md) (통합 현황판)

## 성공 지표 (KPI)
| 지표 | 목표 값 | 결과 |
| --- | --- | --- |
| 6개 BC 도메인 지식 커버리지 | 100% | ✅ Auth·Product·Cart·Order·Payment·Point 모두 문서화 |
| 각 BC의 상태 머신 명시 | 상태 전이 있는 BC 전부 | ✅ Order · Payment · Product |
| 알려진 문제 · 버그 목록 | 최신 상태 유지 | ⚠️ 갱신 시점 (2026-06-04 기준) |

## Scope
**In Scope**:
- 6개 BC 엔티티 · 상태 · 규칙 · API 표
- BC 간 협력 관계
- 알려진 문제 · 버그 인벤토리

**Out of Scope**:
- 자동 갱신 스크립트
- 각 BC 내부 아키텍처 상세 (레이어별 파일 트리)

## 대상 사용자
- **신규 팀원**: 코드 진입 전 5분 파악용
- **리뷰어**: 도메인 규칙 확인
- **SDD 작성자**: Product 문서 §5·§7 작성 시 참조

## 연결된 Epic 목록 (완료)
- [x] Epic 1: Auth/User BC 문서화
- [x] Epic 2: Product BC 문서화
- [x] Epic 3: Cart BC 문서화
- [x] Epic 4: Order BC 문서화
- [x] Epic 5: Payment BC 문서화
- [x] Epic 6: Point BC (미구현 상태 명시)

## 관련 문서
- 원본 문서 (아카이브): `workflows/bc-domain-guide.md` (2026-06-04 갱신)
- 짝 문서: [`sdd-bc-integration-status.md`](./sdd-bc-integration-status.md) — mock → real 전환 로드맵
- CLAUDE.md · `.claude/rules/architecture.md` · `.claude/rules/exception.md`
- Topology 참조: `workflows/topologys/Context-Dependency-Topology.md`

## 열린 질문 (Open Questions)
- 코드 변경 시 이 문서 자동 갱신 트리거 (CI 훅)?
- Point BC 실제 구현 시 어떤 SDD Product에서 이 문서를 갱신하는가?
- Payment · Refund 도메인이 안정화되면 이 문서를 재구조화할 시점?

## 제품 수준 완료 기준 (Product-level DoD)
- [x] 6개 BC 모두 문서화 (엔티티 · 규칙 · 상태 · API)
- [x] BC 간 협력 관계 명시
- [x] 알려진 문제 · 버그 표 존재
- [x] ErrorCode → HTTP 매핑 문서화
- [x] living-doc으로 living-docs/ 또는 topologys/ 이관 검토 완료 (현재 root에 유지)
