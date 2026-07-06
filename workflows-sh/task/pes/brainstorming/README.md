# [PES · Brainstorming] c1oud-mall — 크로스 컷팅 갈림길 축적

> **역할**: 특정 Product 하나에 속하지 않고 여러 Product·BC를 관통하는 갈림길·후보안을 축적한다.
> Product SDD 마다 §5(설계 결정)·§6(대안 검토)에 흡수되기 전에 자유롭게 후보를 쌓는 곳.
> **위치 규칙**: 버전 단위(`{X.Y.Zv}/`) 하위에 도메인 분류로 파일을 둔다.

---

## 도메인 택소노미

| 파일 | 대상 |
|---|---|
| `dev.md` | 개발 컨벤션 · 코드 품질 · 리팩토링 방향 |
| `ops.md` | 운영 · 배포 · 관측성 · SRE 경계 |
| `data.md` | 데이터 모델 · 스키마 진화 · 성능 |
| `ai.md` | AI 연동 · Prompt · Fallback 정책 |
| `deploy.md` | 배포 파이프라인 · CI/CD 자동화 |
| `snapshot.md` | 이번 버전 스냅샷 — 결정된 것 / 열려있는 것 표 |

새 크로스 컷팅 주제는 이 표에 추가 후 파일 생성.

---

## 각 파일 내부 규격 (Candidate 포맷)

한 파일 안에 다음 순서로 Candidate를 누적한다.

```markdown
## [Candidate N] {제목} [status — 변경 시 상세]

> 한 줄 가치 제안 — 무엇이 개선되는가

### 배경 (왜 지금)
- 현재 코드 / PES / 운영 상태의 갭
- 방치할 때의 리스크 또는 기회 비용

### 후보안 (A / B / C)
- **A안**: {요지} — 장점 · 단점 한 줄씩
- **B안 (선택)**: {요지} — 비용 · 보상
- **C안**: {요지} — 거부 사유

### 1차 권장
- 어느 것 · 왜 (1~2줄)
- *(최종 아님 — PES 단계에서 재평가)*

### PES 승격 경로
- 어느 기존 Product가 흡수하거나, 신규 Product 후보
- 의존 Story · ADR 후보

### 미결 질문 *(선택)*
- 추가로 필요한 정보
```

---

## Candidate 상태

| 상태 | 의미 |
|---|---|
| **pending** | 브레인스토밍 중 |
| **promoted** | Product SDD의 §5·§6에 흡수됨 (원 후보는 링크만) |
| **resolved** | 코드에 반영 완료 |
| **deprecated** | 무효화 (사유 명시) |
| **merged** | 다른 Candidate에 합쳐짐 |

상태 변경은 후보 제목 접미어로 표기.

---

## 참조

- Product SDD 정의: `../workspectrum/sdd/sdd.md`
- Fix 이슈 (Product별 실행 중 이슈): `../../fix/brainstorming/version/{X.Y.Zv}/`
- 마일스톤: `../../milestones/version/{X.Y.Zv}/`
- ADR: `workflows/living-docs/Ai-adr/`
