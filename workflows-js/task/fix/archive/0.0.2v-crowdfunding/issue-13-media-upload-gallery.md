# Issue: Media Upload · Gallery (이미지 업로드 · 갤러리) 도메인 신설

## 배경

> **디자인 발견 (Round 5)**: 프로젝트 상세·등록·탐색 페이지에 커버 + 스크린샷 갤러리 UI.

- 디자인 확인 요소:
  - **프로젝트 커버 이미지** (16:9 · 상세·탐색 카드)
  - **스크린샷 갤러리** (4장 · 상세 페이지 · 각 16:10)
  - **스토리 이미지** (에디터 화면 · 아키텍처 다이어그램 등 · 스크롤 중간)
  - **프로필 이미지** (메이커 아바타 · 텍스트 이니셜 fallback)
  - **채팅 첨부 이미지** (이슈 #12 IMAGE 메시지 타입)
- 현재 c1oud-mall은 이미지 업로드 로직 부재 → **미디어 도메인 신설 필수**
- 배송·재고와 무관 · 크라우드 펀딩 UX 필수 요소

## 조사 결과 — 벤치마크

| 저장 방식 | 특징 | 신입 스코프 |
|---|---|---|
| **AWS S3 + Pre-signed URL** | 표준 · 확장 · 백엔드는 URL만 관리 | ⭐⭐⭐ 적합 |
| **로컬 파일 저장** | 단순 · 프로덕션 부적합 · 스케일 불가 | ⭐⭐ (개발용) |
| **Cloudinary · imgix** | 이미지 최적화 자동 · 유료 | ⭐ 오버킬 |
| **DB BLOB** | 절대 비추 | ⭐ |

## 옵션 비교

### 갈림길 1: 저장 백엔드

**Option A (채택) — AWS S3 + Pre-signed URL (환경별)**
- prod: 실제 S3 · Pre-signed PUT URL 발급 → FE 직접 업로드 → 저장 완료 콜백
- dev/local: MinIO or 로컬 디스크 (환경 분기)
- 장점: 표준 · 스토리 · 서버 대역폭 절약 · AWS 통합
- 비용: S3 버킷 · IAM 세팅 필요 (신입 학습 스토리로 오히려 좋음)

**Option B — 로컬 저장만**
- 거부 이유: EC2 재배포 시 파일 손실 · 다중 인스턴스 불가

### 갈림길 2: 파일 검증 위치

**Option A (채택) — 서버 사전 검증 (Pre-signed URL 발급 전) + 업로드 후 검증**
1. FE가 `POST /api/v1/media/upload-url` (파일명·크기·MIME 타입 전달)
2. 서버가 검증 (확장자 · 크기 · MIME) → Pre-signed URL + Media 레코드(PENDING) 반환
3. FE가 S3에 직접 PUT
4. FE가 `POST /api/v1/media/{mediaId}/complete` → 서버가 S3 실 존재 확인 → COMPLETED 전이
- 이중 검증 · 안전 · Kickstarter도 유사

### 갈림길 3: 삭제 정책

**Option A (채택) — Soft Delete (DB) + 배치 실제 삭제 (S3)**
- DB `deleted_at` 표시 → 배치가 30일 후 실 S3 파일 삭제
- 프로젝트 오픈된 이미지는 삭제 방지 (참조 검증)

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.media.*` (4레이어)
- Project · Chat · User Profile에서 공용 사용

### 엔티티 · 스키마
```sql
CREATE TABLE media (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  owner_user_id   BIGINT       NOT NULL,           -- 업로드한 사용자
  storage_key     VARCHAR(500) NOT NULL UNIQUE,    -- S3 key (예: media/2026/07/uuid.jpg)
  original_name   VARCHAR(200) NOT NULL,           -- 원본 파일명
  mime_type       VARCHAR(100) NOT NULL,
  size_bytes      BIGINT       NOT NULL,
  width           INT          NULL,               -- 이미지 폭
  height          INT          NULL,               -- 이미지 높이
  purpose         VARCHAR(30)  NOT NULL,           -- COVER · SCREENSHOT · STORY · AVATAR · CHAT_ATTACH
  status          VARCHAR(20)  NOT NULL,           -- PENDING · COMPLETED · FAILED · DELETED
  reference_type  VARCHAR(30)  NULL,               -- 소속 엔티티 타입 (PROJECT · USER · CHAT_MESSAGE)
  reference_id    BIGINT       NULL,               -- 소속 엔티티 ID
  deleted_at      TIMESTAMP    NULL,
  created_at      TIMESTAMP    NOT NULL,
  updated_at      TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_media_owner (owner_user_id, created_at DESC),
  KEY idx_media_ref (reference_type, reference_id, purpose),
  KEY idx_media_status (status, created_at)
);
```

### 도메인 모델
```java
// media.domain.Media (Aggregate root)
@Entity
public class Media extends BaseEntity {
    @Id @GeneratedValue Long id;
    Long ownerUserId;
    @Column(unique = true) String storageKey;
    String originalName;
    String mimeType;
    long sizeBytes;
    Integer width;
    Integer height;
    @Enumerated(STRING) MediaPurpose purpose;
    @Enumerated(STRING) MediaStatus status;
    @Enumerated(STRING) MediaReferenceType referenceType;   // nullable · 처음엔 미결
    Long referenceId;
    LocalDateTime deletedAt;

    public static Media createPending(Long ownerId, String storageKey, String originalName,
                                       String mimeType, long size, MediaPurpose purpose) {
        // 검증 · 상수 규칙 (아래 참조)
    }

    public void complete(Integer width, Integer height) {
        if (status != PENDING) throw new BusinessException(ErrorCode.MEDIA_INVALID_STATUS);
        this.status = COMPLETED;
        this.width = width;
        this.height = height;
    }

    public void attach(MediaReferenceType type, Long refId) {
        if (status != COMPLETED)
            throw new BusinessException(ErrorCode.MEDIA_NOT_COMPLETED);
        this.referenceType = type;
        this.referenceId = refId;
    }

    public void softDelete(Long userId) {
        verifyOwnership(userId);
        this.deletedAt = LocalDateTime.now();
        this.status = DELETED;
    }
}

// enum
public enum MediaPurpose { COVER, SCREENSHOT, STORY, AVATAR, CHAT_ATTACH }
public enum MediaStatus { PENDING, COMPLETED, FAILED, DELETED }
public enum MediaReferenceType { PROJECT, USER, CHAT_MESSAGE }
```

### 검증 규칙 (상수)
```java
public final class MediaConstraints {
    public static final long MAX_SIZE_BYTES = 10 * 1024 * 1024;   // 10 MB
    public static final Set<String> ALLOWED_MIMES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    public static final int MAX_SCREENSHOT_COUNT = 4;
    public static final int MAX_STORY_COUNT = 20;
}
```

### 업로드 흐름 (2단계)
```java
// media.application.MediaUploadService

// 1단계: Pre-signed URL 발급
@Transactional
public UploadUrlResponse issueUploadUrl(Long userId, UploadUrlCommand cmd) {
    // 검증
    if (cmd.sizeBytes() > MAX_SIZE_BYTES)
        throw new BusinessException(ErrorCode.MEDIA_SIZE_EXCEEDED);
    if (!ALLOWED_MIMES.contains(cmd.mimeType()))
        throw new BusinessException(ErrorCode.MEDIA_INVALID_MIME);

    // S3 key 생성 (media/{yyyy/MM}/{uuid}.{ext})
    String storageKey = generateStorageKey(cmd);

    // Media PENDING 저장
    Media media = Media.createPending(userId, storageKey, cmd.originalName(),
                                       cmd.mimeType(), cmd.sizeBytes(), cmd.purpose());
    mediaRepository.save(media);

    // S3 Pre-signed PUT URL 발급 (5분 유효)
    String url = s3Client.generatePresignedUrl(storageKey, Duration.ofMinutes(5));

    return new UploadUrlResponse(media.getId(), url, storageKey);
}

// 2단계: 업로드 완료 확정
@Transactional
public MediaResponse completeUpload(Long userId, Long mediaId) {
    Media media = mediaRepository.findByIdAndOwnerUserId(mediaId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

    // S3 실 존재 확인
    S3ObjectMetadata metadata = s3Client.headObject(media.getStorageKey())
            .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_S3_NOT_FOUND));

    // 이미지 파일이면 dimensions 추출
    Dimensions dims = imageInspector.inspect(media.getStorageKey());

    media.complete(dims.width(), dims.height());
    return MediaResponse.from(media, publicUrl(media));
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/media/upload-url` | JWT | Pre-signed URL 발급 (1단계) |
| POST | `/api/v1/media/{mediaId}/complete` | JWT | 업로드 완료 확정 (2단계) |
| GET | `/api/v1/media/{mediaId}` | JWT | 미디어 조회 (URL 포함) |
| DELETE | `/api/v1/media/{mediaId}` | JWT | Soft Delete (본인만) |

### 참조 첨부 흐름 (Project · Chat · User)
- Project 등록 시: `Project.coverMediaId` · `Project.screenshotMediaIds[]` 컬럼
- 첨부 시 `Media.attach(PROJECT, projectId)` 호출 (참조 표시)
- 삭제 방지: 프로젝트에 참조된 미디어는 Project 상태가 DRAFT 아니면 삭제 불가

### ErrorCode (신규)
- `MED001` MEDIA_NOT_FOUND (404)
- `MED002` MEDIA_SIZE_EXCEEDED (400)
- `MED003` MEDIA_INVALID_MIME (400)
- `MED004` MEDIA_INVALID_STATUS (400)
- `MED005` MEDIA_NOT_COMPLETED (409 · attach 전에 complete 필요)
- `MED006` MEDIA_S3_NOT_FOUND (500 · Pre-signed 발급 후 실제 업로드 안 됨)
- `MED007` MEDIA_OWNERSHIP_FAILED (403)
- `MED008` MEDIA_REFERENCE_LOCKED (409 · 참조된 미디어 삭제 시도)

### 환경별 설정
| 항목 | dev/local | prod |
|---|---|---|
| S3 백엔드 | MinIO or 로컬 디스크 | AWS S3 |
| 버킷 | `c1oud-mall-dev-media` | `c1oud-mall-media` |
| Public URL | `http://localhost:9000/...` | CloudFront (v0.0.5+) |
| Pre-signed 유효 시간 | 30분 | 5분 |

### 관측
- `media.upload.total{result=success|fail}` counter
- `media.storage.size_gauge` (총 저장 용량 · 배치)
- `media.orphaned.total` (참조 없는 미디어 · 배치)

## 이관 산출물

- **BE-Story #13-1**: `media` 컨텍스트 신규 패키지 + `Media` 엔티티 + 3개 enum
- **BE-Story #13-2**: `MediaConstraints` 상수 클래스
- **BE-Story #13-3**: S3 클라이언트 설정 (`software.amazon.awssdk:s3` 의존성 추가)
- **BE-Story #13-4**: dev/prod 환경별 S3 백엔드 설정 (MinIO or LocalStack)
- **BE-Story #13-5**: `MediaUploadService.issueUploadUrl`·`completeUpload` 흐름
- **BE-Story #13-6**: `MediaController` (4개 엔드포인트)
- **BE-Story #13-7**: `ErrorCode.MED001~008` 등록
- **BE-Story #13-8**: 이미지 dimension 추출 유틸 (java.awt.image 또는 BufferedImage)
- **BE-Story #13-9**: Project 등록 시 미디어 첨부 흐름 (Project · Media 결합 · 이슈 #08과 함께)
- **BE-Story #13-10**: Orphaned 미디어 배치 정리 (30일 후 실 S3 삭제)
- **FE-Story #13-1**: `src/features/media/UploadDropzone.tsx` (드래그·드롭 · Pre-signed URL 이용)
- **FE-Story #13-2**: `src/features/media/ImageGallery.tsx` (커버 + 4 썸네일 · 상세 페이지)
- **FE-Story #13-3**: 프로젝트 등록 페이지에 업로드 UI 통합 (디자인 반영)
- **Docs-Story #13-1**: `backend-boundary/error-codes.md` MED001~008 매핑
- **Docs-Story #13-2**: 미디어 사용 규범 · Purpose · Constraints 문서
- **SDD 개정**: 향후 `product-media.md` 신규 (M4 진입 시)

## 관련 이슈 / 문서

- 관련: [#08 Project](./issue-08-project-domain.md) — Project.coverMediaId · screenshotMediaIds
- 관련: [#12 Chat Rich Message](./issue-12-chat-rich-message.md) — IMAGE 메시지 타입 · CHAT_ATTACH purpose
- 관련: [#11 Maker Profile](./issue-11-maker-profile-response-stats.md) — 아바타 미디어 (v0.0.4+)
- 벤치마크: AWS S3 Pre-signed URL 표준

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — hero-media + 4 thumb + story-img (스토리 중간 이미지)
- `C:\Users\user\Desktop\fe\프로젝트 등록.html` — uploader (드래그·드롭) + thumbs (4개 슬롯)
- `C:\Users\user\Desktop\fe\펀딩 탐색.html` — pcard-media (카드 커버)
