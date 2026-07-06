# [0.0.3v · Issue 13] 관리자 대시보드 API + 관측성 (선택 Grafana)

> **역할**: 백오피스 정체성의 핵심 · 시연 화면. 앞서 구현한 도메인들을 대시보드 API로 통합.
> **Week**: 3 · **Day**: 20
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: (직접 매핑 없음 · 백오피스 프레이밍의 시연 정체성)

---

## 배경

관리자 백오피스의 시연 무대. README 스크린샷 · 채용 리뷰어에게 "이 프로젝트가 뭘 관리하는지" 즉시 보여주는 화면. Grafana 대시보드는 선택 (시간 여유 시).

## 핵심 스코프

### API 통합 (필수)

- `GET /api/v1/admin/dashboard/overview` — 통합 카드 6개
  - 오늘 매출 · 총 주문 수 · 활성 타임세일 이벤트 수 · 대기중 CS 문의 수 · 이번 주 발급 쿠폰 · 인기 검색어 Top 5
- `GET /api/v1/admin/dashboard/popular-search?scope=daily|weekly|all-time` — 이슈 05 재사용
- `GET /api/v1/admin/dashboard/cs-stats` — CS 상태별 통계 (WAITING·IN_PROGRESS·COMPLETED)
- `GET /api/v1/admin/dashboard/timesale-stats?eventId=` — 이벤트 판매 지표
- `GET /api/v1/admin/dashboard/coupon-usage` — 쿠폰 발급/사용률
- `GET /api/v1/admin/dashboard/low-stock?threshold=` — 재고 임박 상품

### Grafana (선택 · 시간 여유 시)

- docker-compose에 Prometheus + Grafana 추가
- Spring Boot Actuator `/actuator/prometheus` 노출
- Micrometer 지표 등록 (`chat.messages.total` · `timesale.purchase.total` · `coupon.issue.total` · `search.hit.total{cache=hit|miss}`)
- Grafana 대시보드 스크린샷 → README 삽입

## 산출물

- BE-13-1: `AdminDashboardController` (6개 조회 엔드포인트)
- BE-13-2: 도메인별 통계 Query (JPA·QueryDSL 조합)
- BE-13-3: Overview 응답 DTO (통합 카드용)
- BE-13-4: (선택) Micrometer 커스텀 지표 등록 (`MeterRegistry` 주입)
- BE-13-5: (선택) `docker-compose.yml` Prometheus + Grafana 서비스 추가
- BE-13-6: (선택) Grafana Dashboard JSON export (Git 저장)
- BE-13-7: README 대시보드 섹션 (스크린샷 or Postman collection)

## 관련 이슈 / 문서

- 선행: [05 인기 검색어](./issue-05-popular-search-redis-zset.md) · [06 타임세일](./issue-06-timesale-event-domain.md) · [08 쿠폰](./issue-08-first-come-coupon-issuance.md) · [09 CS 채팅](./issue-09-cs-chat-stomp-state-machine.md)
- 다음: [14 ADR·README](./issue-14-adr-readme-documentation.md)
- 규범: `.claude/rules/dto.md`

## 상세 (착수 시 채움)

_pending_
