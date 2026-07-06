# [PES · Brainstorming] Deploy — 0.0.1v (2026-07-02)

> 배포 파이프라인 · CI/CD 자동화 관련 크로스 컷팅 후보.

---

## [Candidate 1] prod SecurityConfig 프로파일 분기 [pending]

### 배경
- CORS origins · JWT secret이 코드에 하드코딩
- prod 배포 시 FE 도메인 · 로테이션 대응 어려움
- 보안 취약점 (secret 노출 시 즉시 로테이션 필요)

### 후보안
- **A안 (선택)**: `application-{profile}.yml`에 origins 리스트 + JWT secret은 환경변수/Secrets Manager로 이동
- **B안**: CORS는 프로파일 분기, JWT는 base64 + 별도 파일
- **C안**: 최소 개선 — 하드코딩된 값을 `@Value`로 뽑아냄

### 1차 권장
- **A안** 착수 (fix tier)
- Secrets Manager는 Candidate 2로 분리 (지금 도입 여부는 별도)

### PES 승격 경로
- Fix `feature-story` tier: `fix/brainstorming/version/0.0.1v/infra.md` Issue 1

---

## [Candidate 2] AWS Secrets Manager 도입 [pending]

### 배경
- JWT · PortOne · Gemini · RDS credentials 등 다양한 secret 필요
- 현재 GitHub Actions Secrets + 환경변수 조합
- Secrets Manager는 로테이션 · IAM 통합 · 감사 로그 이점

### 후보안
- **A안 (지금)**: Secrets Manager 즉시 도입 → 모든 secret 이관
- **B안 (지연)**: 첫 secret 로테이션 필요 시점까지 유지
- **C안**: AWS Parameter Store (SSM) 사용

### 1차 권장
- **B안** (지연) — 현재 팀 규모 · secret 수 대비 오버헤드 큼
- 트리거: JWT secret 최초 로테이션 · 계약 secret 추가 시

### PES 승격 경로
- 신규 Product 후보: "Secrets 관리 인프라"

---

## [Candidate 3] Blue/Green 또는 Canary 배포 [pending]

### 배경
- 현재 단일 배포 (down time 있음)
- prod 첫 부팅 시 이슈(RDS 스키마) 있었음 → 안전한 배포 필요성 대두

### 후보안
- **A안 (지금)**: Blue/Green 도입 (ALB target group 2개 + 스위치)
- **B안 (지연)**: 사용자 100명 초과 시
- **C안**: Rolling update (ECS default) 유지

### 1차 권장
- **C안** (현상 유지) — 트래픽 낮고 롤백 시 재배포도 빠름
- 트리거: down time 민감 사용자 발생 시

### PES 승격 경로
- 신규 Product 후보: "무중단 배포 도입"

---

## [Candidate 4] CI 파이프라인 표준화 [pending]

### 배경
- GitHub Actions 워크플로우가 도메인·상황별로 분산
- 최근 커밋 `c4fc07b` (docker run 단일행)에서 배포 스크립트 지속 개선

### 후보안
- **A안 (선택)**: `.github/workflows/*.yml`을 목적별로 표준화 (`ci-test.yml`, `ci-build.yml`, `deploy-prod.yml`)
- **B안**: 하나의 큰 워크플로우로 통합
- **C안**: 3rd party CI (Jenkins 등)로 이동

### 1차 권장
- **A안** — 유지보수성 우선
- 팀 성장 시 재검토

### PES 승격 경로
- Fix `feature-story` tier: CI 워크플로우 표준화

---

## 참조

- 스냅샷: `snapshot.md`
- Fix 인프라 이슈: `../../fix/brainstorming/version/0.0.1v/infra.md`
- 배포 체크리스트: `workflows/living-docs/production-deployment-checklist.md`
- 팀 환경 설정: `workflows/living-docs/team-deployment-env-config.md`
