# [0.0.3v · Issue 12] 인덱싱 최적화 — EXPLAIN Before/After · 복합·커버링 인덱스

> **역할**: 캠프 요구사항의 도전 선택 (인덱싱). 완주 확률 최상 · 이력서 파괴력 큼.
> **Week**: 3 · **Day**: 18~19
> **상태**: pending
> **Tier**: `sdd-lite`
> **캠프 요구사항 매핑**: 도전 (선택1) — 인덱싱 최적화 · EXPLAIN Before/After · 복합 인덱스

---

## 배경

캠프 요구사항 명시:
- 자주 호출되거나 데이터 많아질수록 느려질 쿼리 1개 이상 선정
- `EXPLAIN` 실행 계획으로 `type · key · rows · Extra` 확인
- Full Table Scan (`type=ALL`, `key=NULL`) 병목 식별
- `Extra`: `Using filesort` · `Using temporary` 최적화 대상
- 단일 or 복합 인덱스 설계 (Leftmost Prefix Rule)
- DDL 쿼리 README 명시
- Before/After `EXPLAIN` 비교 · 정량 분석
- 5만+ 데이터 위에서 실효 검증

## 핵심 스코프

### Day 18 — Before 분석
- 후보 쿼리:
  1. `SELECT * FROM product WHERE name LIKE '%맥북%' ORDER BY created_at DESC` — 검색 v1
  2. `SELECT * FROM order_item WHERE user_id = ? ORDER BY created_at DESC` — 마이페이지 주문 이력
  3. `SELECT * FROM chat_room WHERE customer_user_id = ? AND status = ?` — 내 CS 조회
  4. `SELECT * FROM point_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 20` — 포인트 이력
- 각 쿼리 EXPLAIN 실행 · type/key/rows/Extra 표 정리
- 병목 지점 식별 (Full Table Scan · Using filesort · Using temporary)

### Day 19 — 인덱스 적용 + After 리포트
- 복합 인덱스 설계 (Leftmost Prefix):
  - `product (status, created_at DESC)` — 검색 정렬용
  - `order_item (user_id, created_at DESC)` — 마이페이지
  - `chat_room (customer_user_id, status, last_message_at DESC)` — CS 필터
  - `point_history (user_id, created_at DESC)` — 포인트 이력
- 커버링 인덱스 검토 (Secondary Index Leaf에 필요한 컬럼 포함 시 `Extra: Using index`)
- DDL 명시 (README):
  ```sql
  CREATE INDEX idx_product_status_created ON product (status, created_at DESC);
  CREATE INDEX idx_order_item_user_created ON order_item (user_id, created_at DESC);
  ...
  ```
- After EXPLAIN 재실행 · Before/After 비교표

### 리포트 산출

`docs/perf/indexing-explain-report.md`:
- 쿼리별 Before/After EXPLAIN 표
- `type` 변화 (ALL → range/ref)
- `rows` 예측치 감소
- `Extra` 변화 (Using filesort 제거 · Using index 획득)
- 실측 응답 시간 개선 (k6와 결합)

## 카디널리티 · 인덱스 순서 원칙

- 복합 인덱스에서 카디널리티 높은 컬럼을 앞쪽에 배치 (요구사항 언급)
- WHERE = 조건이 `ORDER BY`보다 앞
- INSERT/UPDATE 성능 영향 고려 (인덱스 남용 방지)

## 산출물

- BE-12-1: 병목 쿼리 4개 이상 선정 · EXPLAIN Before 캡처
- BE-12-2: DDL 스크립트 (`resources/db/index/V*.sql` or 직접 SQL)
- BE-12-3: 인덱스 적용 · Before/After 응답 시간 실측
- BE-12-4: 커버링 인덱스 최소 1건 (Extra: Using index)
- BE-12-5: 리포트 문서 (`docs/perf/indexing-explain-report.md`)
- BE-12-6: README 인덱스 섹션 (DDL 명시)

## 학습 포인트 (README 언급)

- B-Tree 구조 · O(log n) 탐색
- 인덱스 스캔 종류 (const/eq_ref/ref/range/index/ALL)
- 커버링 인덱스가 왜 빠른가 (테이블 접근 없음)
- 옵티마이저의 인덱스 선택 기준 (20~25% 이상 → Full Table Scan 선호)

## 관련 이슈 / 문서

- 선행: [02 더미 시드](./issue-02-dummy-seed-50k-datafaker.md) — 5만+ 데이터에서만 실효
- 선행: [11 k6 부하테스트](./issue-11-load-test-k6-report.md) — 병목 발견 원천
- 다음: [14 ADR·README](./issue-14-adr-readme-documentation.md) — 리포트 통합

## 상세 (착수 시 채움)

_pending_
