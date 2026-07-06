# [Product · Done] BC 통합 현황판 — mock → real 전환 로드맵

## Product Vision
> c1oud-mall의 각 BC 간 협력이 mock 상태인지 real 상태인지 한눈에 파악할 수 있는 현황판을 만들어, mock → real 전환의 우선순위·의존관계·잔여 작업량을 명시한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Payment BC 내부에 4개 mock (`MockOrderService`, `MockCartService`, `MockPointService`, `MockInventoryService`)
  - Order BC 내부에 2개 mock (`OMockCartService`, `OMockPaymentService`)
  - mock의 존재 위치·전환 우선순위가 코드 grep으로만 확인 가능
- 발생하는 문제
  - 결제 e2e가 어디까지 동작하고 어디부터 mock인지 문서화 부재
  - 각 mock의 전환 우선순위(P1/P2/P3)가 팀 내 암묵지
- 왜 지금 해결해야 했는가
  - Payment Product 진행 중 · Refund Product 시작 전에 통합 지도 필요
  - Cart/Payment/Order 팀의 협업 시 진실 소스 필요

## 목표 (To-Be)
- 전체 BC 현황 요약표 (API 존재 · 도메인 · 다른 BC 호출 방식)
- BC 간 의존 그래프 ASCII 다이어그램
- Payment BC · Order BC별 mock 인벤토리 (위치 · 전환 우선순위)
- API 엔드포인트 전체 현황 (완성/일부/미구현)
- mock → real 전환 로드맵 (P1/P2/P3)

## 설계 결정 (Design Decisions)
- **우선순위 3단계**: P1(결제 confirm 필수) · P2(Point/Product 재고) · P3(Cart 완성)
- **알려진 버그 인벤토리** 함께 관리 — 전환 전 수정 필요 항목
- **living-doc 성격** — 코드 변경 시 갱신 (2026-06-04 기준)

## 전체 아키텍처 (High-Level Architecture)

### BC 의존 그래프 (2026-06-04 시점)
```
Auth/User ──────────────────────────────▶ (Real)
                                              ↑
Order BC ──[UserService: Real]────────────────┘
         ──[OMockCartService: mock]──────── Cart BC (미연결)
         ──[OMockPaymentService: mock]────  Payment BC (미연결)

Payment BC ──[MockOrderService: mock]────── Order BC (미연결)
           ──[MockCartService: mock]──────── Cart BC (미연결)
           ──[MockPointService: mock]──────── Point BC (미구현)
           ──[MockInventoryService: mock]──── Product BC (미연결)
           ──[PortOnePaymentQueryPort: Real]── PortOne PG (연결됨)
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)
| Mock 잔존 | 실 서비스 결여 | 잠재 실패 |
| --- | --- | --- |
| `MockPointService` | Point BC 도메인만 있고 Service/Repository 없음 | 결제 확정 시 포인트 사용/적립이 로그로만 처리 → DB 반영 없음 |
| `MockInventoryService` | Product 재고 확정/복구 미연결 | 결제 실패 시 재고 복구 안 됨 |
| `MockCartService` | Cart 삭제 미연결 | 결제 완료 후 장바구니 잔존 |

## 롤아웃 / 마이그레이션 (Rollout)

### 전환 로드맵
```
🔴 P1 (payment confirm 동작 필수)
  ├── Order.changeStatus → BusinessException 전환
  ├── OrderService.completeOrder/cancelOrder 추가
  └── MockOrderService → 실 OrderService 교체    [✅ 완료 PR #30]

🟠 P2 (Point BC 구현 · Product 재고 연결)
  ├── Point 도메인·API 구현
  ├── MockPointService → 실 PointService
  └── MockInventoryService → 실 Product BC

🟡 P3 (Cart 완성 · Order → Payment 사전등록 연결)
  ├── Cart 조회·삭제·수량변경 API                 [✅ 완료 v3]
  ├── OMockCartService → 실 CartService          [✅ 완료]
  ├── MockCartService → 실 CartService (또는 제거)
  ├── Cart memberId 하드코딩 제거                 [✅ 완료]
  └── OrderFacade.createOrder §5 TODO 구현       [✅ 완료]
```

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 (2026-06-04 시점) |
| --- | --- | --- |
| Mock 개수 (Payment BC) | 0개 | 3개 잔존 (`MockCartService`, `MockPointService`, `MockInventoryService`) |
| Mock 개수 (Order BC) | 0개 | 0개 (해소 완료) |
| 결제 e2e 정상 동작 | 100% | 80% (Order 연결 완료, Point/Cart/Inventory mock) |
| 알려진 버그 | 0건 | 3건 (OrderFacade quantity 버그 등) |

## Scope
**In Scope**:
- 6개 BC 현황판 (API · 도메인 · 협력)
- Mock 인벤토리 · 전환 우선순위
- API 엔드포인트 전체 상태
- 알려진 버그 리스트

**Out of Scope**:
- 각 mock의 세부 구현 로직 (BC 도메인 지도 참조)
- 실 서비스 신설 세부 (별도 SDD)

## 대상 사용자
- **팀 리더**: 진행률 파악
- **개발자**: 자신이 담당한 BC의 mock 위치 · 전환 우선순위 파악
- **PR 리뷰어**: 새 통합 PR이 로드맵의 어느 우선순위에 속하는지 확인

## 연결된 Epic 목록 (완료)
- [x] Epic 1: 전체 BC 현황 요약표
- [x] Epic 2: BC 의존 그래프
- [x] Epic 3: Mock 인벤토리 · 우선순위
- [x] Epic 4: API 엔드포인트 상태표
- [x] Epic 5: mock → real 전환 로드맵
- [x] Epic 6: 알려진 버그 인벤토리

## 관련 문서
- 원본 문서: `workflows/bc-integration-status.md` (2026-06-04)
- 짝 문서: [`sdd-bc-domain-map.md`](./sdd-bc-domain-map.md)
- 후속 통합 리포트: [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md)
- 관련 브레인스토밍: `workflows/task/fix/brainstorming/version/0.0.1v/{payment,refund,order,infra}.md`

## 열린 질문 (Open Questions)
- Cart 삭제 위치 결정 (OrderFacade.createOrder vs PaymentConfirmationService) — [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md)에서 결론
- Point BC 신설의 소유자 · 일정
- Inventory 책임 위치 (Product BC vs 별도 Inventory BC)

## 제품 수준 완료 기준 (Product-level DoD)
- [x] 6개 BC 현황판 완성
- [x] Mock 인벤토리 · 우선순위 표 존재
- [x] 로드맵 명시
- [x] Cart · Order 팀과 팀원 간 공유 완료
