# [0.0.3v · Issue 05] 인기 검색어 — Redis Sorted Set (ZINCRBY / ZREVRANGE)

> **역할**: 캠프 요구사항의 인기 검색어 조회 API. 관리자 대시보드에도 표시.
> **Week**: 1 · **Day**: 6
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 필수 — 인기 검색어 (Redis ZSet · 검색어별 점수 · 상위 N개 조회)

---

## 배경

캠프 요구사항 명시:
- 인기 검색어 조회 API
- Redis **Sorted Set(ZSet)** 활용 (`ZINCRBY` 점수 증가 · `ZREVRANGE` 랭킹 조회)
- 집계 기간: 실시간 vs 일별 vs 주간 (결정 필요)
- 동일 사용자 중복 검색 카운팅 방지 전략

## 핵심 스코프

- Redis Lettuce (기존 캐시용 인프라 재사용)
- 검색어 발생 시점: `ProductSearchService.search()` 진입 시 `ZINCRBY search:popular:{date} {keyword} 1`
- 조회 API: `GET /api/v1/search/popular?limit=10&scope=daily|weekly|all-time`
- 집계 기간 정책 (결정 초안):
  - 일별: `search:popular:daily:2026-07-15` · TTL 24h
  - 주간: `search:popular:weekly:2026-W29` · TTL 7일
  - 실시간(최근 1시간): `search:popular:hourly:2026-07-15-14` · TTL 1h + 최근 N개 합산
- 중복 카운팅 방지: `search:dedup:{userId}:{keyword}` SET NX TTL 60초 (분당 1회만 카운트)

## 관리자 대시보드 표시

- `AdminController`에 `/api/v1/admin/dashboard/popular-search`
- 실시간·일별·주간 3구획 카드로 표시 (이슈 13에서 통합)

## API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/search/popular?limit=10&scope=daily` | 공개 | 인기 검색어 상위 N |
| GET | `/api/v1/admin/dashboard/popular-search?scope=all` | ADMIN | 대시보드용 3구획 |

## 산출물

- BE-05-1: Redis Lettuce 설정 확인 · `RedisTemplate<String, String>` 등록
- BE-05-2: `PopularSearchRepository` (ZINCRBY · ZREVRANGE 캡슐화)
- BE-05-3: `PopularSearchService.recordSearch(userId, keyword)` · 중복 방지 로직
- BE-05-4: `PopularSearchService.getTop(scope, limit)` 조회
- BE-05-5: 검색 v1/v2 진입점에 `recordSearch` 훅 (트랜잭션 밖 · 비동기 고려)
- BE-05-6: `SearchPopularController` (공개 조회)
- BE-05-7: 관리자 대시보드 엔드포인트 (이슈 13과 통합)
- BE-05-8: TTL 만료 스케줄러 (Redis 자체 TTL로 자연 만료)

## 관련 이슈 / 문서

- 선행: [03 검색 v1](./issue-03-search-v1-querydsl-like-cursor.md) · [04 검색 v2](./issue-04-search-v2-caffeine-local-cache.md)
- 다음: [13 관리자 대시보드](./issue-13-admin-dashboard-observability.md)
- 규범: `.claude/rules/persistence.md`

## 상세 (착수 시 채움)

_pending_
