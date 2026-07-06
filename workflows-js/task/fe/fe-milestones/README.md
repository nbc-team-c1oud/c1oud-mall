# [FE Milestones] c1oud-mall — 프론트엔드 주간 스냅샷

> **역할**: FE의 주(week) 단위 스코프 분배·통합 · BE 마일스톤과 **같은 SemVer**로 정합 유지.
> **위치 규칙**: `version/{X.Y.Zv}/` 하위에 주간 스냅샷 7개 파일.
> **BE 마일스톤과의 관계**: FE 0.0.Xv ↔ BE 0.0.Xv (같은 주 · 같은 버전)

---

## 주간 스냅샷 파일 구성 (7 files per week)

`version/0.0.1v/` 하위:

| 파일 | 목적 | BE 대응 |
|---|---|---|
| `milestone.md` | 이번 주 스코프·tier 분할·의존 chain·완료 신호 | ↔ BE `milestone.md` |
| `infra.md` | FE 배포 인프라 (S3 · CloudFront · GHA · 도메인 · CORS) | ↔ BE `infra.md` (일부 공유) |
| `performance.md` | Web Vitals (LCP · INP · CLS) · Lighthouse · 번들 크기 · TanStack Query cache 지표 | FE 전용 |
| `outcome.md` | 머지된 FE Story · UX delta · 사용자 경험 개선 | ↔ BE `outcome.md` |
| `cost.md` | FE 관련 비용 (CloudFront · S3 · Route53 공유 · Sentry · Figma 등 툴 라이선스) | ↔ BE `cost.md` (일부 공유) |
| `review.md` | 회고 · 다음 버전 진입 신호 | ↔ BE `review.md` |
| `ux-test.md` | FE 화면 수동 검증 체크리스트 (a11y · 시각 · 플로우 · 엣지) | FE 전용 |

---

## SemVer 규칙 (BE와 동일)

- **PATCH (0.0.Xv)**: 주간 스냅샷 (기능 단위)
- **MINOR (0.X.0v)**: 릴리즈 (사용자 노출)
- **MAJOR (X.0.0v)**: 운영 모델 변경 · 아키텍처 전환

---

## BE ↔ FE Sync 원칙

### 버전 정합
- FE 0.0.1v와 BE 0.0.1v는 **같은 SemVer** · 같은 주 · 같은 완료 신호 기준
- FE `milestone.md`의 첫 섹션에 `## BE M{N} 참조` 명시 (링크 · 완결 조건)

### 인프라 공유
- **CloudFront · Route53 · ACM**: BE·FE 공유 (도메인 · 인증서)
- **S3**: FE 정적 자산 (별도 버킷)
- **GHA**: 파이프라인 분리 (`deploy.yml` = BE · `deploy-fe.yml` = FE 예정)

### 계약 정합
- FE `outcome.md`의 "머지된 Story"는 BE Handoff 리포트를 참조해서 정합 검증
- `backend-boundary/error-codes.md`의 UX 매핑 갱신을 FE 주간 회고에 포함

---

## 상태 전이 (BE와 동일)

| 상태 | 조건 |
|---|---|
| **planning** | milestone.md만 존재 · 나머지 6개 골격만 |
| **active** | 주간 진행 중 |
| **frozen** | 주간 종료 · review.md 작성 완료 · 다음 주 전환 |
| **released** | 릴리즈 마일스톤(0.X.0v)로 승격 · 태그 부착 |

---

## 다음 마일스톤 진입 조건

FE 다음 버전(예: 0.0.2v)로 넘어가려면:
- [ ] 이번 주 `review.md` 작성 완료
- [ ] BE 다음 버전(0.0.2v)의 완료 신호와 정합 (같은 주 · 같은 신호 리스트)
- [ ] 이월 Story 재계획 완료 (M2 milestone.md에 반영)

---

## 참조

- **BE 마일스톤**: `../../../milestones/version/{X.Y.Zv}/`
- **FE SDD**: `../fe-workspectrum/sdd/sdd.md`
- **FE-ADR**: `../fe-workspectrum/FE-ADR-CANDIDATES.md`
- **backend-boundary**: `../../../../backend-boundary/error-codes.md`
