# [Product 11] 미디어 (Media · S3 Pre-signed URL 업로드 · 갤러리)

## Product Vision
> 프로젝트 커버·스크린샷·스토리 삽입 이미지·채팅 첨부·메이커 아바타 등 c1oud-mall의 모든 이미지 자산을 통합 관리한다. **S3 Pre-signed URL 2단계 업로드**로 FE가 서버 대역폭 없이 S3에 직접 업로드하고, dev/prod 환경별 백엔드(MinIO/S3)를 자동 분기하며, 참조 무결성·삭제 정책·이미지 dimension 자동 추출로 저작권·성능·정합성을 동시에 확보한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - c1oud-mall은 이미지 업로드 도메인 부재 · 모든 이미지가 UI 목업 상태
  - Project (Product 6)의 `coverMediaId` FK는 스키마에만 존재 · 실 파일 저장 인프라 없음
  - Chat (Product 9)의 `IMAGE` MessageType payload는 정의됐으나 imageUrl 원천 없음
  - Maker Profile (Product 10)의 아바타는 이니셜 렌더링만 · 사용자 이미지 지원 불가
  - 디자인(`프로젝트 상세.html · 프로젝트 등록.html`) 확인: 커버 (16:9) + 4개 스크린샷 갤러리 · 드래그·드롭 업로더 UI 명세됨
- 발생하는 문제
  - 프로젝트 등록 UX가 미완성 · 실 이미지 없이 리치 콘텐츠 노출 불가 → 후원자 이탈률 상승
  - Chat IMAGE 발송 불가 (Product 9의 Rich Message 완결도 저해)
  - 서버 대역폭 부담: 만약 서버가 파일 수신하면 EC2 대역폭 폭증 · 비용·성능 리스크
  - 저작권·개인정보 리스크: 업로드 파일에 대한 검증 없이 방치 시 악용
  - 이미지 dimension 없이 UI 렌더링 시 레이아웃 시프트(CLS 저하) · 사용자 UX 저해
- 왜 지금 해결해야 하는가
  - Project·Chat·Maker Profile 완결 후 이미지 원천 도입의 자연스러운 시점
  - S3 Pre-signed URL 학습 스토리 (백엔드 개발자 인터뷰 어필 최상급)
  - 초기 사용자 없어 저장 정책·삭제 정책·검증 규칙을 코드에 굳혀둘 최적 시점
  - Discovery (Product 13)의 카드 UI에서도 원본 사이트 썸네일 캐싱 시 활용 여지 (v0.0.5+)

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.media.*` (4레이어)
- `Media` **Aggregate root** · 3개 enum (`MediaPurpose · MediaStatus · MediaReferenceType`)
- **S3 Pre-signed URL 2단계 업로드**:
  - 1단계: `POST /api/v1/media/upload-url` — 서버 검증(size · mime · purpose) → PENDING 저장 + Pre-signed PUT URL 발급 (5분 유효)
  - 2단계: FE가 S3에 직접 PUT → `POST /api/v1/media/{id}/complete` — S3 실 존재 확인 + dimension 추출 → COMPLETED 전이
- **환경별 백엔드 분기**:
  - dev/local: **MinIO** (Docker) · 로컬 endpoint
  - prod: **AWS S3** (실 버킷)
- **검증 상수**: MAX_SIZE_BYTES=10MB · ALLOWED_MIMES={jpeg/png/webp/gif} · MAX_SCREENSHOT_COUNT=4 · MAX_STORY_COUNT=20
- **참조 첨부 흐름**: `Media.attach(referenceType, referenceId)` — Project/Chat/User와 결합
- **Soft Delete + 30일 배치 실 삭제**: DB `deleted_at` 표기 → 배치가 30일 후 S3 파일 실 삭제
- **참조 미디어 삭제 방지**: Project 상태 `DRAFT` 아닌 미디어 삭제 시 `RWD004` 유사 · `MED008 REFERENCE_LOCKED`
- REST 4개 엔드포인트
- `ErrorCode.MED001~008` (8개)
- 관측 지표 5종

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **S3 Pre-signed URL 2단계 채택** (이슈 #13 Option A · 확정)
  - 서버 대역폭 절약 · 스토리 파괴력 최상 (신입 개발자 어필 최상급)
  - 이중 검증 (Pre-signed 발급 전 · 완료 확정 시) 안전
- **환경별 백엔드 분기 (MinIO/S3)**
  - dev/local: MinIO 활성 · Docker Compose에서 실행 가능
  - prod: AWS S3 실 버킷 (`c1oud-mall-media`)
  - Spring `@Profile` 기반 Bean 선택 (`S3ClientConfig`)
  - AWS SDK for Java v2 (`software.amazon.awssdk:s3`) 활용
- **`Media` 별도 Aggregate root** (Project · Chat · User 어느 도메인에도 소속 X)
  - 여러 도메인에서 참조 · 독립 관리 필요
  - `reference_type + reference_id` 컬럼으로 소유 추적 (nullable)
- **1단계 = PENDING · 2단계 = COMPLETED · 사이는 서버가 완결 판정**
  - 1단계 후 FE가 실 업로드 안 하면 PENDING 상태 유지 · 배치 정리 대상
  - 2단계에서 서버가 `s3.headObject()`로 실 존재 확인 → COMPLETED 전이
- **이미지 dimension 서버 추출**
  - 2단계 완료 확정 시 `java.awt.image.BufferedImage`로 width·height 추출
  - FE 렌더링 시 CLS(Cumulative Layout Shift) 예방 · Web Vitals 개선
- **`purpose` 기반 개수 제한**
  - SCREENSHOT: 최대 4개 · STORY: 최대 20개 · 나머지: 1개 (COVER, AVATAR)
  - Service 계층에서 검증 · `MED009 QUANTITY_EXCEEDED` (필요 시 추가)
- **Soft Delete + 30일 후 실 삭제 배치**
  - DB `deleted_at != null` 시 조회 자동 필터 (`@SQLRestriction`)
  - 매일 새벽 4시 배치 · 30일 초과 미디어 S3에서 실 삭제
  - 참조된 미디어는 삭제 방지 (`MED008 REFERENCE_LOCKED`)
- **`Media`는 지연 첨부 방식** — 업로드 시점엔 참조 무결성 X
  - 업로드 → COMPLETED → 이후 `attach(PROJECT, projectId)` 별도 호출
  - Project 등록 흐름에서 미디어 업로드 → Project 생성 → attach 순서
  - 참조 첨부 후 삭제 방지 · 참조 해제 API는 v0.0.4+ (초기엔 참조는 영구)

## 대안 검토 (Alternatives Considered)

### 저장 백엔드
**Option A — AWS S3 + Pre-signed URL (환경별 MinIO/S3 분기) (선택)**
- 비용: SDK 의존성 · 환경별 설정
- 보상: 표준 · 확장 · 서버 대역폭 절약 · **인터뷰 스토리 최상급**

**Option B — 로컬 파일 저장 (EBS 마운트)**
- 거부 이유: EC2 재배포 시 파일 손실 · 다중 인스턴스 불가 · Prod 부적합

**Option C — Cloudinary/imgix (SaaS)**
- 거부 이유: 유료 · 오버킬 · 학생 스코프 부적합

### 업로드 방식
**Option A — 2단계 (Pre-signed 발급 + 완료 확정) (선택)**
- 이중 검증 · 안전 · Kickstarter도 유사

**Option B — 1단계 (Pre-signed URL만 · 완료 확인 없음)**
- 거부 이유: FE가 업로드 실패 시 PENDING 방치 · 정합성 검증 어려움

**Option C — 서버 프록시 업로드 (FE → 서버 → S3)**
- 거부 이유: 서버 대역폭 부담 · S3 Pre-signed의 이점 무효화

### Dimension 추출 시점
**Option A — 2단계 완료 확정 시 서버 추출 (선택)**
- FE 렌더링 시 CLS 방지 · 서버 부담 소량 (10MB 이하 이미지 파싱은 밀리초 단위)

**Option B — FE가 클라이언트 파싱해서 전달**
- 거부 이유: FE 신뢰성 낮음 · 조작 가능성

### 참조 첨부 방식
**Option A — 지연 첨부 (`attach(type, id)`) (선택)**
- 업로드→참조 순서 자연스러움 · Project·Chat 등록 흐름과 정합

**Option B — 업로드 시점에 참조 필수**
- 거부 이유: Project 생성 전 이미지 업로드 불가 · UX 저해

### Soft Delete
**Option A — Soft Delete + 30일 배치 실 삭제 (선택)**
- 실수 삭제 복구 여지 · 저작권 이슈 대응 유연

**Option B — 즉시 실 삭제**
- 거부 이유: 실수 복구 불가 · 참조 무결성 검증 어려움

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
MediaController      MediaUploadService    Media (Aggregate)         MediaRepository
- issueUploadUrl     - issueUploadUrl      - createPending()          - findByIdAndOwner
- complete           - completeUpload      - complete(width,height)   - findOrphaned (배치)
- getById            - attach              - attach(type, refId)      - findByReferenceType...
- delete             - softDelete          - softDelete(userId)       S3Client
                     - findOrphaned        - verifyOwnership()        - generatePresignedUrl
                     MediaCleanupScheduler MediaPurpose               - headObject
                     - runDaily()          - COVER,SCREENSHOT,STORY   - deleteObject
                     ImageInspector          AVATAR, CHAT_ATTACH      S3ClientConfig
                     - inspect(key)        MediaStatus                - @Profile("prod")
                                           - PENDING/COMPLETED/FAILED/DELETED  → AwsS3Client
                                           MediaReferenceType         - @Profile("dev|local")
                                           - PROJECT/USER/CHAT_MESSAGE → MinioS3Client

External refs (참조만):
- Project (Product 6) — Project.coverMediaId · screenshotMediaIds
- Chat (Product 9) — IMAGE payload imageUrl
- Maker Profile (Product 10) — 아바타 (v0.0.4+)
- Discovery (Product 13) — 원본 사이트 썸네일 캐싱 (v0.0.5+)
```

### 핵심 플로우
**1. 업로드 요청 (1단계 · Pre-signed URL 발급)**
```
FE → POST /api/v1/media/upload-url
     body: {fileName, mimeType, sizeBytes, purpose}
   → MediaUploadService.issueUploadUrl(userId, cmd)
     ├── validate: size ≤ 10MB · mime ∈ allowed
     ├── storageKey 생성: media/{yyyy/MM}/{uuid}.{ext}
     ├── Media.createPending(...) 저장 (status=PENDING)
     └── s3Client.generatePresignedUrl(storageKey, 5분)
   ← 200 {mediaId, uploadUrl, storageKey}

FE → PUT {uploadUrl} (직접 S3에 파일 업로드 · 서버 경유 X)
```

**2. 업로드 확정 (2단계 · S3 존재 확인 + dimension)**
```
FE → POST /api/v1/media/{mediaId}/complete
   → MediaUploadService.completeUpload(userId, mediaId)
     ├── Media 조회 · 소유권 검증
     ├── s3Client.headObject(storageKey)  → 실 존재 확인 · MED006 MEDIA_S3_NOT_FOUND
     ├── imageInspector.inspect(storageKey)  → width, height 추출
     └── media.complete(width, height)  → status=COMPLETED
   ← 200 {mediaId, publicUrl, width, height}
```

**3. 참조 첨부 (Project 등록 시)**
```
Project 등록 4단계 워크플로우:
  Step 1-2: 기본 정보 · 이미지 업로드 (COMPLETED)
  Step 3-4: 펀딩·리워드 · 검토 후 최종 등록

ProjectService.create (Product 6)
  ├── Project 저장 (coverMediaId 임시 저장)
  └── For each mediaId:
        MediaUploadService.attach(mediaId, PROJECT, projectId)
          → media.attach(PROJECT, projectId)
```

**4. 30일 배치 정리**
```
@Scheduled(cron = "0 0 4 * * *")   -- 매일 새벽 4시
MediaCleanupScheduler.runDaily()
  ├── PENDING 미디어 (24시간 초과) 조회 → S3 존재 시 실 삭제 · DB `status=FAILED`
  └── deleted_at 30일 초과 미디어 조회
      For each:
        - 참조 확인 · 참조 없어야 함
        - s3Client.deleteObject(storageKey)
        - DB에서 실제 DELETE (또는 archive 테이블 이관)
```

### Out-of-Process 의존
- **AWS S3** (prod) — 실 버킷 `c1oud-mall-media`
- **MinIO** (dev/local) — Docker Compose 컨테이너
- **RDS (MySQL)** — `media` 테이블
- (참조) Project · Chat · User — 각 도메인의 참조 컬럼

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 미디어 없음 | `MED001` MEDIA_NOT_FOUND | 404 | 재업로드 유도 |
| 파일 크기 초과 (10MB↑) | `MED002` MEDIA_SIZE_EXCEEDED | 400 | 파일 압축·재선택 |
| 지원 안 되는 MIME 타입 | `MED003` MEDIA_INVALID_MIME | 400 | JPG/PNG/WEBP/GIF만 |
| 상태 위반 (PENDING이 아닌데 complete 호출 등) | `MED004` MEDIA_INVALID_STATUS | 400 | 재조회 |
| complete 전 attach 시도 | `MED005` MEDIA_NOT_COMPLETED | 409 | 업로드 완료 후 재시도 |
| complete 시 S3에 실 파일 없음 (Pre-signed 발급 후 업로드 실패) | `MED006` MEDIA_S3_NOT_FOUND | 500 | 재업로드 |
| 본인 소유 아님 (삭제 시) | `MED007` MEDIA_OWNERSHIP_FAILED | 403 | 접근 거부 |
| 참조된 미디어 삭제 시도 (Project.coverMediaId 등) | `MED008` MEDIA_REFERENCE_LOCKED | 409 | Project 삭제 or 참조 해제 후 재시도 |
| 이미지 dimension 추출 실패 | (내부 로그 마커) | - | width/height 없이 저장 · 로그 마커 |
| S3 클라이언트 통신 실패 | `C002` INTERNAL_ERROR | 500 | 재시도 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `mediaId` · `ownerUserId` · `purpose` · `sizeBytes` · `mimeType` · 액션(upload_url/complete/attach/delete)
  - 배치: `MEDIA_CLEANUP_BATCH_START/END pendingCleaned={} deletedCleaned={}`
- **debug**: S3 pre-signed URL 발급 시각 · 만료 시각 · storageKey
- **절대 금지**:
  - Pre-signed URL 원문 (5분 유효라도 로그 노출 방지)
  - 이미지 원본 바이너리
- **특수 마커**:
  - `MEDIA_S3_ORPHAN storageKey={}` — DB에 없는데 S3에 존재 (배치 감지)
  - `MEDIA_DB_ORPHAN mediaId={}` — DB에 COMPLETED인데 S3에 없음 (배치 감지)
  - `MEDIA_DIMENSION_EXTRACT_FAILED mediaId={}` — 이미지 파싱 실패

### 관측 지표
- `media.upload.total{result=success|fail, purpose}` — counter — 업로드 시도 결과 분포
- `media.storage.size.gauge` — gauge — 총 저장 용량 (배치 · 주간)
- `media.orphaned.total{location=s3|db}` — counter — 배치가 감지한 orphan 수
- `media.cleanup.deleted.total` — counter — 30일 배치가 실 삭제한 수
- `media.presigned.duration_seconds` — histogram — Pre-signed URL 발급 시간

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Project (Product 6) · Chat (Product 9) 완결 · 참조 컬럼 이용 가능
- 초기 사용자 없음 · 신규 테이블만 · JPA ddl-auto
- **AWS S3 버킷 생성 필수** (별도 인프라 작업 · IAM Role 설정)
- **MinIO Docker Compose** 로컬 개발용

### Product 의존성
- **선행**: **Project (6)** · **Chat (9)** — 참조 컬럼 이용
- **후행**: **Maker Profile (10) 아바타 확장 (v0.0.4+)** · **Discovery (13) 썸네일 캐싱 (v0.0.5+)**
- **동시 대응**: `application.yml` prod/dev 프로파일에 S3 설정 추가 (Access Key · Secret · Region · Bucket)

### Epic·Story 의존성 그래프
```
Epic 1 (도메인·enum·ErrorCode) ──► Epic 2 (S3 클라이언트·환경 분기)
                                          └─► Epic 3 (2단계 업로드·attach)
                                                 └─► Epic 4 (배치·REST·관측·ADR)
```

### 환경별 설정 분기
| 항목 | dev/local | prod |
| --- | --- | --- |
| S3 백엔드 | MinIO (Docker) | AWS S3 |
| 버킷 | `c1oud-mall-dev-media` | `c1oud-mall-media` |
| Endpoint | `http://localhost:9000` | AWS 표준 |
| Pre-signed URL 유효 시간 | 30분 (테스트 편의) | 5분 |
| Public URL | Endpoint 직결 | CloudFront (v0.0.5+) |
| 배치 cron | 5분 (테스트) | 매일 04:00 |
| Cleanup 유예 기간 | 1일 (테스트) | 30일 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| Pre-signed URL 발급 응답 P95 | ≤ 500ms | `media.presigned.duration_seconds` P95 |
| 업로드 완료율 (1단계 → 2단계) | ≥ 90% | `media.upload.total{result=success}` / `media.upload.total{total}` |
| Orphan 감지 정확도 (DB vs S3 정합) | 100% | 주간 배치 검증 |
| 30일 배치 실 삭제 정확 | 참조 없는 것만 삭제 · 100% | 통합 테스트 · SQL 검증 |
| 이미지 dimension 추출 성공률 | ≥ 99% (지원 형식) | `MEDIA_DIMENSION_EXTRACT_FAILED` 발생률 |

## Scope
**In Scope**:
- 신규 컨텍스트 `nbc.c1oud_mall.media.*` (4레이어)
- `Media` Aggregate root · 3개 enum (Purpose · Status · ReferenceType)
- S3 Pre-signed URL 2단계 업로드 (`issueUploadUrl · completeUpload`)
- MinIO/S3 환경별 백엔드 분기 (`@Profile` Bean)
- 이미지 dimension 추출 (`ImageInspector`)
- 참조 첨부 (`Media.attach`)
- Soft Delete + 30일 배치 실 삭제 (`MediaCleanupScheduler`)
- REST 4개 엔드포인트 (`upload-url · complete · get · delete`)
- `ErrorCode.MED001~008`
- 검증 상수 (`MediaConstraints`)
- 관측 지표 5종
- Docker Compose에 MinIO 컨테이너 추가 (dev 인프라)

**Out of Scope**:
- **이미지 리사이징·썸네일** — CloudFront + Lambda@Edge 활용 · v0.0.5+
- **CDN (CloudFront)** — Public URL은 초기 S3 직결 · v0.0.5+
- **비디오 업로드·인코딩** — v0.0.6+ · 큰 별개 도메인
- **문서 (PDF · DOC)** — 이미지만 지원 · v0.0.5+ 확장
- **참조 해제 API** — 초기 참조는 영구 · v0.0.4+
- **미디어 갤러리 검색·태그** — v0.0.5+
- **콘텐츠 조정(Moderation)** — 부적절 이미지 자동 감지 · v0.0.5+ (AWS Rekognition 등)
- **다중 리전 복제** — 단일 리전 · v0.0.6+
- **버킷 정책 세밀 관리 (IAM Policy 최적화)** — 초기 표준 정책 · 트래픽 증가 시 재검토
- **저작권·Watermark** — 초기 미지원

## 대상 사용자
- **메이커 (Maker)** — 프로젝트 등록 시 커버·스크린샷·스토리 이미지 업로드
- **후원자 (Backer)** — 채팅 IMAGE 첨부 · 프로필 아바타 (v0.0.4+)
- **관리자** — Orphan 배치 결과 대응 · S3 비용·용량 모니터링
- **후속 SDD 작성자** — Maker Profile 아바타 · Discovery 썸네일 캐싱
- **개발/QA** — MinIO Docker 통합 테스트 · Pre-signed URL 흐름 검증

## 연결된 Epic 목록
- [ ] Epic 1: `Media` 엔티티 + 3개 enum + Repository + `ErrorCode.MED001~008` + `MediaConstraints`
- [ ] Epic 2: S3 클라이언트 (MinIO/S3 환경 분기) + Pre-signed URL 발급 + `application.yml` 설정
- [ ] Epic 3: 2단계 업로드 서비스 (`issueUploadUrl · completeUpload`) + `ImageInspector` + `attach` 흐름
- [ ] Epic 4: `MediaCleanupScheduler` (30일 배치) + REST 4개 + 관측 5개 + ADR + 규범 갱신

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-13-media-upload-gallery.md`
- **선행 SDD**: `product-project.md` (Product 6) · `product-chat.md` (Product 9)
- **후행 SDD**:
  - `product-maker.md` (Product 10) — 아바타 확장 (v0.0.4+)
  - (v0.0.5+) Discovery 썸네일 캐싱
- **관련 이슈**:
  - `issue-08-project-domain.md` — Project.coverMediaId · screenshotMediaIds
  - `issue-12-chat-rich-message.md` — IMAGE payload imageUrl
  - `issue-11-maker-profile-response-stats.md` — 아바타 (v0.0.4+)
- **디자인 참조**:
  - `프로젝트 등록.html` — uploader (드래그·드롭) · 4개 스크린샷 슬롯
  - `프로젝트 상세.html` — hero-media (16:9) + 4 thumb + story-img
  - `펀딩 탐색.html` — pcard-media (카드 커버)
- **AWS 참조**:
  - AWS SDK for Java v2 (`software.amazon.awssdk:s3`)
  - S3 Pre-signed URL 표준 · IAM PutObject/GetObject 정책
- **신규 ADR 후보**:
  - "S3 Pre-signed URL 2단계 업로드 · 서버 대역폭 절약 정책"
  - "환경별 S3 백엔드 분기 (MinIO dev · S3 prod)"
  - "Soft Delete + 30일 실 삭제 배치 정책"
  - "이미지 dimension 서버 추출 · CLS 방지"
- **규범 갱신 예정**:
  - `workflows/backend-boundary/error-codes.md` MED001~008 매핑
  - `application.yml` prod/dev 프로파일에 S3/MinIO 설정 추가
  - Docker Compose에 MinIO 서비스 추가 (개발 환경)
  - `build.gradle.kts`에 `software.amazon.awssdk:s3` · `commons-imaging` 등 의존성 추가

## 열린 질문 (Open Questions)
- **CloudFront 도입 시점** — 초기 S3 직결 · Public URL 응답 시간 · 트래픽 급증 시 v0.0.5+
- **버킷 정책 세부** — Read Public? Signed URL only? · 초기 Signed URL 방식 · 정책 검토 필요
- **삭제 유예 기간 30일 최적성** — 사용자 실수 복구 여지 · GDPR 개인정보 유예 규칙 · 조정 가능
- **이미지 최대 크기 10MB** — 크라우드 펀딩 커버는 고해상도 필요 · v0.0.5+ purpose별 상한 조정
- **비디오 지원 시점** — 프로젝트 데모 영상 요구 시 · v0.0.6+
- **콘텐츠 조정 도입** — 부적절 이미지 자동 감지 (AWS Rekognition) · v0.0.5+ 사용자 확장 시
- **참조 해제 API** — 프로젝트에서 이미지 교체 UX · 초기 미지원 · v0.0.4+
- **MinIO 대신 LocalStack** — S3 완전 호환 · 개발 환경 선택지 · MinIO 채택은 경량성 우선

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 시나리오 1: FE가 Pre-signed URL 요청 → S3 PUT → complete → Project.attach → 상세 페이지에 이미지 노출
- [ ] E2E 시나리오 2: 10MB 초과 파일 · 유효하지 않은 MIME · MED002/MED003 반환
- [ ] E2E 시나리오 3: Pre-signed 발급 후 FE가 업로드 안 함 → 24시간 후 배치가 FAILED 전이
- [ ] E2E 시나리오 4: Soft Delete 후 30일 초과 → 배치가 S3 실 삭제
- [ ] E2E 시나리오 5: Project.coverMediaId 참조된 미디어 삭제 시도 → MED008
- [ ] MinIO Docker Compose로 로컬 개발 환경 검증 · MinIO 없어도 dev 프로파일 부팅 가능
- [ ] AWS S3 실 버킷 접근 검증 (통합 테스트 or 수동 검증)
- [ ] Orphan 배치 SSOT 검증 (`s3.listObjects()` vs DB) 정확도
- [ ] ADR 최소 3건 발행
- [ ] `backend-boundary/error-codes.md` MED001~008 매핑 완료
- [ ] 관측 지표 5개 프로덕션 노출

---

# [Epic 1] `Media` 엔티티 + 3개 enum + Repository + `ErrorCode.MED001~008` + `MediaConstraints`

## 목표
`Media` Aggregate root · 3개 enum · Repository · 8개 ErrorCode · 검증 상수 클래스를 구축하여 후속 Epic이 안정적으로 S3 인프라·업로드·배치를 확장할 수 있는 기반을 마련한다.

## 배경
- 이미지 자산 통합 관리 도메인 · 참조 무결성·상태·소유권 강제 필요
- 3개 enum으로 타입 안전 · 8개 ErrorCode로 명확한 응답
- 검증 상수는 상수 클래스로 통합 관리

## 포함 Story
- Story 1-1: `Media` 엔티티 + 3개 enum (`MediaPurpose · MediaStatus · MediaReferenceType`) + Repository
- Story 1-2: 5개 도메인 메서드 (`createPending · complete · attach · softDelete · verifyOwnership`)
- Story 1-3: `MediaConstraints` 상수 클래스 + `ErrorCode.MED001~008`

## Epic 인수 시나리오
- Given 유효 파라미터 · When `Media.createPending(userId, key, ...)` · Then `status=PENDING`
- Given PENDING · When `complete(1920, 1080)` · Then `status=COMPLETED · width=1920 · height=1080`
- *(예외)* Given COMPLETED · When `complete` · Then `MED004`

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 도메인 메서드 단위 테스트 매트릭스
- [ ] Repository 슬라이스 테스트
- [ ] `ErrorCode` 등록 확인

---

## [Story 1-1] `Media` 엔티티 + 3개 enum + Repository

### User Story
- As a Media 도메인 개발자
- I want `Media` Aggregate root · 3개 enum · Repository 기본 CRUD
- so that 서비스 계층이 미디어 저장·조회·상태 관리

### 설명
- 위치: `nbc.c1oud_mall.media.domain.Media`
- 필드: id · ownerUserId · storageKey · originalName · mimeType · sizeBytes · width · height · purpose · status · referenceType · referenceId · deletedAt
- Soft Delete: `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`

**주요 스키마**:
```sql
CREATE TABLE media (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  owner_user_id   BIGINT       NOT NULL,
  storage_key     VARCHAR(500) NOT NULL,
  original_name   VARCHAR(200) NOT NULL,
  mime_type       VARCHAR(100) NOT NULL,
  size_bytes      BIGINT       NOT NULL,
  width           INT          NULL,
  height          INT          NULL,
  purpose         VARCHAR(30)  NOT NULL,           -- COVER · SCREENSHOT · STORY · AVATAR · CHAT_ATTACH
  status          VARCHAR(20)  NOT NULL,           -- PENDING · COMPLETED · FAILED · DELETED
  reference_type  VARCHAR(30)  NULL,               -- PROJECT · USER · CHAT_MESSAGE
  reference_id    BIGINT       NULL,
  deleted_at      TIMESTAMP    NULL,
  created_at      TIMESTAMP    NOT NULL,
  updated_at      TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_media_storage (storage_key),
  KEY idx_media_owner (owner_user_id, created_at DESC),
  KEY idx_media_ref (reference_type, reference_id, purpose),
  KEY idx_media_status (status, created_at),
  KEY idx_media_deleted (deleted_at)
);
```

**엔티티**:
```java
@Entity
@Table(name = "media")
@SQLDelete(sql = "UPDATE media SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Media extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(unique = true, nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 200)
    private String originalName;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column
    private Integer width;
    @Column
    private Integer height;

    @Enumerated(STRING)
    @Column(nullable = false, length = 30)
    private MediaPurpose purpose;

    @Enumerated(STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    @Enumerated(STRING)
    @Column(length = 30)
    private MediaReferenceType referenceType;

    @Column
    private Long referenceId;

    @Column
    private LocalDateTime deletedAt;
}

public enum MediaPurpose { COVER, SCREENSHOT, STORY, AVATAR, CHAT_ATTACH }
public enum MediaStatus { PENDING, COMPLETED, FAILED, DELETED }
public enum MediaReferenceType { PROJECT, USER, CHAT_MESSAGE }
```

**Repository**:
```java
public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByIdAndOwnerUserId(Long id, Long ownerUserId);
    List<Media> findByReferenceTypeAndReferenceId(MediaReferenceType type, Long refId);

    // 배치용 (native or JPQL - Soft Delete 필터 우회)
    @Query(value = "SELECT * FROM media WHERE status = 'PENDING' AND created_at < :threshold", nativeQuery = true)
    List<Media> findStalePending(@Param("threshold") LocalDateTime threshold);

    @Query(value = "SELECT * FROM media WHERE deleted_at IS NOT NULL AND deleted_at < :threshold", nativeQuery = true)
    List<Media> findSoftDeletedBefore(@Param("threshold") LocalDateTime threshold);
}
```

### 완료 기준 (AC)
- Given `Media.createPending(...)` · When 저장 · Then status=PENDING 저장
- Given Soft Delete · When 재조회 · Then `@SQLRestriction` 자동 필터로 Optional.empty()
- Given native 조회 · Then Soft Delete 필터 우회하여 조회 가능

### Definition of Done
- [ ] `Media` 엔티티 + 3개 enum
- [ ] `MediaRepository` 인터페이스 · 3개 기본 쿼리
- [ ] 배치용 native 쿼리 · Soft Delete 필터 우회
- [ ] DDL 확인 (JPA · dev H2)
- [ ] `@DataJpaTest` 슬라이스

### 스토리 포인트
1d

### 의존성
- 선행: 없음
- 후행: Story 1-2 · Epic 2·3

---

## [Story 1-2] 5개 도메인 메서드

### User Story
- As a Media 도메인 개발자
- I want 5개 도메인 메서드로 상태·소유권·참조 원자적 관리
- so that Service가 안전하게 조합

### 설명
- 5개 메서드:
  - `createPending(ownerId, storageKey, originalName, mimeType, sizeBytes, purpose)` — 정적 팩토리
  - `complete(width, height)` — PENDING → COMPLETED
  - `attach(referenceType, referenceId)` — COMPLETED만 · 참조 첨부
  - `softDelete(userId)` — 소유권 검증 · Soft Delete
  - `verifyOwnership(userId)` — 위반 시 MED007

**주요 메서드**:
```java
public static Media createPending(Long ownerId, String storageKey, String originalName,
                                    String mimeType, long size, MediaPurpose purpose) {
    Media m = new Media();
    m.ownerUserId = ownerId;
    m.storageKey = storageKey;
    m.originalName = originalName;
    m.mimeType = mimeType;
    m.sizeBytes = size;
    m.purpose = purpose;
    m.status = MediaStatus.PENDING;
    return m;
}

public void complete(Integer width, Integer height) {
    if (status != MediaStatus.PENDING)
        throw new BusinessException(ErrorCode.MED004);
    this.status = MediaStatus.COMPLETED;
    this.width = width;
    this.height = height;
}

public void markFailed() {
    if (status != MediaStatus.PENDING)
        throw new BusinessException(ErrorCode.MED004);
    this.status = MediaStatus.FAILED;
}

public void attach(MediaReferenceType type, Long refId) {
    if (status != MediaStatus.COMPLETED)
        throw new BusinessException(ErrorCode.MED005);
    this.referenceType = type;
    this.referenceId = refId;
}

public void softDelete(Long userId) {
    verifyOwnership(userId);
    if (referenceType != null) {
        throw new BusinessException(ErrorCode.MED008);
    }
    // JPA @SQLDelete 트리거로 삭제 (Repository.delete(this))
}

public void verifyOwnership(Long userId) {
    if (!Objects.equals(this.ownerUserId, userId))
        throw new BusinessException(ErrorCode.MED007);
}

public boolean isCompleted() { return status == MediaStatus.COMPLETED; }
public boolean isReferenced() { return referenceType != null; }
```

### 완료 기준 (AC)
- Given PENDING · When `complete(1920, 1080)` · Then COMPLETED · width/height 설정
- *(예외)* Given COMPLETED · When `complete` · Then MED004
- Given COMPLETED · When `attach(PROJECT, 100)` · Then referenceType/referenceId 설정
- *(예외)* Given PENDING · When `attach` · Then MED005
- Given 본인 소유 · 참조 없음 · When `softDelete` · Then verifyOwnership 통과 · MED008 예외 없음
- *(예외)* Given 참조된 미디어 · When `softDelete` · Then MED008

### Definition of Done
- [ ] 6개 도메인 메서드 (5 + `markFailed` 추가)
- [ ] 뷰 메서드 2개 (`isCompleted · isReferenced`)
- [ ] 단위 테스트: 성공·상태 위반·참조 잠금·소유권 위반 각 케이스

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1
- 후행: Epic 2·3

---

## [Story 1-3] `MediaConstraints` + `ErrorCode.MED001~008`

### User Story
- As a Media 서비스
- I want 검증 상수 클래스 · 8개 ErrorCode 등록
- so that 상수 중앙 관리 · 일관된 예외 응답

### 설명
- `MediaConstraints`:
  ```java
  public final class MediaConstraints {
      public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;   // 10MB
      public static final Set<String> ALLOWED_MIMES = Set.of(
          "image/jpeg", "image/png", "image/webp", "image/gif"
      );
      public static final int MAX_SCREENSHOT_COUNT = 4;
      public static final int MAX_STORY_COUNT = 20;
      public static final Duration UPLOAD_URL_TTL_DEV = Duration.ofMinutes(30);
      public static final Duration UPLOAD_URL_TTL_PROD = Duration.ofMinutes(5);
      public static final Duration PENDING_STALE_THRESHOLD = Duration.ofHours(24);
      public static final Duration SOFT_DELETE_RETENTION = Duration.ofDays(30);
  }
  ```
- ErrorCode 8개:
  - `MED001` MEDIA_NOT_FOUND (404)
  - `MED002` MEDIA_SIZE_EXCEEDED (400)
  - `MED003` MEDIA_INVALID_MIME (400)
  - `MED004` MEDIA_INVALID_STATUS (400)
  - `MED005` MEDIA_NOT_COMPLETED (409)
  - `MED006` MEDIA_S3_NOT_FOUND (500)
  - `MED007` MEDIA_OWNERSHIP_FAILED (403)
  - `MED008` MEDIA_REFERENCE_LOCKED (409)

### 완료 기준 (AC)
- Given ErrorCode 등록 · When `errorCode.getCode()` · Then "MED001" ~ "MED008"
- Given `MediaConstraints` · When 참조 · Then 상수 값 정확

### Definition of Done
- [ ] `MediaConstraints` 상수 클래스
- [ ] `ErrorCode` enum에 8개 추가 (Media 섹션 주석 구분선)
- [ ] 단위 테스트: 상수 참조 · ErrorCode 매핑

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1·1-2
- 후행: Epic 2·3·4

---

# [Epic 2] S3 클라이언트 (MinIO/S3 환경 분기) + Pre-signed URL 발급

## 목표
AWS SDK for Java v2 기반 S3 클라이언트를 도입하고, `@Profile` 기반으로 dev/local(MinIO)과 prod(AWS S3)를 분기하며, Pre-signed URL 발급·객체 확인·삭제 API를 제공하여 후속 Epic이 도메인 흐름에 집중할 수 있게 한다.

## 배경
- AWS SDK v2 채택 (v1 대비 성능 · 유지보수 우수)
- MinIO 로컬 개발용 · Docker Compose 통합
- Pre-signed URL은 PUT 전용 (업로드용) · 조회는 Public URL

## 포함 Story
- Story 2-1: AWS SDK 의존성 추가 + `S3ClientConfig` (환경 분기 Bean)
- Story 2-2: `S3Client` 래퍼 (`generatePresignedUrl · headObject · deleteObject`)
- Story 2-3: `application.yml` 설정 + MinIO Docker Compose 추가

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] dev 프로파일: MinIO 로컬 컨테이너에서 업로드 검증
- [ ] prod 프로파일: 실 AWS S3 버킷 접근 검증
- [ ] Pre-signed URL 발급 응답 시간 P95 ≤ 500ms

---

## [Story 2-1] AWS SDK 의존성 + `S3ClientConfig`

### User Story
- As a c1oud-mall 인프라 담당
- I want AWS SDK v2 · `S3Client` Bean 환경별 분기
- so that dev(MinIO)와 prod(S3) 자동 전환

### 설명
- `build.gradle.kts`에 의존성 추가:
  ```kotlin
  implementation("software.amazon.awssdk:s3:2.24.0")
  implementation("software.amazon.awssdk:s3-transfer-manager:2.24.0")
  ```
- `S3ClientConfig`:
  - `@Profile("prod")` — AWS S3 (region + credentials chain)
  - `@Profile({"dev", "local"})` — MinIO (endpoint override + basic credentials)

**핵심 파일**:
- `nbc.c1oud_mall.media.infrastructure.S3ClientConfig`

**주요 클래스**:
```java
@Configuration
@RequiredArgsConstructor
public class S3ClientConfig {

    @Bean
    @Profile("prod")
    public software.amazon.awssdk.services.s3.S3Client awsS3Client(
            @Value("${c1oudmall.media.s3.region}") String region) {
        return software.amazon.awssdk.services.s3.S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    @Profile({"dev", "local"})
    public software.amazon.awssdk.services.s3.S3Client minioClient(
            @Value("${c1oudmall.media.s3.endpoint}") String endpoint,
            @Value("${c1oudmall.media.s3.access-key}") String accessKey,
            @Value("${c1oudmall.media.s3.secret-key}") String secretKey) {
        return software.amazon.awssdk.services.s3.S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)   // MinIO에서는 임의 region OK
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)   // MinIO는 path-style 필수
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(software.amazon.awssdk.services.s3.S3Client s3Client) {
        return S3Presigner.builder()
            .s3Client(s3Client)
            .build();
    }
}
```

### 완료 기준 (AC)
- Given prod 프로파일 · When 부팅 · Then AWS S3 Client 생성
- Given dev 프로파일 · When 부팅 · Then MinIO Client 생성
- Given 두 프로파일 동시 활성 · Then 우선순위 결정 (prod 우선)

### Definition of Done
- [ ] `build.gradle.kts` 의존성 추가
- [ ] `S3ClientConfig` 구현
- [ ] 통합 테스트: dev 프로파일 부팅 검증
- [ ] `application-*.yml`에 S3 설정 항목 정의 (Story 2-3)

### 스토리 포인트
1d

### 의존성
- 선행: 없음
- 후행: Story 2-2

---

## [Story 2-2] `S3Client` 래퍼 (`generatePresignedUrl · headObject · deleteObject`)

### User Story
- As a Media 서비스
- I want S3 SDK를 감싸는 얇은 래퍼 서비스
- so that 도메인 로직이 SDK 세부에 의존하지 않음

### 설명
- 3개 메서드:
  - `generatePresignedPutUrl(storageKey, ttl)` — PUT용 Pre-signed URL
  - `headObject(storageKey)` — S3에 파일 존재 확인 · `Optional<S3ObjectMetadata>` 반환
  - `deleteObject(storageKey)` — 실 S3 파일 삭제

**핵심 파일**:
- `nbc.c1oud_mall.media.infrastructure.MediaStorageClient` (인터페이스)
- `nbc.c1oud_mall.media.infrastructure.S3MediaStorageClient` (구현)

**주요 클래스**:
```java
public interface MediaStorageClient {
    String generatePresignedPutUrl(String storageKey, String mimeType, Duration ttl);
    Optional<S3ObjectMetadata> headObject(String storageKey);
    void deleteObject(String storageKey);
    String publicUrl(String storageKey);
}

@Component
@RequiredArgsConstructor
@Slf4j
public class S3MediaStorageClient implements MediaStorageClient {
    private final software.amazon.awssdk.services.s3.S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${c1oudmall.media.s3.bucket}")
    private String bucket;

    @Value("${c1oudmall.media.s3.public-base-url:}")
    private String publicBaseUrl;   // 옵션 · CloudFront 도입 시

    @Override
    public String generatePresignedPutUrl(String storageKey, String mimeType, Duration ttl) {
        PutObjectRequest putReq = PutObjectRequest.builder()
            .bucket(bucket)
            .key(storageKey)
            .contentType(mimeType)
            .build();
        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(putReq)
            .build();
        return presigner.presignPutObject(presignReq).url().toString();
    }

    @Override
    public Optional<S3ObjectMetadata> headObject(String storageKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());
            return Optional.of(new S3ObjectMetadata(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteObject(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket).key(storageKey).build());
    }

    @Override
    public String publicUrl(String storageKey) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank())
            return publicBaseUrl + "/" + storageKey;
        return s3Client.utilities().getUrl(GetUrlRequest.builder()
            .bucket(bucket).key(storageKey).build()).toExternalForm();
    }

    public record S3ObjectMetadata(long sizeBytes, String contentType) {}
}
```

### 완료 기준 (AC)
- Given 유효 storageKey · mimeType · TTL · When `generatePresignedPutUrl` · Then URL 문자열 반환
- Given S3에 파일 존재 · When `headObject` · Then Optional non-empty
- Given S3에 파일 없음 · When `headObject` · Then Optional.empty()
- Given `deleteObject(key)` · Then S3에서 실 삭제 · 재조회 Optional.empty()

### Definition of Done
- [ ] `MediaStorageClient` 인터페이스 + `S3MediaStorageClient` 구현
- [ ] `S3ObjectMetadata` record
- [ ] 통합 테스트: MinIO 컨테이너 활용 · 실 업로드·조회·삭제 왕복
- [ ] Pre-signed URL 발급 응답 시간 관측 (Timer)

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 2-1
- 후행: Epic 3

---

## [Story 2-3] `application.yml` 설정 + MinIO Docker Compose

### User Story
- As a c1oud-mall 인프라 담당
- I want 환경별 S3 설정 · MinIO 로컬 실행 환경
- so that dev·prod 모두 즉시 부팅 가능

### 설명
- `application-prod.yml`:
  ```yaml
  c1oudmall:
    media:
      s3:
        region: ap-northeast-2
        bucket: c1oud-mall-media
        public-base-url: ""   # CloudFront 도입 시 채움
  ```
- `application-dev.yml` / `application-local.yml`:
  ```yaml
  c1oudmall:
    media:
      s3:
        endpoint: http://localhost:9000
        access-key: minioadmin
        secret-key: minioadmin
        bucket: c1oud-mall-dev-media
        public-base-url: http://localhost:9000/c1oud-mall-dev-media
  ```
- `docker-compose.yml`에 MinIO 서비스 추가:
  ```yaml
  services:
    minio:
      image: minio/minio:latest
      ports:
        - "9000:9000"
        - "9001:9001"
      environment:
        MINIO_ROOT_USER: minioadmin
        MINIO_ROOT_PASSWORD: minioadmin
      command: server /data --console-address ":9001"
      volumes:
        - minio-data:/data
  volumes:
    minio-data:
  ```
- 부팅 시 버킷 자동 생성 (`ApplicationRunner` or MinIO init 스크립트)

### 완료 기준 (AC)
- Given dev 프로파일 · MinIO 실행 · When `./gradlew bootRun` · Then 앱 부팅 · MinIO 접근 가능
- Given prod 프로파일 · AWS 자격증명 세팅 · When 부팅 · Then S3 클라이언트 초기화
- Given MinIO 콘솔 접근 · `http://localhost:9001` · Then 관리자 로그인 · 버킷 확인

### Definition of Done
- [ ] `application-prod.yml` · `application-dev.yml` · `application-local.yml` 설정
- [ ] `docker-compose.yml` MinIO 서비스
- [ ] 버킷 자동 생성 (부팅 시)
- [ ] README에 로컬 실행 가이드 추가

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1·2-2
- 후행: Epic 3·4

---

# [Epic 3] 2단계 업로드 서비스 + `ImageInspector` + `attach` 흐름

## 목표
`MediaUploadService`를 도입하여 2단계 업로드(Pre-signed URL 발급 + 완료 확정)를 제공하고, `ImageInspector`로 dimension을 서버 추출하며, `attach` 흐름으로 Project/Chat/User와의 결합을 성립시킨다.

## 포함 Story
- Story 3-1: `MediaUploadService.issueUploadUrl` (1단계 · Pre-signed URL 발급)
- Story 3-2: `ImageInspector` (BufferedImage 기반 width/height 추출)
- Story 3-3: `MediaUploadService.completeUpload` (2단계 · S3 확인 + dimension) + `attach` 메서드

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] E2E: FE → Pre-signed URL → S3 PUT → complete → attach 흐름 검증
- [ ] Dimension 추출 성공률 99%

---

## [Story 3-1] `MediaUploadService.issueUploadUrl`

### User Story
- As a FE 개발자
- I want 파일 정보를 서버에 전달하여 Pre-signed URL 발급받음
- so that S3에 직접 업로드 · 서버 대역폭 절약

### 설명
- Request 유효성 검증 (size ≤ 10MB · mime allowed)
- `storageKey` 생성: `media/{yyyy/MM}/{uuid}.{ext}`
- `Media.createPending` 저장
- `s3Client.generatePresignedPutUrl(key, mime, TTL)` 발급
- Response: `{mediaId, uploadUrl, storageKey, expiresAt}`

**주요 메서드**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class MediaUploadService {
    private final MediaRepository mediaRepository;
    private final MediaStorageClient storageClient;
    private final MeterRegistry meterRegistry;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public UploadUrlResponse issueUploadUrl(Long userId, UploadUrlCommand cmd) {
        long start = System.currentTimeMillis();

        // 검증
        if (cmd.sizeBytes() > MediaConstraints.MAX_SIZE_BYTES)
            throw new BusinessException(ErrorCode.MED002);
        if (!MediaConstraints.ALLOWED_MIMES.contains(cmd.mimeType()))
            throw new BusinessException(ErrorCode.MED003);

        // storage key 생성
        String storageKey = generateStorageKey(cmd);

        // Media PENDING 저장
        Media media = Media.createPending(userId, storageKey, cmd.originalName(),
                                            cmd.mimeType(), cmd.sizeBytes(), cmd.purpose());
        mediaRepository.save(media);

        // Pre-signed URL 발급
        Duration ttl = isDev() ? MediaConstraints.UPLOAD_URL_TTL_DEV
                               : MediaConstraints.UPLOAD_URL_TTL_PROD;
        String url = storageClient.generatePresignedPutUrl(storageKey, cmd.mimeType(), ttl);

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("media.presigned.duration_seconds").record(duration, MILLISECONDS);
        meterRegistry.counter("media.upload.total", "result", "presigned", "purpose", cmd.purpose().name())
                     .increment();

        return new UploadUrlResponse(media.getId(), url, storageKey,
                                      LocalDateTime.now().plus(ttl));
    }

    private String generateStorageKey(UploadUrlCommand cmd) {
        String ext = extractExtension(cmd.mimeType());   // "image/jpeg" → "jpg"
        String yearMonth = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now());
        return "media/" + yearMonth + "/" + UUID.randomUUID() + "." + ext;
    }

    private boolean isDev() {
        return activeProfile.contains("dev") || activeProfile.contains("local");
    }
}
```

### 완료 기준 (AC)
- Given size=5MB · mime=jpeg · purpose=COVER · When 요청 · Then 200 · uploadUrl 반환 · Media PENDING 저장
- *(예외)* size=15MB · Then 400 · MED002
- *(예외)* mime=application/pdf · Then 400 · MED003

### Definition of Done
- [ ] `MediaUploadService.issueUploadUrl`
- [ ] `UploadUrlCommand · UploadUrlResponse` (record)
- [ ] 검증 로직 · storage key 생성기
- [ ] 통합 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1·2
- 후행: Story 3-2·3-3

---

## [Story 3-2] `ImageInspector` (BufferedImage 기반)

### User Story
- As a Media 서비스
- I want S3 파일에서 이미지 width/height 추출
- so that FE 렌더링 시 CLS 방지

### 설명
- S3에서 파일 다운로드 (또는 InputStream) → `ImageIO.read(inputStream)` → BufferedImage
- 실패 시 로그 마커 · Optional.empty() 반환 (예외 X)
- 지원 형식: JPG · PNG · WEBP (WEBP는 Twelvemonkeys 라이브러리 필요 검토) · GIF

**핵심 파일**:
- `nbc.c1oud_mall.media.infrastructure.ImageInspector`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageInspector {
    private final software.amazon.awssdk.services.s3.S3Client s3Client;

    @Value("${c1oudmall.media.s3.bucket}")
    private String bucket;

    public Optional<Dimensions> inspect(String storageKey) {
        try (ResponseInputStream<GetObjectResponse> stream =
                s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(storageKey).build())) {
            BufferedImage img = ImageIO.read(stream);
            if (img == null) {
                log.warn("MEDIA_DIMENSION_EXTRACT_FAILED storageKey={} reason=null_image", storageKey);
                return Optional.empty();
            }
            return Optional.of(new Dimensions(img.getWidth(), img.getHeight()));
        } catch (Exception e) {
            log.warn("MEDIA_DIMENSION_EXTRACT_FAILED storageKey={}", storageKey, e);
            return Optional.empty();
        }
    }

    public record Dimensions(int width, int height) {}
}
```

### 완료 기준 (AC)
- Given JPG 1920x1080 · When `inspect` · Then `Dimensions(1920, 1080)`
- Given 유효하지 않은 이미지 · Then Optional.empty() + 로그 마커
- Given S3 조회 실패 · Then Optional.empty()

### Definition of Done
- [ ] `ImageInspector` 구현
- [ ] 단위 테스트: 정상 · 실패 케이스
- [ ] 로그 마커 검증

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: Story 3-3

---

## [Story 3-3] `MediaUploadService.completeUpload` + `attach`

### User Story
- As a FE 개발자 · Project 서비스
- I want 업로드 완료 확정 · 참조 첨부 API
- so that 정합성 있는 미디어 관리

### 설명
- `completeUpload(userId, mediaId)`:
  1. Media 조회 (본인 소유)
  2. `storageClient.headObject()` → 실 존재 확인 · 없으면 MED006
  3. `imageInspector.inspect()` → width/height (실패 시 null)
  4. `media.complete(width, height)`
- `attach(userId, mediaId, refType, refId)`:
  1. Media 조회 · 소유권 검증
  2. `media.attach(refType, refId)` → COMPLETED 아니면 MED005

**주요 메서드**:
```java
@Transactional
public MediaResponse completeUpload(Long userId, Long mediaId) {
    Media media = mediaRepository.findByIdAndOwnerUserId(mediaId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MED001));

    // S3 존재 확인
    S3ObjectMetadata metadata = storageClient.headObject(media.getStorageKey())
            .orElseThrow(() -> {
                log.error("MEDIA_S3_NOT_FOUND mediaId={} key={}", mediaId, media.getStorageKey());
                media.markFailed();
                return new BusinessException(ErrorCode.MED006);
            });

    // Dimension 추출 (실패 허용)
    Dimensions dims = imageInspector.inspect(media.getStorageKey())
            .orElse(new Dimensions(null, null));

    media.complete(dims.width(), dims.height());

    meterRegistry.counter("media.upload.total", "result", "completed",
                          "purpose", media.getPurpose().name()).increment();

    return MediaResponse.from(media, storageClient.publicUrl(media.getStorageKey()));
}

@Transactional
public void attach(Long userId, Long mediaId, MediaReferenceType refType, Long refId) {
    Media media = mediaRepository.findByIdAndOwnerUserId(mediaId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MED001));
    media.attach(refType, refId);
}
```

### 완료 기준 (AC)
- Given PENDING · S3에 파일 존재 · When `completeUpload` · Then COMPLETED · width/height 설정 · publicUrl 반환
- *(예외)* Given PENDING · S3에 파일 없음 · When `completeUpload` · Then MED006 · Media.markFailed
- Given COMPLETED · When `attach(PROJECT, 100)` · Then referenceType/referenceId 설정
- *(예외)* Given PENDING · When `attach` · Then MED005

### Definition of Done
- [ ] `MediaUploadService.completeUpload · attach`
- [ ] `MediaResponse` record · `from(media, publicUrl)` 팩토리
- [ ] 통합 테스트: 정상 · S3 없음 · 상태 위반

### 스토리 포인트
1d

### 의존성
- 선행: Story 3-1·3-2
- 후행: Epic 4

---

# [Epic 4] `MediaCleanupScheduler` + REST 4개 + 관측 + ADR

## 목표
30일 배치 정리 · 24시간 초과 PENDING 정리 · REST 4개 · 5개 관측 지표 · 3건 ADR로 미디어 도메인 완결.

## 포함 Story
- Story 4-1: `MediaCleanupScheduler` (매일 04:00 · PENDING stale + Soft Delete 30일)
- Story 4-2: `MediaController` REST 4개 + 관측 지표 4개 추가 (Timer는 이미 등록)
- Story 4-3: ADR 3건 + `backend-boundary/error-codes.md` MED001~008 매핑

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 4개 REST 엔드포인트 · 5개 지표
- [ ] ADR 3건 발행
- [ ] 규범 갱신

---

## [Story 4-1] `MediaCleanupScheduler`

### User Story
- As a 운영자
- I want 매일 새벽 4시 stale PENDING 정리 · 30일 초과 Soft Delete 실 삭제
- so that 저장 공간 절약 · 정합성 유지

### 설명
- `@Scheduled(cron = "0 0 4 * * *")`
- 2가지 작업:
  1. PENDING 24시간 초과 → S3 존재 확인 후 실 삭제 · DB status=FAILED
  2. Soft Delete 30일 초과 → 참조 확인 후 S3 실 삭제 · DB 실제 DELETE

**핵심 파일**:
- `nbc.c1oud_mall.media.infrastructure.MediaCleanupScheduler`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupScheduler {
    private final MediaRepository mediaRepository;
    private final MediaStorageClient storageClient;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 4 * * *")
    public void runDaily() {
        cleanStalePending();
        cleanSoftDeleted();
    }

    protected void cleanStalePending() {
        LocalDateTime threshold = LocalDateTime.now()
                .minus(MediaConstraints.PENDING_STALE_THRESHOLD);
        List<Media> stales = mediaRepository.findStalePending(threshold);
        long cleaned = 0;
        for (Media media : stales) {
            try {
                if (storageClient.headObject(media.getStorageKey()).isPresent()) {
                    storageClient.deleteObject(media.getStorageKey());
                }
                media.markFailed();   // DB 상태 갱신
                cleaned++;
            } catch (Exception e) {
                log.error("MEDIA_CLEANUP_STALE_FAILED mediaId={}", media.getId(), e);
            }
        }
        log.info("MEDIA_CLEANUP_STALE_DONE total={} cleaned={}", stales.size(), cleaned);
    }

    protected void cleanSoftDeleted() {
        LocalDateTime threshold = LocalDateTime.now()
                .minus(MediaConstraints.SOFT_DELETE_RETENTION);
        List<Media> softDeleted = mediaRepository.findSoftDeletedBefore(threshold);
        long cleaned = 0;
        for (Media media : softDeleted) {
            try {
                storageClient.deleteObject(media.getStorageKey());
                mediaRepository.deleteByIdReal(media.getId());   // native DELETE (Soft Delete 필터 우회)
                cleaned++;
            } catch (Exception e) {
                log.error("MEDIA_CLEANUP_DELETED_FAILED mediaId={}", media.getId(), e);
            }
        }
        meterRegistry.counter("media.cleanup.deleted.total").increment(cleaned);
        log.info("MEDIA_CLEANUP_DELETED_DONE total={} cleaned={}", softDeleted.size(), cleaned);
    }
}
```

### 완료 기준 (AC)
- Given PENDING 24시간 초과 5건 · When 배치 · Then 5건 → FAILED · S3 삭제
- Given deleted_at 30일 초과 3건 · When 배치 · Then 3건 S3 삭제 · DB 실제 DELETE
- *(엣지)* Given 참조된 미디어 (deleted_at != null · reference_id != null) · Then 삭제 방지 (스코프 밖 · 시스템 이상 로그 마커)

### Definition of Done
- [ ] `MediaCleanupScheduler`
- [ ] `MediaRepository.deleteByIdReal` native DELETE
- [ ] 통합 테스트: 정상 · 부분 실패 격리
- [ ] 관측: `media.cleanup.deleted.total` counter

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 1·2·3
- 후행: 없음

---

## [Story 4-2] `MediaController` REST 4개

### User Story
- As a FE 개발자
- I want 업로드 URL 발급 · 완료 · 조회 · 삭제 API
- so that 업로드 흐름 완결

### 설명
- 4개 엔드포인트:
  - `POST /api/v1/media/upload-url` · Request `{fileName, mimeType, sizeBytes, purpose}` · Response `{mediaId, uploadUrl, storageKey, expiresAt}`
  - `POST /api/v1/media/{mediaId}/complete` · Response `{mediaId, publicUrl, width, height}`
  - `GET /api/v1/media/{mediaId}` · Response `MediaResponse`
  - `DELETE /api/v1/media/{mediaId}` · 204 No Content · 본인만

**Response**:
- `MediaResponse` (record) — id · publicUrl · originalName · mimeType · sizeBytes · width · height · purpose · status · referenceType · referenceId

### 완료 기준 (AC)
- Given 인증 · 유효 request · When `POST /upload-url` · Then 200 · `UploadUrlResponse`
- Given PENDING · When `POST /complete` · Then 200 · publicUrl
- Given 본인 · When `DELETE` · Then 204
- *(예외)* Given 참조된 미디어 · When `DELETE` · Then 409 · MED008

### Definition of Done
- [ ] `MediaController` 4개 엔드포인트
- [ ] Request/Response DTO
- [ ] `@WebMvcTest` 슬라이스

### 스토리 포인트
1d

### 의존성
- 선행: Epic 3
- 후행: 없음

---

## [Story 4-3] ADR 3건 + 규범 갱신

### User Story
- As a 팀 리더
- I want ADR 3건 + 규범 갱신
- so that 미디어 도메인 정책 확립

### 설명
- ADR 3건:
  - `023-media-s3-presigned-2step-upload.md`
  - `024-media-multi-environment-backend-minio-s3.md`
  - `025-media-soft-delete-and-cleanup-batch.md`
- 규범:
  - `workflows/backend-boundary/error-codes.md` MED001~008 UX 매핑
  - `application.yml` prod/dev 미디어 설정 항목 문서화
  - `docker-compose.yml` MinIO 서비스 추가 (실 코드는 Epic 2 Story 2-3)

### 완료 기준 (AC)
- Given ADR 3건 · When 확인 · Then 완결
- Given `backend-boundary/error-codes.md` · Then MED001~008 매핑

### Definition of Done
- [ ] ADR 3건 파일
- [ ] `backend-boundary/error-codes.md` MED 섹션 추가
- [ ] `application-*.yml` 문서화

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1~3 완료
- 후행: 없음

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: 도메인·enum·ErrorCode | 3 | 2.5 |
| Epic 2: S3 클라이언트·환경 분기 | 3 | 3.5 |
| Epic 3: 2단계 업로드·dimension·attach | 3 | 2.5 |
| Epic 4: 배치·REST·관측·ADR | 3 | 3.0 |
| **합계** | **12** | **11.5 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 도메인 · 뒤 Epic 전제
- Epic 2 완료 후 Epic 3·4 병렬 가능 (일부)
- Epic 4는 마지막 (규범 굳힘)

## 미디어 도메인 완결 → 후속 참조 준비

본 Product 11(Media) 완결 시 c1oud-mall의 모든 이미지 자산 관리 기반 성립. 다음 Product·이슈들이 참조:
- **Project (Product 6)**: coverMediaId · screenshotMediaIds 실 참조 활성
- **Chat (Product 9)**: IMAGE MessageType payload imageUrl 실 참조
- **Maker Profile (Product 10)**: 아바타 확장 (v0.0.4+)
- **Discovery (Product 13)**: 원본 사이트 썸네일 캐싱 (v0.0.5+)
- **Notification (v0.0.5+)**: 알림 이미지 첨부
