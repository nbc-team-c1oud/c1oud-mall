# [Fix · Brainstorming] Infra / Deploy — 0.0.1v (2026-07-02)

> **역할**: 인프라·배포·CI/CD·설정 관련 이슈를 축적. 크로스 컷팅 영역.
> **짝 파일**: 없음 (독립)
> **다음 단계**: 각 이슈 → 결정 → 정식 fix tier 문서로 승격

---

## 추적 컨텍스트

| 항목 | 사실 |
|---|---|
| Scope | Docker · AWS · Spring Profile · Secret 관리 |
| 진행 중 작업 | prod 프로파일 · RDS 첫 부팅 · 더미 데이터 확장 (최근 커밋) |
| 발견 시점 | 2026-06-09 페어리뷰 및 배포 과정 |
| fix tier 정의 | `../../../pes/workspectrum/sdd/sdd.md` |

---

## [Issue 1] — CORS 하드코딩 & JWT secret 함정 [pending]

### 현상 / 트리거
`2026-06-09-cors-hardcoded-and-jwt-secret-trap.md` (review/state): CORS allowed-origins가 코드에 하드코딩되어 있고, JWT secret이 application.yml 평문에 위치.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | 초기 개발 편의로 하드코딩 → 프로파일 분기 없음 | SecurityConfig 코드로 확인 |
| (b) | JWT secret이 gitignore되지 않은 yml에 노출 | git log 확인 필요 |

**최고 개연성**: (a). (b)는 노출 여부 즉시 확인 필요.

### 영향 범위
- prod 배포 시 FE 도메인 등록 문제
- 보안 취약점 (secret 노출 시 즉시 로테이션 필요)

### 결정해야 할 것
- **(A) `application-{profile}.yml`에 origins 리스트 + JWT secret은 환경변수/Secrets Manager로 이동** — 정공법
- **(B) CORS는 프로파일 분기, JWT는 base64 + 별도 파일** — 절충
- **(C) 최소 개선: 하드코딩된 값을 `@Value`로 뽑아냄** — 임시방편

### 권장 fix 방향 (1차)
1. Step 1: SecurityConfig에서 하드코딩된 origins 확인
2. Step 2: (b) 검증 — 현재 JWT secret이 git 히스토리에 있는지 확인
3. Step 3: 결정 (A) 채택 → `feature-story` tier

### workspectrum tier 추천
- **`feature-story`** — SecurityConfig 리팩토링 + prod 배포 검증

---

## [Issue 2] — JPA ddl-auto=update 유지 vs Flyway 도입 [pending]

### 현상 / 트리거
최근 커밋(`fcc67db fix(prod): JPA ddl-auto를 update로 변경`)로 prod 첫 부팅 시 RDS 테이블 자동 생성 이슈 해결. 하지만 이후 스키마 진화에는 Flyway 등 마이그레이션 도구가 필요.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | 현재 팀 규모/스피드에서 Flyway 도입 오버헤드 큼 | 팀 컨텍스트 |
| (b) | update 유지는 컬럼 삭제·리네임 케이스에 무력 | JPA 문서 확인 |

**최고 개연성**: 둘 다 사실. 트레이드오프 결정 필요.

### 결정해야 할 것
- **(A) 첫 스키마 변경 이슈 발생 시점에 Flyway 도입** — 지금은 update 유지
- **(B) 지금 도입 (선제 대응)** — 배포 워크플로우 정착 이점
- **(C) hibernate schema validate로 전환 + 수동 SQL 관리** — 중간 절충

### 권장 fix 방향 (1차)
1. Step 1: 팀 합의 (사용자 결정 필요)
2. Step 2: 결정 (A) 시 → 트리거 조건만 문서화하고 종결
3. Step 3: 결정 (B) 시 → `pes` tier로 승격

### workspectrum tier 추천
- (A): 문서화만 (**tier 없음**)
- (B): **`pes`** — Flyway 도입 + baseline 스크립트 + CI 검증

---

## [Issue 3] — Docker run 단일행 처리 · secret 값 따옴표 [resolved]

### 상태
**resolved** — 커밋 `c4fc07b fix(ci): docker run을 단일 행으로 변경 + secret 값 따옴표 처리`에서 반영 완료.

### 잔여 액션
- (없음)

---

## 누적 메모 (Free-form Memo)
- 2026-06-09 — CORS/JWT 이슈 페어리뷰 발견
- 2026-06-XX — JPA ddl-auto=update 임시 처치 (커밋 `fcc67db`)
- 2026-07-02 — 브레인스토밍 등록
- (추후 추가)

---

## 참조
- Fix tier 정의: `../../../pes/workspectrum/sdd/sdd.md` §fix 레이어
- Deployment 체크리스트: `workflows/living-docs/production-deployment-checklist.md`
- 팀 배포 환경 설정: `workflows/living-docs/team-deployment-env-config.md`
- SecurityConfig 원본: `src/main/java/nbc/c1oud_mall/common/config/SecurityConfig.java`
- application yml: `src/main/resources/application*.yml`
