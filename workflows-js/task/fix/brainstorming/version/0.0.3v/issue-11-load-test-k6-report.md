# [0.0.3v · Issue 11] k6 부하테스트 v1/v2 성능 비교 리포트

> **역할**: 캠프 요구사항의 도전 기능 — 부하 테스트 도구로 v1/v2 성능을 정량 측정하고 리포트로 산출.
> **Week**: 3 · **Day**: 17
> **상태**: pending
> **Tier**: `sdd-lite`
> **캠프 요구사항 매핑**: 도전 — k6 부하테스트 (v1/v2 · vUser Ramp Up · TPS · Saturation Point)

---

## 배경

캠프 요구사항 명시:
- k6 등 부하 도구로 v1/v2 API 각각 성능 테스트
- 두 포인트: (1) 평균 응답 속도 (2) 감당 가능한 사용자 수
- vUser 점진 증가 · Ramp Up 활용
- TPS 참고해 포화지점(Saturation Point) 찾기
- 성능 테스트 보고서를 산출물로 작성

## 핵심 스코프

### k6 시나리오 스크립트

```javascript
// tests/k6/search-v1-vs-v2.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },    // Ramp Up
    { duration: '1m', target: 100 },
    { duration: '30s', target: 300 },   // Saturation 탐색
    { duration: '1m', target: 300 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const KEYWORDS = ['맥북', '아이폰', '노트북', '자취', ...];
const BASE = 'http://localhost:8080';

export default function () {
  const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  const v1 = http.get(`${BASE}/api/v1/products/search?keyword=${kw}&size=20`);
  check(v1, { 'v1 200': (r) => r.status === 200 });
  const v2 = http.get(`${BASE}/api/v2/products/search?keyword=${kw}&size=20`);
  check(v2, { 'v2 200': (r) => r.status === 200 });
  sleep(1);
}
```

### 실행 절차
1. dev-large 프로파일 부팅 (5만+ 데이터)
2. k6 스크립트 실행 (`k6 run search-v1-vs-v2.js`)
3. 요약 리포트 저장 (JSON export)
4. Grafana Dashboard (선택 · docker-compose k6+influx+grafana)

### 리포트 산출

`docs/perf/search-v1-vs-v2-report.md`:
- **응답 시간**: P50/P95/P99 각 vUser 단계별 (표)
- **TPS**: 단계별 RPS
- **에러율**: 4xx/5xx 비율
- **Saturation Point**: TPS 곡선이 꺾이는 지점
- **비교 결론**: v2가 P95를 X% 개선

## 산출물

- BE-11-1: k6 스크립트 (`tests/k6/search-v1-vs-v2.js`)
- BE-11-2: 실행 스크립트 (`scripts/run-k6.sh` or PowerShell)
- BE-11-3: 리포트 문서 (`docs/perf/search-v1-vs-v2-report.md`)
- BE-11-4: v1 baseline vs v2 (Caffeine) vs v2 (Redis Remote) 3구획 비교
- BE-11-5: README에 성능 그래프/표 삽입
- BE-11-6: Ramp Up 활용 근거 (왜 Ramp Up 필요한지 문서 언급)

## 관련 이슈 / 문서

- 선행: [02 더미 시드](./issue-02-dummy-seed-50k-datafaker.md) — 5만+ 데이터
- 선행: [03 v1](./issue-03-search-v1-querydsl-like-cursor.md) · [04 v2 Caffeine](./issue-04-search-v2-caffeine-local-cache.md) · [10 Redis Remote](./issue-10-cache-remote-redis-eviction.md)
- 다음: [12 인덱싱](./issue-12-indexing-explain-report.md) — 병목 쿼리 발견 시 인덱스 개선
- 다음: [14 ADR·README](./issue-14-adr-readme-documentation.md) — 리포트 통합

## 상세 (착수 시 채움)

_pending_
