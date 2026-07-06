# [PES · Brainstorming] Ops — 0.0.1v (2026-07-02)

> 운영 · 배포 · 관측성 · SRE 경계 관련 크로스 컷팅 후보.

---

## [Candidate 1] MDC 요청 컨텍스트 (requestId · userId) 도입 [pending]

> 로그·에러 트래킹의 기초. 요청 흐름 상관관계 확보.

### 배경
- 현재 로그에 requestId 없음 → 동시 요청 로그가 뒤섞임
- 사용자 문의 → 로그 검색 시 시간·경로만 기준 → 어렵고 부정확
- 운영 관측 성숙도의 첫 단추

### 후보안
- **A안 (선택)**: `OncePerRequestFilter` 기반 `MdcLoggingFilter` — 진입 시 requestId 발급, 응답 시 clean up. 이후 `AuthenticationFilter` 뒤에 userId injection
- **B안**: Servlet Filter → Spring Interceptor로 변경 → MVC 라이프사이클과 정합
- **C안**: Async Servlet + reactive 대응 (미래)

### 1차 권장
- **A안 착수** — product-log Epic 2에서 담당
- MDC 키: `requestId` (uuid), `userId` (인증 시)

### PES 승격 경로
- product-log Epic 2 (Story 2-1 · 2-2)
- 필요 ADR: 없음 (표준 패턴)

### 미결 질문
- Request-Id 헤더 수신 시 그대로 이어받을지, 항상 서버에서 새로 발급할지 (분산 트레이싱 대비)

---

## [Candidate 2] 구조화 로깅 JSON 포맷 (logstash-logback-encoder) [pending]

### 배경
- prod 로그가 사람이 읽는 문자열 → 집계·쿼리 불가
- 관측 도구(Cloudwatch Logs Insights 등)에서 필드 인덱싱 필요

### 후보안
- **A안 (선택)**: `net.logstash.logback:logstash-logback-encoder` + `logback-spring.xml`에 dev(콘솔)/prod(JSON) 분기
- **B안**: 자체 JSON 어펜더 구현
- **C안**: 텍스트 유지 + Cloudwatch Metric Filter로 파싱

### 1차 권장
- **A안 착수** — product-log Epic 1 Story 1-1

### PES 승격 경로
- product-log Epic 1

---

## [Candidate 3] 에러 로깅 표준화 (BusinessException 로그 레벨 매핑) [pending]

### 배경
- 현재 `GlobalExceptionHandler`가 모든 예외를 `log.warn`으로 출력
- 4xx 클라이언트 오류와 5xx 서버 오류를 같은 레벨로 잡음 → 알람 노이즈

### 후보안
- **A안 (선택)**: HTTP status 기준 매핑 — 4xx→`warn`, 5xx→`error`, `Access/Auth` → `info`
- **B안**: ErrorCode 접두사 기준 매핑
- **C안**: BusinessException 필드에 leveler 추가

### 1차 권장
- **A안 착수** — product-log Epic 3

### PES 승격 경로
- product-log Epic 3

---

## [Candidate 4] Actuator + Prometheus 도입 시점 [pending]

### 배경
- 관측 지표(Gauge / Counter / Histogram) 도입 트리거 부재
- 결제 성공률·평균 응답시간 등 KPI 관측 필요성 증가

### 후보안
- **A안 (지금)**: Actuator + `micrometer-registry-prometheus` 즉시 도입
- **B안 (지연)**: 사용자 100명 · 결제 100건 달성 시 도입
- **C안**: CloudWatch만 사용

### 1차 권장
- **B안** (지연) — 현재는 사용자 지표가 미미하고 로그로 충분
- 트리거: 일일 결제 건수 100건 · 사용자 100명 도달 시

### PES 승격 경로
- 신규 Product 후보: "관측 인프라 도입"

---

## 참조

- 스냅샷: `snapshot.md`
- product-log SDD (예정): `../../workspectrum/sdd/in-progress/product-log.md`
- Fix 인프라 이슈: `../../fix/brainstorming/version/0.0.1v/infra.md`
