# 트러블슈팅 회고

c1oud-mall 프로젝트(결제·환불·포인트·인증·배포)를 거치며 만났던 비자명한 문제와 해결을 영역별로 정리. 단순 오타·환경설정은 제외하고 **원인 추적이 까다로웠거나 같은 함정에 재방문하기 쉬운 사례**만 추렸다.

---

## 1. 결제 (Payment)

### 1-1. confirm API는 200인데 DB는 PENDING 그대로 — cart-clear의 사일런트 데이터 손실

**증상**

- `POST /api/v1/payments/confirm` 응답 200 + `paymentStatus: COMPLETED`
- 그러나 주문 내역에서 해당 주문이 계속 "결제 대기"로 표시
- 서버 로그에 exception 없음, SQL 로그에 `UPDATE payments / UPDATE orders` 한 줄도 안 보임
- 환불 시도 시 RF002 ("환불할 수 없는 결제 상태")

**원인 (한참 헤맸음)**

`CartItemJpaRepository.deleteAllByUserId`의

```java
@Modifying(clearAutomatically = true)   // flushAutomatically = false (기본값)
```

조합이 결제 확정 흐름 전체를 무력화하고 있었음.

같은 트랜잭션 안 순서:
1. `payment.markCompleted(...)` — dirty entity 등록 (flush 전)
2. `orderService.completeOrder(...)` — Order도 dirty 등록
3. `pointService.deductPoints/accruePoints(...)` — User dirty, PointHistory insert
4. `cartService.clearCart(userId)` → `cartItemJpaRepository.deleteAllByUserId(userId)`
   - `flushAutomatically=false` → 1~3의 dirty 변경분이 **DB로 flush 안 됨**
   - bulk DELETE 실행
   - `clearAutomatically=true` → 영속성 컨텍스트 전체 clear → **dirty Payment/Order/User 모두 detached → 변경분 폐기**
5. TX commit → PC 비어있음 → UPDATE statement 미생성

응답은 메모리상 `payment` 객체(이미 mutate된)를 직렬화하므로 200 + COMPLETED로 나감. DB는 그대로 PENDING. **사용자 관점에선 성공인데 백엔드는 손실** — 가장 무서운 종류의 버그.

**해결**

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
```

`flushAutomatically=true` 추가로 bulk DELETE 직전에 PC dirty 엔티티를 DB로 강제 flush. `clearAutomatically=true`는 그대로 두어 stale CartItem 참조 방지 효과 유지.

회귀 차단 통합 테스트(`PaymentConfirmationPointAccrualIntegrationTest`)도 추가 — `@SpringBootTest`로 실 PC 돌려서 `User.pointBalance`/`PointHistory(EARN)`/`Payment.pointEarnedAmount` 3중 영속 검증. 픽스 없으면 1~2가 실패.

**교훈**

- `@Modifying`의 `clearAutomatically=true`는 항상 `flushAutomatically=true`와 짝으로. 안 그러면 같은 TX의 다른 dirty 엔티티를 사일런트로 폐기.
- "응답은 성공인데 DB는 다름" 패턴이면 단위 테스트가 통과해도 통합 테스트(`@SpringBootTest` + 실 DB 재조회)로만 잡힌다.
- SQL 로그에 `UPDATE`가 한 번도 안 보이면 PC 자체가 비어 있다는 신호 — 어딘가 detach·clear가 일어났다고 의심.

### 1-2. Webhook 멱등성 — `(portonePaymentId, eventType)` INSERT-first

**증상**

PortOne 웹훅과 클라이언트 confirm API가 거의 동시에 도착하면 결제 확정이 두 번 처리될 위험.

**해결**

- `WebhookEvent` 테이블에 `(portonePaymentId, eventType)` UNIQUE 제약
- 웹훅 진입 시 **먼저 INSERT 시도** (`webhookEventRegistrar.tryRegister`)
- 충돌(UNIQUE 위반)이면 silent 200 OK + "이미 처리됨" 응답
- 성공이면 confirm 도메인 서비스 호출 (클라이언트 confirm과 동일 메서드 공유 → 양쪽 멱등 보장)

**교훈**

- 외부 시스템(PortOne)이 at-least-once 전달이면 우리 쪽은 무조건 INSERT-first 패턴 (idempotency.md §4 S+ 등급)
- 클라이언트와 웹훅이 **같은 도메인 서비스를 공유**해야 멱등성과 검증 로직이 한 곳에 집중됨

### 1-3. 결제 보상 트랜잭션 — `REQUIRES_NEW`로 메인 TX 롤백과 격리

**증상**

PortOne은 PAID인데 금액 위·변조로 우리 측 검증 실패(PM001). 메인 TX는 롤백돼야 하지만 보상(Payment.FAILED + 재고 복구 + PortOne 취소 호출)은 commit 돼야 함.

**해결**

- `PaymentCompensationTxOp.compensateDb(...)`를 `@Transactional(propagation=REQUIRES_NEW)`로 분리
- 메인 TX 롤백과 무관하게 별도 TX로 보상 commit
- PortOne 취소 호출은 TX 밖

**교훈**

- 보상 흐름이 메인 TX와 운명을 공유하면 안 됨 → 명시적 `REQUIRES_NEW`
- 외부 호출(PortOne 취소)은 항상 트랜잭션 밖 (consistency.md §6)

---

## 2. 환불 (Refund)

### 2-1. 비관락 + 잔여 수량 재검증 — 동시 환불 race 차단

**증상**

같은 결제에 동시 환불 요청 두 건이 들어오면 둘 다 잔여 수량 검증을 통과해버려 초과 환불 발생 위험.

**해결**

idempotency.md §4 A등급 패턴:

1. **선검증 (락 없는 fast-fail)** — `paymentRepository.findByOrderId(...)`로 일반 조회 후 잔여 수량 확인
2. **DB TX 안에서 재검증** — `paymentRepository.findByOrderIdForUpdate(...)` (PESSIMISTIC_WRITE) → `refundJpaRepository.sumRefundedQuantity(...)`로 재합산 → 다시 검증
3. 락 안에서 Refund 저장 → 락 해제 → TX commit

선검증은 트래픽 절감용 (대다수 요청은 여기서 fast-fail). 진짜 보장은 락 안의 재검증.

통합 테스트(`RefundProcessServiceIntegrationTest`)에서 `CompletableFuture` 두 개 + `CountDownLatch`로 동시 진입 시뮬레이션 → 정확히 1건만 성공, 다른 1건은 RF001(잔여수량 초과) 확인.

**교훈**

- "락 없는 선검증 → 락 안 재검증" 두 단계가 정석. 한쪽만 있으면 성능 또는 정확성 잃음.
- race 테스트는 `CompletableFuture.allOf` + `CountDownLatch`로 작성. H2도 비관락 동작함.

### 2-2. DB 커밋 후 PortOne 취소 (consistency.md §6)

**증상**

PortOne 취소 호출은 외부 통신이라 지연·실패 가능. 락 안에서 호출하면 DB 락이 PortOne 응답을 기다리며 다른 환불 요청을 막음.

**해결**

3단계 분리:
1. 선검증 (TX 밖)
2. **DB TX** — 비관락 → 재검증 → Refund 저장(`DB_COMMITTED`) → 재고/포인트 복구 → commit
3. **TX 밖** — PortOne 취소 호출 → 성공 시 별도 단일 TX로 `Refund.markPgCancelled` → `PG_CANCELLED`

PortOne 호출 실패 시 Refund는 `DB_COMMITTED` 상태로 남음. 응답은 202 Accepted + warning 필드로 "PG 취소 처리 중. 운영팀 확인 필요" 안내. 컨트롤러가 `result.isPgCancelled()`로 200/202 분기.

**교훈**

- 외부 호출이 길고 실패 가능하면 무조건 트랜잭션 밖. 보상은 별도 TX로 단일 UPDATE만.
- 부분 성공(DB OK, PG 실패) 상태를 응답 코드(202)로 명시 → 프론트가 사용자에게 정확히 안내 가능.

### 2-3. 적립 포인트 비례 회수 — lenient (잔액 부족 시 잔액까지만)

**증상**

환불 시 결제로 적립된 포인트를 비례 회수해야 하는데, 사용자가 이미 적립 포인트를 다 써버린 경우 회수 시도하면 잔액 음수 또는 환불 차단.

**해결**

- `User.useEarnedPointsLenient(amount)` 도메인 메서드 신설 — `min(pointBalance, amount)` 만큼만 차감, 실제 차감액 반환
- `PointService.cancelEarnedPoints(userId, amount, payment)` — User 비관락 + lenient 차감 + 실 차감액으로 `PointHistory(EARN_CANCEL)` 저장
- 부족분은 `log.warn("[POINT_EARNED_RECOVER_SHORT] ...")` 마커로 회계 모니터링용 기록
- **환불 자체는 차단하지 않음** — 사용자 불편 + PG 취소 race 위험 회피

**교훈**

- "정확하지만 사용자 차단" vs "약간 헐겁지만 차단 안 함"의 트레이드오프에서, 결제·환불 흐름은 후자 + 모니터링 로그가 정답에 가까움.
- 부족분을 log marker로 남기면 운영 대시보드에서 추적 가능.

### 2-4. Mock port 추상화 제거 — 단일 구현체 port의 함정

**증상**

`InventoryRestorePort` / `PointRestorePort` 인터페이스에 mock 어댑터만 존재 — 실 구현이 들어왔는데도 port 추상화가 발목 잡고 있었음.

**해결**

ADR-0003 §47 결정("단일 구현체 port는 도입 안 함")을 그대로 적용:
- port 인터페이스 삭제
- `RefundTxOp`가 `ProductService.restoreStockWithLock` / `PointService.restorePoints` 직접 주입·호출
- 기존 `OrderFacade.cancelOrder` 패턴과 일관

**교훈**

- port 추상화는 "여러 구현이 진짜로 필요할 때"만 가치. mock 하나만 있으면 dead seam.
- mock → 실 구현 교체 시 port를 같이 삭제하는 게 정석. 안 그러면 thin wrapper가 영원히 남는다.

---

## 3. 포인트 (Point)

### 3-1. PointPolicy 캡슐화 — 결제 BC가 포인트 정책 세부 모르게

**증상**

`PaymentConfirmationService`가 `PointPolicy`를 직접 주입받아 `pointPolicy.calculateEarnedAmount(...)`로 산정. 결제 도메인이 포인트 정책 세부에 결합 — 정책 진화(등급별 차등, 캠페인) 시 결제 BC도 같이 수정 필요.

**해결**

- `PointService.calculateEarnedAmount(long totalAmount)` 한 줄 위임 메서드 신설 — 내부에서 `PointPolicy` 호출
- `PaymentConfirmationService`는 `PointPolicy` import 제거, `pointService.calculateEarnedAmount(...)`만 호출
- payment.application → point.application(`PointService`) ✓ / payment.application → point.domain(`PointPolicy`) ✗ (캡슐화 달성)

**교훈**

- "정책 객체"는 그것을 가장 잘 아는 BC의 service 안에서만 소비. 다른 BC는 결과만 받음.
- DDD 의존 방향이 깨지면 정책 변경의 파급이 BC 경계를 넘는다.

### 3-2. PointPolicy basis points 외부화 — yml에서 운영 정책 변경

**해결**

- `@ConfigurationProperties(prefix="points")` record로 `accrualRateBasisPoints` 1개 필드만 외부화
- `application.yml`의 `points.accrual-rate-basis-points: 100` (= 1.00%)
- basis points 단위 (1bp = 0.01%) → 0.5%·1.25% 등 미세 조정 시에도 정수 산술로 정확

**교훈**

- 외부 설정으로 가져갈 값은 처음부터 SI 단위(basis points 같은 정수)로 정의. 부동소수 % 쓰지 말 것.
- record + `@ConfigurationProperties` + `@EnableConfigurationProperties`는 Spring Boot 4에서 가장 가벼운 정책 외부화 패턴.

---

## 4. 인증·공통 (Auth / Cart)

### 4-1. `:memberId` JPQL 파라미터 바인딩 미스매치

**증상**

```
org.hibernate.QueryParameterException:
  No argument for named parameter ':memberId'
```

`CartService.clearCart` 호출 시점에 폭발 → confirm 흐름 전체 5xx.

**원인**

```java
@Modifying(clearAutomatically = true)
@Query("DELETE FROM CartItem c WHERE c.userId = :memberId")  // ← :memberId
void deleteAllByUserId(@Param("userId") Long memberId);       // ← @Param("userId")
```

JPQL placeholder와 `@Param` 이름 불일치. 변수명(`Long memberId`)은 무관. Spring Data JPA는 `@Param`만 본다.

**해결**

JPQL placeholder를 `:userId`로 통일 (파일 내 다른 메서드와 일관).

**교훈**

- `@Query` + `@Param` 사용 시 **JPQL의 `:이름` ↔ `@Param("이름")` 만 매칭**. 메서드 파라미터 변수명은 영향 없음.
- 이런 미스매치는 컴파일 통과 + 단위 테스트도 통과(메서드 자체를 안 부르면) → 통합 테스트나 실 호출에서만 폭발. CI에 통합 테스트가 있어야 잡힌다.
- 코드베이스 전체에 같은 패턴이 더 있을 수 있으니 `@Query` + `@Param` 조합 전수 검토 권장.

### 4-2. CORS — WebConfig vs SecurityConfig

**증상**

CloudFront 프론트가 백엔드 호출 시 CORS 차단. `WebConfig.addCorsMappings`에 CloudFront 도메인을 추가했는데도 안 됨.

**원인**

Spring Security가 적용된 요청은 `WebMvcConfigurer.addCorsMappings`(DispatcherServlet 레벨)가 아니라 **`SecurityConfig.corsConfigurationSource()`(Security 필터 체인)이 진짜 게이트**. WebConfig 추가는 dead code.

**해결**

`SecurityConfig`의 `setAllowedOriginPatterns(...)`에 CloudFront 도메인 추가.

**교훈**

- Spring Security 사용 중이면 CORS는 무조건 `SecurityConfig` 안에서. WebConfig만 고치면 효과 없음.
- "CORS 추가했는데 안 됨"이면 진짜 게이트가 어디인지부터 확인 (filter chain 우선순위).

---

## 5. 배포 (Deploy)

가장 누적 손실이 컸던 영역. 한 번 새 인프라 컴포넌트를 추가할 때마다 새로운 함정이 나왔다.

### 5-1. `./gradlew: Permission denied` — file mode 100644

**증상**

GitHub Actions Linux runner에서:
```
./gradlew: Permission denied
Error: Process completed with exit code 126.
```

**원인**

`gradlew`가 git index에 `100644`(실행 권한 없음)로 등록. Windows에서 작업하던 팀원의 `core.filemode=false` 설정으로 인해 로컬에선 안 보이다 Linux에서 노출.

**해결 (이중 안전망)**

1. `git update-index --chmod=+x gradlew` — index file mode를 `100755`로 변경
2. workflow에 `chmod +x ./gradlew` 스텝 추가 — 누군가 다시 권한을 없애도 빌드 직전 보장

**교훈**

- Windows 팀 + Linux CI 조합이면 `gradlew`/`mvnw` 같은 스크립트는 처음부터 `git update-index --chmod=+x` 처리 + workflow에 chmod safety net 추가.

### 5-2. Docker 이미지 플랫폼 미스매치 — AMD64 빌드 ↔ ARM64 EC2

**증상**

배포 자체는 success지만 컨테이너 동작이 불안정. CORS 헤더가 간헐적으로 안 붙음. deploy 로그에:

```
WARNING: The requested image's platform (linux/amd64) does not match
the detected host platform (linux/arm64/v8)
```

**원인**

- GitHub Actions runner: `linux/amd64` (기본)
- EC2: ARM64 (Graviton 계열 t4g/c7g)
- Docker가 QEMU 에뮬레이션으로 amd64 이미지를 arm64에서 실행

크로스 아키텍처 에뮬레이션에서 JVM/Tomcat/Spring Security 일부가 예측 불가하게 동작.

**해결**

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3

- name: Build and push Docker image
  uses: docker/build-push-action@v5
  with:
    platforms: linux/arm64   # ← 명시
    ...
```

빌드 시간 약간 늘지만 운영 호스트가 native로 실행 → 안정성·성능 회복.

**교훈**

- EC2 인스턴스 타입(`uname -m`)을 확인하고 빌드 플랫폼을 명시. `arm64`/`amd64` 둘 다 지원해야 하면 `platforms: linux/amd64,linux/arm64`로 multi-arch.
- "에뮬레이션이라 그냥 좀 느릴 뿐"이 아니라 정확성 문제가 생길 수 있다. 무조건 native 빌드.

### 5-3. `docker run -d \` 멀티라인 라인 이음이 SSH로 전송되며 깨짐

**증상**

```
err: bash: line 7: -e: command not found
err: docker: 'docker run' requires at least 1 argument
```

CMD 출력에는 멀티라인 형태가 그대로 보이는데, 실행되면 첫 줄만 실행되고 두 번째 줄부터 별도 명령으로 해석됨.

**원인**

`appleboy/ssh-action`이 YAML `script: |` 블록을 EC2로 전송하는 과정에서 라인 끝 `\` 뒤에 공백 또는 CRLF가 끼어 bash가 라인 이음을 인식 못 함.

**해결**

`docker run` 전체를 **한 줄로 평탄화** + secret 값을 `'...'` 단일 따옴표로 감쌈 (DB_URL의 `?useSSL=true&serverTimezone=...`에서 `&`가 bash 백그라운드 실행으로 해석되는 두 번째 버그도 동시 차단).

```yaml
script: |
  docker pull ...
  docker run -d --name c1oud-mall -p 8080:8080 -e DB_URL='${{ secrets.DB_URL }}' -e ... ${{ secrets.DOCKERHUB_USERNAME }}/c1oud-mall:latest
```

가독성 손실은 있지만 운영 신뢰성이 우선.

**교훈**

- SSH로 전송되는 yaml 멀티라인 script는 `\` 라인 이음을 신뢰하지 말 것. 한 줄 또는 heredoc.
- secret 값에 `&`, `?`, 공백이 들어갈 가능성이 있으면 항상 single quotes로 감싸기.

### 5-4. GitHub Secrets 미등록 → `PlaceholderResolutionException`

**증상**

배포는 성공하지만 컨테이너가 1~2초 후 exit. `docker logs c1oud-mall`:

```
Could not resolve placeholder 'SUPER_ADMIN_EMAIL' in value "${SUPER_ADMIN_EMAIL}"
```

**원인**

`application-prod.yml`의 `${SUPER_ADMIN_EMAIL}` placeholder가 미해소. GitHub Secret 미등록 상태에서 `${{ secrets.X }}`는 **빈 문자열**로 치환 → workflow는 통과하지만 컨테이너 내부에서 placeholder 해소 실패.

추가로 `PortOneWebhookSignatureFilter` 생성자는 `webhook-secret`이 비면 `IllegalStateException`을 던지도록 만들어둠 — 빈 값으로 운영 시작되는 사고 차단.

**해결**

GitHub repo Settings → Secrets and variables → Actions에 누락된 시크릿 등록 (SUPER_ADMIN_*, PORTONE_API_SECRET, PORTONE_WEBHOOK_SECRET 등).

**교훈**

- yml에 `${X}` placeholder를 추가했으면 즉시 GitHub Secret 등록 체크리스트로 옮겨야 함. PR 본문에 명시.
- 빈 값으로 운영 시작이 위험한 시크릿(웹훅 시크릿 등)은 도메인 컴포넌트 생성자에서 explicit fail 처리 — `IllegalStateException`이 컴파일타임 검증 대용.

### 5-5. JDBC URL 형식 오류 — 호스트만 입력

**증상**

```
Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl,
c1oud-mall-database.xxxxx.ap-northeast-2.rds.amazonaws.com
```

**원인**

`DB_URL` Secret 값이 RDS 엔드포인트(호스트만)로 들어가 있고 `jdbc:mysql://` 프리픽스, 포트, DB명이 누락. MySQL 드라이버가 "이 URL은 못 받는다"고 거부.

**해결**

올바른 JDBC URL 형식으로 교체:
```
jdbc:mysql://<host>:3306/<dbname>?useSSL=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

`<dbname>`이 RDS의 실제 데이터베이스명과 정확히 일치해야 함.

**교훈**

- AWS Console에서 RDS 엔드포인트만 복사해 붙이지 말고 JDBC 전체 URL을 정리해서 secret에 저장.
- DataSource 연결 에러는 메시지를 정확히 읽으면 거의 즉시 진단 가능. "claims to not accept jdbcUrl"이 핵심 키워드.

### 5-6. Hibernate `ddl-auto: validate` — 빈 RDS에서 부팅 실패

**증상**

DB 연결까지 성공 후:
```
Schema validation: missing table [cart_item]
```

**원인**

`application-prod.yml`이 `ddl-auto: validate` — "테이블 모두 존재 검증"하는 모드. 운영 RDS가 빈 상태(첫 배포)면 통과 못 함. Flyway/Liquibase 미도입 상태라 자동 마이그레이션도 없음.

**해결 (첫 부팅 부트스트랩만)**

`ddl-auto: update`로 변경. Hibernate가 엔티티 기반으로 테이블 자동 생성. 이후 컬럼 추가는 `ALTER ADD COLUMN`, DROP·MODIFY는 절대 안 함 → 운영 데이터 안전.

**교훈**

- 신규 운영 DB 첫 부팅 한 번은 `update`로 부트스트랩 가능. 그 다음엔 Flyway 도입 후 `validate`로 복귀가 정석.
- `validate` ↔ `update` ↔ `create`의 차이를 정확히 알고 운영 yml에 박을 것:
  - validate — 검증만 (실패 시 부팅 실패)
  - update — 추가만 (DROP 안 함, 데이터 안전)
  - create / create-drop — DROP 후 CREATE → **운영 절대 금지**

### 5-7. application-dev.yml placeholder 기본값 — CI 통합 테스트 회복

**증상**

PR #47 이후 develop의 통합 테스트 28건이 일제히 실패:
```
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

**원인**

application.yml의 `jwt.secret`을 `${JWT_SECRET}` 환경변수화하면서 application-dev.yml에 기본값을 안 줬음. 로컬·CI 환경에서 환경변수 없이 부팅하면 placeholder 미해소.

**해결**

application-dev.yml에 dev/test용 placeholder 기본값 추가:
```yaml
jwt:
  secret: ${JWT_SECRET:dev-jwt-secret-for-local-and-test-only}

super-admin:
  email: ${SUPER_ADMIN_EMAIL:super-admin-dev@example.com}
  password: ${SUPER_ADMIN_PASSWORD:dev-super-admin-password}
```

운영은 환경변수 오버라이드.

**교훈**

- `${VAR}` 형태로 외부화할 때 dev/test 프로파일엔 반드시 `:기본값` 부여 — 운영 안전성과 로컬 편의 동시 달성.
- 운영 환경변수를 추가하는 PR을 머지하면 같은 PR에서 dev 기본값도 같이 처리해야 CI 깨지지 않음.

---

## 횡단 교훈 (Cross-cutting Lessons)

### 운영 vs 로컬·CI 환경 분기는 **항상 같이 다룬다**

운영 yml(`application-prod.yml`)이나 `deploy.yml`을 변경할 때 dev profile / CI 영향이 같이 가는지 한 번 더 확인. 분리된 변경으로 머지하면 어느 쪽이 깨질지 모름.

### "응답은 OK인데 DB는 다르다" 패턴

- 사일런트 데이터 손실의 전형
- 단위 테스트(Mockito)로는 못 잡음 — `@SpringBootTest`로 실 PC 사용 + DB 재조회 어설션이 유일
- 원인은 보통 JPA 영속성 컨텍스트 detach/clear · TX 경계 오설정 · `clearAutomatically` 같은 부작용 속성

### CI 에러는 시점·메시지를 항상 확인

- 같은 에러 메시지가 다른 시점에 발생할 수 있음 (이전 실패 로그를 새 실패로 오인)
- workflow run timestamp를 기준으로 "이 에러가 어떤 commit에서 났는가" 확정 후 진단

### Secrets 관리는 **2단계로 분리**

1. 코드(yml/workflow)는 placeholder 형태로 PR
2. GitHub Secrets 등록은 별도 사용자 액션 — PR 본문 체크리스트로 명시

빈 secret이 들어가도 워크플로우는 통과해버리는 함정 회피.

### "한 줄로 펴라"의 의외의 가치

- 가독성 vs 신뢰성 트레이드오프에서 운영 critical path는 신뢰성이 압도적으로 우선
- `docker run` 멀티라인 / SQL 멀티라인 / 복잡 명령은 한 줄로 평탄화 + 따옴표가 안전한 default

### 통합 테스트는 회귀 차단의 마지막 보루

- 사일런트 버그를 잡으려면 결국 `@SpringBootTest` + 실 DB(H2 / Testcontainers) + 재조회 어설션
- 본 프로젝트에서 `PaymentConfirmationPointAccrualIntegrationTest`, `RefundProcessServiceIntegrationTest`(race + rollback 케이스), `PaymentConfirmationServiceCartClearIntegrationTest`가 이 역할을 했다
