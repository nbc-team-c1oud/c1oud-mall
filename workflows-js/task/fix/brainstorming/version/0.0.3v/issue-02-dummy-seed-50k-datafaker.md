# [0.0.3v · Issue 02] Datafaker + JDBC Batch Insert 5만+ 더미 시딩

> **역할**: 부하테스트·인덱싱 리포트의 baseline 데이터를 확보. 소량으로는 인덱스·캐시 효과 실측이 안 됨.
> **Week**: 1 · **Day**: 3
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 도전 — 5만건 이상 대용량 더미 데이터 (11 부하테스트 · 12 인덱싱의 전제)

---

## 배경

기존 `DummyDataInit`는 상품 32건만 초기화 → 부하테스트·인덱스 실측에 부족. Datafaker로 실 유사 데이터를 생성 + JDBC `batchUpdate()`로 대량 삽입 (JPA 1건씩 대비 10~100배 빠름).

## 핵심 스코프

- 상품 **50,000건+** · 유저 **10,000건+** · 주문 **100,000건+** · 검색 로그 (인기 검색어용) **50,000건+**
- Datafaker (`net.datafaker:datafaker`) 상품명·설명·가격·카테고리 랜덤
- `JdbcTemplate.batchUpdate()` 배치 삽입 (1,000건 커밋 단위)
- `@Profile("dev-large")` 조건부 실행 (prod에서 실수 실행 방지)
- `@Transactional` 범위 조절로 OutOfMemoryError 방지 (flush + clear 주기)

## 왜 5만+인가

- **인덱스 효과**: 옵티마이저가 인덱스 vs Full Table Scan 선택하는 임계가 데이터 20~25%. 5만 이상에서만 정량 비교 가능
- **캐시 히트율**: 소량은 캐시 히트가 100%에 가까워 v1/v2 비교 무의미
- **k6 부하**: TPS·Saturation Point가 데이터 볼륨에 따라 다름

## 산출물

- BE-02-1: `SeedProperties` (조건부 활성화 · 볼륨 설정)
- BE-02-2: `ProductSeeder` · `UserSeeder` · `OrderSeeder` · `SearchLogSeeder` 클래스
- BE-02-3: `JdbcBatchInserter` 유틸 (배치 크기 · 커밋 단위)
- BE-02-4: `@ConditionalOnProperty(name = "seed.enabled")` 스위치
- BE-02-5: 실행 시간 로깅 (5만건 삽입 X초 리포트)

## 관련 이슈 / 문서

- 선행: [01 Admin 컨텍스트](./issue-01-admin-context-and-role.md)
- 다음: [11 k6 부하테스트](./issue-11-load-test-k6-report.md) — baseline 데이터 대상
- 다음: [12 인덱싱](./issue-12-indexing-explain-report.md) — 인덱스 실측 대상
- 규범: `.claude/rules/persistence.md`
- 회의록: 10차 세션

## 상세 (착수 시 채움)

_pending_
