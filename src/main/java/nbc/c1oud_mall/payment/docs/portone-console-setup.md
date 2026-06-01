# PortOne V2 콘솔 설정 가이드

> 결제 BC가 PortOne V2 REST API를 호출하기 위해 PortOne 콘솔에서 수행해야 하는 작업과 그 결과를 우리 서버 환경변수로 연결하는 방법.

---

## 0. 우리가 PortOne에서 필요한 정보 (요약)

| 항목 | 어디서 발급 | 사용처 |
| --- | --- | --- |
| **V2 API Secret** | 콘솔 → API 연동 → 시크릿 키 | 우리 서버 `PORTONE_API_SECRET` 환경변수 |
| **Store ID** | 콘솔 → 스토어 관리 (storeId) | 프론트엔드 SDK (서버는 무관) |
| **Channel Key** | 콘솔 → 결제 연동 → 채널 (channelKey) | 프론트엔드 SDK (서버는 무관) |
| Webhook URL | 콘솔 → 웹훅 | (Story 3+ Webhook 처리 도입 시) |

Story 2-2 시점에 반드시 필요한 것은 **V2 API Secret** 하나. 1~3번은 실제 PG 결제를 e2e 테스트할 때 필요.

---

## 1. PortOne 가입

1. https://portone.io 접속
2. 우상단 "회원가입" → 이메일·비밀번호로 가입
3. 이메일 인증 완료

## 2. Store 생성

1. 로그인 후 콘솔 좌측 메뉴 → "스토어 관리"
2. "+ 스토어 추가" 클릭
3. 스토어 정보 입력 (사업자명·주소·연락처 등)
4. 생성 완료 후 자동 발급된 **storeId** 메모 (프론트엔드에서 사용)

## 3. Channel 설정 (PG 연동)

본 단계는 실제 결제 위젯을 띄울 때 필요. Story 2-2 백엔드 단독 테스트에는 불필요.

1. 콘솔 좌측 메뉴 → "결제 연동" → "채널"
2. "+ 채널 추가" 클릭
3. 사용할 PG사 선택 (TossPayments / KakaoPay / NaverPay / KGINIcis 등)
4. 각 PG사의 가맹점 ID·API 키 등록 (해당 PG사에서 별도로 가맹 신청·발급 필요)
5. 환경 선택: **테스트** 또는 **실결제**
6. 생성 완료 후 발급된 **channelKey** 메모 (프론트엔드에서 사용)

## 4. V2 API Secret 발급 ⭐ (필수)

1. 콘솔 좌측 메뉴 → "API 연동" → "API 시크릿 관리"
2. 메뉴에서 **V2** 탭 선택 (V1은 사용 안 함)
3. "+ 시크릿 발급" 클릭
4. 명칭 입력 (예: `c1oud-mall-dev-secret`)
5. 발급 완료 → **시크릿 문자열을 즉시 복사**
   - ⚠️ 시크릿은 발급 직후 한 번만 노출됨. 분실 시 폐기·재발급 필요
6. 안전한 저장소(비밀번호 매니저 등)에 보관

## 5. 테스트 모드 활성화 (선택)

실제 결제 발생 없이 e2e 흐름을 검증할 때 사용.

1. 콘솔 우상단 환경 토글에서 "테스트 모드" ON
2. 채널을 테스트 환경으로 만들었는지 확인 (3번 단계 5)
3. 테스트용 카드번호 (예: `4242-4242-4242-4242`)로 결제 시도 → 실 결제 없이 PortOne 응답만 받아옴

## 6. 개발 머신 환경변수 설정 ⭐ (필수)

발급받은 V2 API Secret을 우리 서버가 사용할 수 있도록 환경변수에 등록.

### PowerShell (현재 세션 한정)
```powershell
$env:PORTONE_API_SECRET = "<4번에서 복사한 시크릿>"
$env:PORTONE_BASE_URL = "https://api.portone.io"  # 기본값과 동일하면 생략 가능
```

### IntelliJ IDEA Run Configuration
1. Run/Debug Configurations → 해당 Spring Boot 설정
2. "Environment variables" 입력란에:
   ```
   PORTONE_API_SECRET=<시크릿>;PORTONE_BASE_URL=https://api.portone.io
   ```

### Windows 시스템 환경변수 (영구)
1. 제어판 → 시스템 → 고급 시스템 설정 → 환경 변수
2. "사용자 변수" 또는 "시스템 변수"에 추가:
   - 이름: `PORTONE_API_SECRET`
   - 값: `<시크릿>`
3. IntelliJ·터미널 재시작 (변수 반영용)

### `.env` 파일 (현 프로젝트 미도입)
- Spring Boot는 기본으로 `.env`를 로드하지 않음. 도입하려면 `spring-dotenv` 같은 의존성 추가 필요. 1차에는 미도입.

## 7. Webhook URL 등록 (Story 3 예정)

결제 상태 변경 통지를 PortOne이 우리 서버에 직접 호출. Story 2-2엔 미필요.

1. 콘솔 좌측 메뉴 → "웹훅"
2. "+ 웹훅 추가"
3. URL 입력: `https://<우리 도메인>/api/payments/webhook` (Story 3에서 구현 예정인 endpoint)
4. 구독할 이벤트 선택 (Payment.Paid·Payment.Cancelled 등)
5. 서명 검증용 시크릿 별도 발급·환경변수 등록 (Story 3에서 자세히)

---

## 자주 발생하는 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| 401 Unauthorized | `PORTONE_API_SECRET` 미설정 또는 오타 | 환경변수 확인, IntelliJ 재시작 |
| 403 Forbidden | V1 시크릿을 V2 엔드포인트에 사용 | V2 시크릿 발급 (4번) |
| 404 Not Found (paymentId) | 콘솔이 실결제 모드인데 테스트 결제 시도 | 콘솔 환경 토글 확인 |
| Connection timeout | 사내 방화벽이 api.portone.io 차단 | 프록시 설정 또는 사내 네트워크 담당자 문의 |

## 참고

- PortOne V2 공식 문서: https://developers.portone.io/api/rest-v2
- ADR-0002 (`docs/adr/0002-portone-http-client.md`) — HTTP 클라이언트·토큰 관리 결정
