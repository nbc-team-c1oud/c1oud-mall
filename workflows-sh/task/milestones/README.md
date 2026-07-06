# [Milestone] c1oud-mall — 릴리즈 & 주간 스냅샷

> **역할**: 진행 중인 여러 Product의 Story를 **주(week) 단위**로 분배·통합해 한 주 완료 신호(Definition of Done for the week)를 관리.
> **위치 규칙**: `version/{X.Y.Zv}/` 하위에 주간 스냅샷 6개 파일을 둔다.
> **SemVer 규칙**:
> - **PATCH (0.0.Xv)**: 주간 스냅샷 (기능 단위)
> - **MINOR (0.X.0v)**: 릴리즈 (사용자에게 노출되는 배포)
> - **MAJOR (X.0.0v)**: 운영 모델 변경 · 아키텍처 전환

---

## 주간 스냅샷 파일 구성 (6 files per week)

`version/0.0.1v/` 하위:

| 파일 | 목적 |
|---|---|
| `milestone.md` | 이번 주 스코프 분배, tier 분할, 의존 chain, 완료 신호 |
| `infra.md` | 인프라 진행, 배포 산출물, 환경별 설정 노트 |
| `outcome.md` | 머지된 Story, 사용자·기능·기술자산 delta |
| `cost.md` | AWS · Gemini API · 기타 비용 결산 |
| `review.md` | 회고 + 다음 버전 결정 |
| (필요 시) `performance.md` | 성능 기준선, 리소스 사용, 안정성 지표 |

---

## 릴리즈 vs 버전 마일스톤 구분

### 버전 마일스톤 (`version/0.0.Xv/`) — 주간 개발 진행
- 매주 목요일 자정 기준 스냅샷
- 목적: **팀 내부 정합성 유지 · 진행률 가시화**
- 스코프: Story 단위 — Must / Want / Extended 3-tier

### 릴리즈 마일스톤 (`version/0.X.0v/` 또는 별도 릴리즈 노트) — 사용자 노출
- 특정 사용자 가치 묶음이 완성되어 배포 대상이 되는 시점
- 목적: **외부 커뮤니케이션 · 태그(Git tag) · 롤백 지점**
- 스코프: Product 하나 이상의 완결된 가치

---

## 상태 전이

| 상태 | 조건 |
|---|---|
| **planning** | milestone.md만 존재, 나머지 5개는 빈 파일 또는 골격만 |
| **active** | 주간 진행 중 |
| **frozen** | 주간 종료, review.md 작성 완료, 다음 주로 전환 |
| **released** | 릴리즈 마일스톤(0.X.0v)로 승격되어 태그 부착 |

---

## 참조

- Milestone 정본 골격: `references/001.md` (본 README에 포함)
- Product SDD: `../pes/workspectrum/sdd/sdd.md`
- Brainstorming: `../pes/brainstorming/{X.Y.Zv}/`
- Fix 이슈 트래킹: `../fix/brainstorming/version/{X.Y.Zv}/`
- Living ADR: `workflows/living-docs/Ai-adr/`
