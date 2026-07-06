# [Infra] 0.0.1v — 첫 프로덕션 배포 성과

> 첫 배포 버전의 실제 인프라 구성·CI/CD·환경 설정·달성 산출물 기록.

---

## 배포 상태 (Final)

| 환경 | 프로파일 | 상태 | 배포 방식 |
|---|---|---|---|
| local/dev | `local` / `dev` (H2) | 정상 | 로컬 개발용 (H2 in-memory, MySQL 호환 모드) |
| prod | `prod` (RDS MySQL) | **정상 · 첫 배포 성공** | EC2 ARM64 컨테이너 · DockerHub 이미지 pull |

---

## 배포 파이프라인 (`.github/workflows/deploy.yml`)

```
push (main/release)
   ↓
1. actions/checkout@v3
2. Setup JDK 21 (temurin)
3. Gradle 빌드 (`./gradlew build -x test`) — 테스트 스킵
4. Docker Hub 로그인
5. QEMU + Docker Buildx 세팅
6. Docker 이미지 빌드 (linux/arm64)
7. DockerHub 이미지 푸시
8. SSH → EC2 접속
9. docker pull + docker run (환경변수 6+개 주입)
```

**특이점**: 
- 빌드 시 테스트 제외(`-x test`) — CI 시간 절약, 로컬/PR 단계에서 테스트 통과 전제
- ARM64 플랫폼 (Graviton EC2 사용 · x86 대비 비용 절감)
- ECR 아닌 DockerHub 사용 (초기 스타트업 세팅)

---

## Dockerfile (멀티 스테이지)

```dockerfile
# 빌드 스테이지
FROM gradle:8.14-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle build --no-daemon -x test

# 런타임 스테이지
FROM eclipse-temurin:21-jre-jammy
COPY --from=build /app/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
```

**특이점**:
- 빌드용은 `gradle:8.14-jdk21` (~800MB) · 런타임은 `eclipse-temurin:21-jre-jammy` (~200MB)
- 최종 이미지 예상 300~400MB (JAR + JRE)
- Graviton(ARM64) 지원 이미지 사용

---

## 완료 산출물

### CI/CD
- [x] GitHub Actions `deploy.yml` 파이프라인 정착
- [x] DockerHub 이미지 자동 푸시
- [x] EC2 SSH 자동 배포 (docker pull → run)
- [x] docker run 단일 행 · secret 따옴표 처리 (커밋 `c4fc07b`)

### Container & Runtime
- [x] Java 21 · Spring Boot 4.0.6 실 부팅 검증
- [x] ARM64 이미지 EC2 pull 성공
- [x] `SPRING_PROFILES_ACTIVE=prod` 환경변수 정상 인식

### Database
- [x] prod → RDS MySQL 연결 성공
- [x] JPA `ddl-auto=update` — 엔티티 기반 자동 스키마 생성 (첫 부팅) (커밋 `fcc67db`)
- [x] 이후 엔티티 추가(Refund·WebhookEvent 등) 시 컬럼 자동 반영 확인
- [x] 더미 상품 32건 `DummyDataInit` 프로파일 조건부 초기화 (커밋 `532d653`)

### 외부 연동
- [x] PortOne V2 REST API 실 연동 (`https://api.portone.io`)
- [x] PortOne 웹훅 HMAC-SHA256 서명 검증 (`PortOneWebhookSignatureFilter`)
- [x] 웹훅 시크릿 미설정 시 서버 기동 실패 (fail-fast 확인)

---

## 환경 변수 (EC2 배포 시 주입)

| 변수 | 용도 | 필수 |
|---|---|---|
| `DB_URL` | RDS 엔드포인트 | ✅ |
| `DB_USERNAME` | RDS 사용자 | ✅ |
| `DB_PASSWORD` | RDS 비밀번호 | ✅ |
| `JWT_SECRET` | JWT 서명 키 | ✅ |
| `PORTONE_API_SECRET` | PortOne V2 API Bearer | ✅ |
| `PORTONE_WEBHOOK_SECRET` | 웹훅 HMAC 시크릿 | ✅ |
| `SUPER_ADMIN_EMAIL` | 초기 슈퍼어드민 계정 | ✅ |
| `SUPER_ADMIN_PASSWORD` | 초기 슈퍼어드민 비번 | ✅ |

**관리 방식**: GitHub Secrets · EC2 환경변수 주입 (Secrets Manager 미도입 → M3+ 검토)

---

## 환경별 설정 스냅샷

| 항목 | local | dev | prod |
|---|---|---|---|
| Datasource | H2 in-memory (MySQL 호환) | H2 in-memory | RDS MySQL (환경변수) |
| JPA `ddl-auto` | `create-drop` | `create-drop` | `update` |
| Profile 활성 | `local` | `dev` | `prod` (`SPRING_PROFILES_ACTIVE=prod`) |
| PortOne | (미연동 or 테스트) | 테스트 채널 | 운영 채널 (env) |
| CORS origins | `http://localhost:*` · `https://localhost:*` | 동일 | 동일 (하드코딩 잔존 · Fix Issue 1 이월) |
| DummyDataInit | 활성 | 활성 | 활성 (32건) |
| 로그 포맷 | Spring Boot 기본 평문 | 동일 | 동일 (Log Product 미착수 → M2) |
| Actuator | 미도입 | 미도입 | 미도입 (M2+ 검토) |

---

## 리소스 사용 (Baseline)

| 리소스 | 스펙 | 비고 |
|---|---|---|
| EC2 인스턴스 | ARM64 (Graviton) | Docker 이미지 ARM64 빌드 대응 |
| RDS 인스턴스 클래스 | (미명시) | 추정 `t3.micro`~`t4g.small` 수준 |
| Docker 이미지 크기 | 예상 300~400MB | JAR + JRE-jammy |
| 컨테이너 메모리 | 기본 JVM 자동 | GC 옵션 미설정 (기본값) |
| 트래픽 | 초기 · 저부하 | 실 사용자 지표 미측정 |

---

## 이 버전에서 미도입 · M2+ 이월

| 항목 | 사유 | 예정 |
|---|---|---|
| Flyway 스키마 마이그레이션 | `ddl-auto=update`로 충분 (커밋 · rename 케이스 미발생) | M3+ (실 사고 트리거 시) |
| 관측성 (JSON 로그 · MDC · Actuator) | Log Product 미착수 | M2 Log Epic 1~2 |
| Secrets Manager | 현 규모 오버킬 | 첫 secret 로테이션 시점 |
| Blue/Green 배포 | 트래픽 낮음 | 사용자 100명+ 시 |
| ECR 전환 | DockerHub로 충분 | private 저장소 필요 시 |
| CORS 프로파일 분기 | 하드코딩 잔존 (`fix/brainstorming/infra.md` Issue 1) | M2 |

---

## Hermes 폴더 (참고)

`/hermes` — 팀 교육·온보딩용 학습 자료 (실습 코드 포함). **배포 산출물 아님**.
- `/planning/workflows`, `/software/workflows` — 계획·설계 문서
- `/test/devTest/01~07` — 7개 주제 (Service/Domain, Architecture/DDD, Consistency, Payment/Idempotency, Testing, Ops/Deploy, Security)

---

## 다음 인프라 우선순위 (M2 후보)

1. **Log Product Epic 1**: `logstash-logback-encoder` 도입 → JSON 로그 (prod)
2. **Log Product Epic 2**: `MdcLoggingFilter` (requestId · userId)
3. **CORS 프로파일 분기** (Fix Issue 1)
4. **Actuator `/health`** 도입 (헬스체크 정확도 향상)
5. **Grafana 또는 CloudWatch Insights** — 로그 수집기 검토 (Log Product 완료 후)

---

## 참조

- 배포 파이프라인: `.github/workflows/deploy.yml`
- Dockerfile: `/Dockerfile`
- 설정 파일: `src/main/resources/application*.yml`
- 배포 체크리스트: `workflows/living-docs/production-deployment-checklist.md`
- 팀 환경 설정: `workflows/living-docs/team-deployment-env-config.md`
- 인프라 이슈 트래킹: `../../fix/brainstorming/version/0.0.1v/infra.md`
- 배포 관련 커밋: `c4fc07b`, `fcc67db`, `532d653`, PR #65 · #67
