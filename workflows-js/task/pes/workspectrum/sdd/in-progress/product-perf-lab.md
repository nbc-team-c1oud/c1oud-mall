# [Product 5] 성능 실험실 · 5만+ 시딩 · k6 부하 · 인덱싱 EXPLAIN

## Product Vision
> 5만+ 상품·유저·주문 더미 데이터 위에서 v1/v2 검색과 타임세일·쿠폰 락 3전략을 k6로 부하테스트하고, EXPLAIN Before/After 리포트로 인덱싱 개선 효과를 정량 증명해 **"이 백엔드가 실제로 굴러간다"** 는 이력서 파괴력을 확보한다.

## 배경 및 문제

- 현재 상황 (As-Is)
  - `DummyDataInit`가 상품 32건만 시딩 (M1 · `532d653` 커밋)
  - 부하테스트 도구 미도입 · 성능 실측 부재
  - 인덱스 설계는 도메인별 산발 · EXPLAIN 실행 계획 검증 부재
- 발생하는 문제
  - 소량 데이터로는 캐시·인덱스 효과 실측 불가능 (옵티마이저가 Full Table Scan 선택 임계 20~25%)
  - 캠프 요구사항 도전: 5만+ 더미 · k6 부하 · v1/v2 리포트 · 인덱싱 EXPLAIN Before/After · DDL README 명시
  - 리뷰어가 GitHub 열었을 때 부하 리포트·EXPLAIN 표 없으면 백엔드 파괴력 얕음
- 왜 지금 해결해야 하는가
  - Week 1 이슈 02 (시딩) = 이후 모든 성능 실측의 원천 데이터
  - Week 3 이슈 11·12 (k6 · 인덱싱) = 이력서 파괴력 자산
  - 캠프 도전 3개 중 하나 (인덱싱) 필수 선택

## 목표 (To-Be)

- `SeedProperties` + Datafaker + JDBC `batchUpdate()` 조합으로 5만+ 상품 · 1만+ 유저 · 10만+ 주문 시드
- `@Profile("dev-large")` 조건부 실행 (prod 실수 방지)
- k6 스크립트 (`tests/k6/search-v1-vs-v2.js` · `tests/k6/timesale-purchase.js` 등) 및 실행 스크립트
- 부하 리포트 (`docs/perf/search-v1-vs-v2-report.md` · `timesale-lock-benchmark.md` · `indexing-explain-report.md`)
- EXPLAIN Before/After 표 · 복합 인덱스 DDL · 커버링 인덱스 활용
- README §Performance 섹션 통합

## 설계 결정 (Design Decisions)

- **Datafaker + JDBC Batch Insert** — JPA 1건씩 대비 10~100배 빠름 · OOM 방지 위해 1000건 커밋 단위
- **`@Profile("dev-large")` 스위치** — prod 실수 실행 방지 · 로컬 부하테스트 전용
- **k6 선택** (Gatling/JMeter 대비) — JavaScript 스크립트 · 리포트 export 간편 · docker-compose 통합 편함
- **부하 시나리오: Ramp Up** — 갑작스러운 vUser 폭주 대신 점진 증가 → Saturation Point 관찰 자연스러움
- **인덱싱 도전 채택** (캠프 3개 중) — 완주 확률 최상 · 이력서 파괴력 큼 (EXPLAIN Before/After 표가 시각적으로 강함)
- **복합 인덱스 Leftmost Prefix Rule 준수** — 카디널리티 높은 컬럼 앞쪽 · WHERE > ORDER BY 순
- **커버링 인덱스 최소 1건** (`Extra: Using index` 획득) — 2차 인덱스 학습 스토리 소재

## 대안 검토 (Alternatives Considered)

### 시딩 방식

**Option A — JPA `save()` 반복**
- 거부: 5만 건 20분+ · 트랜잭션 관리 부담 · OOM 리스크

**Option B (선택) — JDBC `batchUpdate()`**
- 비용: SQL 직접 · JPA 캐시 우회 (dirty checking 이점 없음)
- 보상: 5만 건 30초 이내 · 안전

**Option C — MySQL Stored Procedure**
- 부분 채택 (백업 옵션): 가장 빠르지만 dev/prod 환경 이질 · Java Datafaker 랜덤 못 씀

### 부하 도구

**Option A (선택) — k6**
- 비용: JavaScript 러닝커브
- 보상: 리포트 export · InfluxDB/Grafana 통합 · docker-compose 부팅 쉬움

**Option B — Gatling**
- 부분 채택: Scala 부담 · 리포트 좋음 · 대안

**Option C — JMeter**
- 거부: GUI 도구 · CI 통합 부담

### 도전 선택 (캠프 3개 중)

**Option A (선택) — 인덱싱**
- 완주 확률 최상 · Before/After 정량 · 이력서 파괴력 큼

**Option B — Redis Pub/Sub 채팅 다중 서버**
- 거부: 채팅 도메인 스코프 오버 · 다중 인스턴스 배포 부담

**Option C — CI/CD (Docker + EC2 + SSM + Actions)**
- 부분 채택 (여유 시): 이미 배포 라인 일부 있음 · 추가 완성 부담 낮음

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
       [부하 발생기]              [백엔드]                    [DB]
docker-compose k6         Spring Boot         MySQL/H2
  ├─ k6 (부하 스크립트)  ─────HTTP────►  Controller ──► Service ──► Repository ──► SQL
  ├─ InfluxDB (지표)                                                              │
  └─ Grafana (시각화)                                                             │
                                                                                  ▼
                                                                          EXPLAIN 실행 계획
                                                                          (Before/After 비교)

Seed:
  Datafaker → JDBC Batch → INSERT (1000건 커밋)
  Products 50k · Users 10k · Orders 100k · SearchLog 50k
```

### 핵심 플로우

**1. 대용량 시딩 (Epic 1)**
```
@Profile("dev-large")
SeedRunner (ApplicationRunner):
  if seed.enabled:
    ProductSeeder.seed(50000)
      Datafaker → Product 랜덤 (name·description·price·categoryId·status)
      JdbcTemplate.batchUpdate("INSERT INTO product ... VALUES (?,?,?,?,?)", batch)
      매 1000건 flush + log
    UserSeeder.seed(10000)
    OrderSeeder.seed(100000)
    SearchLogSeeder.seed(50000)
    → 총 실행 시간 로그
```

**2. k6 부하 시나리오 (Epic 2)**
```
k6 run tests/k6/search-v1-vs-v2.js
  stages: [
    { duration: '30s', target: 20 },    // Ramp Up
    { duration: '1m',  target: 100 },
    { duration: '30s', target: 300 },   // Saturation 탐색
    { duration: '1m',  target: 300 },
    { duration: '30s', target: 0 },
  ]
  export → summary.json · InfluxDB (선택)

Report:
  P50/P95/P99 (v1 · v2)
  TPS 곡선 · Saturation Point
  Error Rate (4xx/5xx)
```

**3. EXPLAIN Before/After (Epic 3)**
```
Before:
  EXPLAIN SELECT * FROM product WHERE name LIKE '%맥북%' ORDER BY created_at DESC LIMIT 20;
  → type=ALL · key=NULL · rows=50000 · Extra=Using where; Using filesort

Index 추가:
  CREATE INDEX idx_product_status_created ON product (status, created_at DESC);
  (참고: LIKE '%...%'는 인덱스 활용 어려움 · 상황별 인덱스 설계)

After:
  EXPLAIN SELECT * FROM product WHERE status='SALE' ORDER BY created_at DESC LIMIT 20;
  → type=index · key=idx_product_status_created · rows=20 · Extra=Using index (커버링 조건)
```

### Out-of-Process 의존
- **MySQL/H2**: EXPLAIN 실행 (H2도 EXPLAIN 문법 지원 · MySQL 호환 모드)
- **k6**: 로컬 실행 or docker-compose
- **InfluxDB + Grafana** (선택): k6 지표 수집·시각화

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | 조치 |
| --- | --- |
| 시딩 중 OOM | `@Transactional` 범위 조절 · 1000건 커밋 · JPA 캐시 clear · 실패 시 로그 마커 `SEED_OOM` |
| k6 실행 중 백엔드 다운 | 부하 완화 후 재실행 · 로그 확인 (`error_rate > 5%` 시 중단) |
| EXPLAIN이 인덱스 사용 안 함 (옵티마이저 판단) | `FORCE INDEX` 힌트 실험 · Statistics 갱신 (`ANALYZE TABLE`) · 데이터 분포 문제 진단 |
| 커버링 인덱스가 예상대로 안 걸림 | SELECT 컬럼 vs 인덱스 컬럼 비교 · 인덱스 재설계 |

### 로깅 정책
- **시딩**: `SEED_START type={} count={}` · `SEED_PROGRESS type={} inserted={} elapsed_ms={}` · `SEED_COMPLETE type={} total_ms={}`
- **k6**: JSON summary export · InfluxDB write · logs 별도
- **EXPLAIN**: 리포트 문서에 캡처 (테이블 형태)

### 관측 지표 (백엔드 부하 중)
- `http.server.requests` (Spring Boot 기본) · P50/P95/P99
- `hikaricp.connections.usage` — DB 커넥션 포화 감지
- `jvm.memory.used` — GC 압박 감지
- `system.cpu.usage` — CPU 병목

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Week 1 Day 3 시딩 착수 (이슈 02) · Week 3 Day 17~19 부하·인덱싱

### Product 의존성
- 선행: 이슈 01 (Admin)
- 후행: 이슈 06/07/08 (락 3전략) 부하 시나리오 · 이슈 13 (대시보드 지표 관찰)
- 참조: product-search-cache (v1/v2) · product-timesale-concurrency (락 3전략)

### Epic·Story 의존성 그래프
```
Epic 1 (시딩) ──► Epic 2 (k6 부하) ──► Epic 3 (인덱싱 EXPLAIN)
                  │                       │
                  └─── (병렬 가능)  ◄──────┘
                       (부하 결과가 인덱싱 대상 쿼리 발견의 원천)
```

### 환경별 설정 분기
| 항목 | dev-large (H2) | prod (RDS · 미실행) |
| --- | --- | --- |
| 프로파일 | `dev-large` | (실행 안 함) |
| SeedProperties | enabled=true · counts 명시 | disabled |
| k6 | 로컬 실행 (localhost:8080) | (실행 안 함 · prod 부하 X) |
| EXPLAIN | H2 실행 (MODE=MySQL) | RDS 실행 (참고) |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 시딩 5만 상품 삽입 시간 | ≤ **60초** | SeedRunner 로그 |
| k6 v1 P95 (baseline) | (실측 · 참고값) | k6 summary |
| k6 v2 P95 (개선) | v1 대비 ≥ **50% 개선** | k6 summary |
| k6 Saturation Point | vUser N 명시 (실측) | TPS 곡선 꺾임 |
| EXPLAIN 인덱스 사용률 | ≥ **80%** (Before Full Scan → After index/ref) | 병목 쿼리 4개+ 중 |
| 커버링 인덱스 획득 | 최소 **1건** | `Extra: Using index` |
| Before/After 응답 시간 개선 | ≥ **70%** (병목 쿼리 기준) | 통합 테스트 지표 |

## Scope

**In Scope**:
- Datafaker + JDBC BatchInserter (Product · User · Order · SearchLog 5만+ 시딩)
- `@Profile("dev-large")` 스위치 + `SeedProperties`
- k6 스크립트 (검색 v1/v2 · 타임세일 구매 · 쿠폰 발급)
- 부하 리포트 (`docs/perf/search-v1-vs-v2-report.md` 등)
- EXPLAIN 병목 쿼리 4개+ 선정 · Before 캡처
- 복합 인덱스 · 커버링 인덱스 설계 · DDL 명시
- After EXPLAIN 리포트 · Before/After 비교표
- README §Performance · §Indexing 섹션

**Out of Scope**:
- Gatling/JMeter 부하 도구 — 사유: k6 채택
- Stored Procedure 시딩 — 사유: JDBC Batch로 충분
- MySQL FULLTEXT 인덱스 — 사유: 초기 스코프 · 필요 시 후속
- 프로덕션 부하테스트 — 사유: prod에서 실행 X · dev-large 로컬 only
- Grafana + Prometheus 정식 세팅 — 사유: 이슈 13/product-admin-backoffice에서 선택

## 대상 사용자
- **개발자/리뷰어** — 성능 리포트 · EXPLAIN 표가 이력서 파괴력 원천
- **관리자 (간접)** — 대시보드 응답 시간 개선 혜택
- **일반 사용자 (간접)** — 검색·구매·쿠폰 응답 시간 개선

## 연결된 Epic 목록
- [ ] Epic 1: Datafaker + JDBC Batch 시딩 (Product 5만 · User 1만 · Order 10만 · SearchLog 5만)
- [ ] Epic 2: k6 부하테스트 (v1/v2 · 락 3전략 · Ramp Up · Saturation)
- [ ] Epic 3: 인덱싱 EXPLAIN Before/After + 커버링 인덱스

## 관련 문서
- 대응 이슈:
  - [issue-02-dummy-seed-50k-datafaker](../../../fix/brainstorming/version/0.0.3v/issue-02-dummy-seed-50k-datafaker.md)
  - [issue-11-load-test-k6-report](../../../fix/brainstorming/version/0.0.3v/issue-11-load-test-k6-report.md)
  - [issue-12-indexing-explain-report](../../../fix/brainstorming/version/0.0.3v/issue-12-indexing-explain-report.md)
- 관련 ADR (발행 예정):
  - `ADR 016` — 인덱싱 전략 (복합·커버링·Leftmost Prefix)
- CLAUDE.md / `.claude/rules/*` 갱신:
  - `.claude/rules/persistence.md` (선택 · 커버링 인덱스 사례 추가)
- 관련 Product: `./product-search-cache.md` (v1/v2 검색 대상) · `./product-timesale-concurrency.md` (락 3전략 부하 대상) · `./product-admin-backoffice.md` (Micrometer 지표 연결)
- 관련 milestone: `../../../milestones/version/0.0.3v/milestone.md`

## 열린 질문 (Open Questions)
- **Q1**: k6 결과를 InfluxDB+Grafana에 저장할지 vs JSON export만? (초기 JSON · 여유 시 시각화)
- **Q2**: LIKE '%keyword%' 인덱스 활용 어려움 → 다른 쿼리를 인덱싱 대상으로? (예: 카테고리 필터 · 정렬 인덱스)
- **Q3**: `FORCE INDEX` 힌트를 쓰는 케이스 있을지? (옵티마이저 판단이 문제 있을 때만 · 남용 금지)
- **Q4**: 통계 갱신 `ANALYZE TABLE`이 EXPLAIN 결과 바꾸는지 실험? (배경 학습으로 리포트에 언급)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] Epic 1·2·3 완료
- [ ] ADR 016 발행
- [ ] 부하 리포트 3건 (`docs/perf/*.md`)
- [ ] EXPLAIN Before/After 표 (병목 쿼리 4개+)
- [ ] README §Performance · §Indexing 섹션
- [ ] DDL `CREATE INDEX ...` 스크립트 README 명시

---

# [Epic 1] Datafaker + JDBC Batch 시딩

## 목표
Product 5만 · User 1만 · Order 10만 · SearchLog 5만 시딩을 60초 이내에 완료할 수 있는 시딩 파이프라인을 완성한다.

## 배경
이후 모든 성능 실측의 원천 데이터.

## 포함 Story
- Story 1-1: `SeedProperties` + `@Profile("dev-large")` + Datafaker 의존성
- Story 1-2: `JdbcBatchInserter` 유틸 + `ProductSeeder`
- Story 1-3: `UserSeeder` + `OrderSeeder` + `SearchLogSeeder`
- Story 1-4: SeedRunner (ApplicationRunner) + 실행 시간 로깅

## Epic 인수 시나리오
- Given `dev-large` 프로파일 · `seed.enabled=true`
- When `./gradlew bootRun -Dspring.profiles.active=dev-large`
- Then 60초 이내 5만 상품 · 1만 유저 · 10만 주문 · 5만 검색로그 완료 · 로그 마커 `SEED_COMPLETE`

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 5만 상품 삽입 시간 ≤ 60초 (로그 검증)
- [ ] `dev` 프로파일에서 실행 안 됨 (`@Profile` 검증)

## [Story 1-1] SeedProperties + Datafaker

### User Story
- As a 개발자
- I want 시딩 활성화·볼륨 설정을 프로퍼티로 제어하고 Datafaker로 실 유사 랜덤 데이터를 생성해서
- so that 개발자별로 볼륨을 조정하고 프로덕션 실수 실행을 방지한다

### 설명
- 의존성: `net.datafaker:datafaker:2.x`
- `SeedProperties` (`@ConfigurationProperties("seed")`): `enabled` (boolean) · `productCount` · `userCount` · `orderCount` · `searchLogCount`
- `@Profile("dev-large")` — 활성 프로파일 이름 확정

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.seed.SeedProperties`
- Datafaker `Faker` 사용 (name · description · price · createdAt)

### 완료 기준 (AC)
- Given `spring.profiles.active=dev` / When 부팅 / Then SeedRunner 실행 안 됨
- Given `spring.profiles.active=dev-large · seed.enabled=false` / When 부팅 / Then SeedRunner 실행 안 됨
- Given `spring.profiles.active=dev-large · seed.enabled=true` / When 부팅 / Then SeedRunner 실행

### Definition of Done
- [ ] 구현: `SeedProperties` · `application-dev-large.yml`
- [ ] Datafaker 의존성 추가
- [ ] 단위 테스트: SeedProperties 바인딩

### 스토리 포인트
0.5d

### 의존성
- 후행: Story 1-2·1-3·1-4

## [Story 1-2] JdbcBatchInserter + ProductSeeder

목록:
- `JdbcBatchInserter` — `JdbcTemplate.batchUpdate(sql, List<Object[]>)` 래퍼 · 1000건 커밋 단위
- `ProductSeeder.seed(count)`:
  - 카테고리·상태 랜덤 분포
  - Datafaker `commerce().productName()` · `commerce().price()` · `lorem().paragraph()`
  - JDBC batch insert
  - 진행률 로그 (10% 단위)

**SP**: 1d

## [Story 1-3] User + Order + SearchLog Seeder

목록:
- `UserSeeder.seed(10000)` — 이메일·비밀번호(bcrypt · 성능 위해 고정) · Role.USER
- `OrderSeeder.seed(100000)` — 무작위 userId·productId 매칭 · 상태 분포 · createdAt 최근 90일 랜덤
- `SearchLogSeeder.seed(50000)` — 검색어 pool 100개에서 랜덤 · userId 매칭 · createdAt 랜덤 (이슈 05 인기 검색어 시딩용)

**SP**: 1d

## [Story 1-4] SeedRunner + 실행 시간 로깅

목록:
- `SeedRunner implements ApplicationRunner` · `@Profile("dev-large")`
- 각 Seeder 순차 실행 · 총 소요 시간 로그
- `SEED_START` · `SEED_PROGRESS` · `SEED_COMPLETE` 마커

**SP**: 0.5d

---

# [Epic 2] k6 부하테스트 (v1/v2 · 락 3전략 · Ramp Up · Saturation)

## 목표
k6 스크립트로 검색 v1/v2와 타임세일 락 3전략을 실측하고, Ramp Up · TPS · Saturation Point · P50/P95/P99를 리포트로 산출한다.

## 배경
캠프 도전 · 이력서 파괴력 큼. product-search-cache · product-timesale-concurrency 성능 검증.

## 포함 Story
- Story 2-1: k6 로컬 세팅 + docker-compose (선택)
- Story 2-2: `search-v1-vs-v2.js` 스크립트
- Story 2-3: `timesale-purchase.js` (락 3전략별 시나리오)
- Story 2-4: 리포트 문서 3건 + README §Performance

## Epic 인수 시나리오
- Given 5만 시딩 완료 · 백엔드 부팅 · k6 설치
- When `k6 run tests/k6/search-v1-vs-v2.js`
- Then Ramp Up 30초→100→300 vUser · P50/P95/P99 각 vUser 단계 기록 · Saturation Point 관찰

- Given 타임세일 이벤트 재고 10 · 락=OPTIMISTIC/PESSIMISTIC/REDIS
- When `k6 run tests/k6/timesale-purchase.js --env STRATEGY=redis`
- Then 성공 카운트 · 락 실패 카운트 · P95 응답 시간 기록

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 리포트 문서 3건 (`docs/perf/`)
- [ ] README §Performance 섹션

## [Story 2-1] k6 세팅

목록:
- 로컬 설치 (`brew install k6` or Windows chocolatey)
- (선택) `docker-compose.yml`에 `k6 · influxdb · grafana` 서비스 추가
- `tests/k6/` 폴더 구조 · 공통 유틸 (`lib/http.js`)

**SP**: 0.5d

## [Story 2-2] search-v1-vs-v2.js

목록:
- vUser stages: 30s→20 · 1m→100 · 30s→300 · 1m→300 · 30s→0
- 시나리오: 랜덤 키워드 → v1 GET → v2 GET (병렬) → sleep 1s
- Thresholds: `http_req_duration p(95)<500` · `http_req_failed rate<0.01`
- Export: JSON summary → `docs/perf/data/search-v1-vs-v2-YYYYMMDD.json`

**SP**: 1d

## [Story 2-3] timesale-purchase.js

목록:
- 시나리오: 재고 100 이벤트 · vUser 200 · 각 사용자 1회 구매
- 환경변수 STRATEGY로 락 스위칭
- 지표: 성공/실패 카운트 · P50/P95/P99 · lock 획득 실패율
- 3전략 각각 실행 · 비교 표

**SP**: 1d

## [Story 2-4] 리포트 문서 3건 + README

목록:
- `docs/perf/search-v1-vs-v2-report.md` — P95 개선률 · TPS · Saturation
- `docs/perf/timesale-lock-benchmark.md` — 3전략 비교 · 최종 선택 근거
- `docs/perf/README.md` — 통합 요약
- README §Performance — 그래프 스크린샷 · 요약 표

**SP**: 0.5d

---

# [Epic 3] 인덱싱 EXPLAIN Before/After + 커버링 인덱스

## 목표
병목 쿼리 4개+ 선정 · EXPLAIN Before 캡처 · 복합 인덱스 설계 · After 재실행 · 커버링 인덱스 최소 1건 획득 · Before/After 리포트.

## 배경
캠프 도전 (선택1) · 완주 확률 최상 · 이력서 파괴력 큼.

## 포함 Story
- Story 3-1: 병목 쿼리 4개 선정 + EXPLAIN Before 캡처
- Story 3-2: 복합 인덱스 설계 + DDL
- Story 3-3: After 실행 + Before/After 비교 리포트
- Story 3-4: 커버링 인덱스 시연 + ADR 016

## Epic 인수 시나리오
- Given 5만 상품 · 10만 주문 · 5만 검색로그 시드 완료
- When 대상 쿼리에 EXPLAIN 실행
- Then Before: type=ALL · key=NULL · rows=50000 · Extra=Using filesort (병목)

- Given 복합 인덱스 `idx_product_status_created (status, created_at DESC)` 추가
- When EXPLAIN 재실행
- Then After: type=index · key=idx_product_status_created · rows=20 · Extra=Using index (커버링)

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] EXPLAIN Before/After 표 (병목 쿼리 4개+)
- [ ] 커버링 인덱스 1건 이상 `Extra: Using index` 확인
- [ ] ADR 016 발행
- [ ] README §Indexing 섹션 · DDL 명시

## [Story 3-1] 병목 쿼리 선정 + Before

목록:
- 후보 쿼리 4개+:
  1. 상품 검색 정렬 (`SELECT ... FROM product WHERE status='SALE' ORDER BY created_at DESC LIMIT 20`)
  2. 마이 주문 이력 (`SELECT ... FROM order_item WHERE user_id=? ORDER BY created_at DESC`)
  3. CS 조회 (`SELECT ... FROM chat_room WHERE customer_user_id=? AND status=?`)
  4. 포인트 이력 (`SELECT ... FROM point_history WHERE user_id=? ORDER BY created_at DESC LIMIT 20`)
- 각 EXPLAIN 실행 · type/key/rows/Extra 표 정리 · 캡처

**SP**: 0.5d

## [Story 3-2] 복합 인덱스 설계 + DDL

목록:
- DDL:
  ```sql
  CREATE INDEX idx_product_status_created ON product (status, created_at DESC);
  CREATE INDEX idx_order_item_user_created ON order_item (user_id, created_at DESC);
  CREATE INDEX idx_chat_room_customer_status ON chat_room (customer_user_id, status, last_message_at DESC);
  CREATE INDEX idx_point_history_user_created ON point_history (user_id, created_at DESC);
  ```
- Leftmost Prefix 준수 · 카디널리티 순
- README에 DDL 명시

**SP**: 0.5d

## [Story 3-3] After + Before/After 리포트

목록:
- After EXPLAIN 재실행 · 표 갱신
- `docs/perf/indexing-explain-report.md`:
  - 쿼리별 Before/After 표 (type · key · rows · Extra)
  - 응답 시간 실측 개선 (통합 테스트 or k6)
  - 정량 개선률 (%)

**SP**: 1d

## [Story 3-4] 커버링 인덱스 + ADR 016

목록:
- 커버링 인덱스 시연: SELECT 컬럼이 모두 인덱스에 포함되도록 (예: `SELECT id, status, created_at FROM product WHERE status='SALE' ORDER BY created_at DESC`)
- `Extra: Using index` 확인
- ADR 016 발행:
  - 배경 · 결정(복합+커버링) · 대안(단일 인덱스·FULLTEXT) · 결과(정량) · 롤아웃 · Leftmost Prefix 규범

**SP**: 0.5d
