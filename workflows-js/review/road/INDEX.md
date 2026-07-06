# 회고록 Road

> 백엔드 기술 / 프로젝트 운영 / AI·워크플로우 활용 세 관점으로 꾸준히 쌓는 회고 기록.
> 각 항목은 하나의 결정이나 경험에 집중한다. 넓게 쓰는 일기가 아니라 좁고 깊은 단위 기록.

---

## 구조

```
road/
├── INDEX.md                  ← 이 파일 (내비게이션)
├── backend/
│   ├── _template.md          ← 백엔드 회고 양식
│   ├── topics.md             ← 작성 예정 / 완료 토픽 목록
│   └── entries/              ← 실제 회고 항목
├── project/
│   ├── _template.md          ← 프로젝트 회고 양식
│   ├── topics.md             ← 작성 예정 / 완료 토픽 목록
│   └── entries/
├── ai-workflow/
│   ├── _template.md          ← AI·워크플로우 회고 양식
│   ├── topics.md             ← 작성 예정 / 완료 토픽 목록
│   └── entries/
├── people/
│   ├── _template.md          ← 협업·인성 회고 양식
│   ├── topics.md             ← 작성 예정 / 완료 토픽 목록
│   └── entries/
└── troubleshooting/
    ├── _template.md          ← 트러블슈팅 회고 양식 (전 영역 공통)
    ├── topics.md             ← 작성 예정 / 완료 토픽 목록
    └── entries/
```

---

## 작성 흐름

1. `topics.md`에서 쓸 토픽을 고른다
2. `_template.md`를 복사해 `entries/YYYY-MM-DD-<slug>.md`로 저장
3. 작성 완료 시 `topics.md`의 체크박스를 체크

---

## 관점별 요약

| 관점 | 목적 | 핵심 질문 |
|---|---|---|
| **backend** | 기술 결정과 설계 경험 누적 | "왜 이렇게 설계했나, 다시 하면?" |
| **project** | 팀 협업·프로세스 개선 | "무엇이 팀 속도를 높이거나 막았나?" |
| **ai-workflow** | AI 활용 패턴 정제 | "AI와 어떻게 일할 때 가장 생산적이었나?" |
| **people** | 협업 태도·인성 성장 기록 | "나는 어떤 사람으로 이 팀에 있었나?" |
| **troubleshooting** | 전 영역 문제 해결 패턴 축적 | "왜 막혔나, 어떻게 뚫었나, 다음엔 더 빨리 잡을 수 있나?" |
