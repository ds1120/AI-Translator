# AI-Translator · Phase 1

한국어 ↔ 태국어 LINE 그룹 자동 번역 백엔드. Java 21 / Spring Boot 3.5 / Maven / MyBatis / MariaDB.
관리 화면은 **React + TypeScript + Vite**로 Phase 3에서 구현한다. Phase 1에는 관리자 UI/API가 없다.

이 작업 환경은 Linux이므로 `/workspace/AI-Translator`에 독립 생성했다. Windows에서는 이 폴더를 `C:\ai\project\AI-Translator`로 복사해 사용하면 된다. 기존 프로젝트는 수정하지 않았다.

## 인증정보 없이 실행 (Mock)

필수: **JDK 21** (`java -version`, `javac -version` 모두 21), Maven 3.9+, Python 3(웹훅 전송 스크립트 사용 시).

```powershell
cd C:\ai\project\AI-Translator\backend
mvn clean verify
mvn spring-boot:run
```

빌드된 JAR가 포함된 배포 ZIP에서는 프로젝트 루트에서 `java -jar backend/target/ai-translator-0.1.0.jar`로도 바로 실행할 수 있다 (Java 21 필요).

기본 profile은 `mock`이다. 내장 H2 메모리 DB라 MariaDB/Redis/LINE/AI 키가 필요 없다. 서버는 기본 `127.0.0.1:8090`에 바인딩한다.

- 상태 확인: http://localhost:8090/actuator/health
- `/`에는 화면이 없으며 403 응답이 정상이다.
- Mock은 아래 **고정 문장만** 번역한다. 일반 문장 번역 능력은 없으며 `[MOCK]` 표식을 붙인다.

| 입력 | Mock 결과 |
| --- | --- |
| 안녕하세요 | สวัสดีค่ะ |
| สวัสดีค่ะ | 안녕하세요 |
| 내일 아침 9시에 출발할게. | พรุ่งนี้ฉันจะออกเดินทางตอนเก้าโมงเช้า |
| พรุ่งนี้เจอกันตอนสิบโมงนะ | 내일 10시에 만나자. |

다른 터미널에서 프로젝트 루트로 이동한 후:

```powershell
python scripts/mock-webhook.py
python scripts/mock-webhook.py --text "พรุ่งนี้เจอกันตอนสิบโมงนะ"
```

각 명령은 같은 이벤트를 두 번 보내며 HTTP 200을 두 번 받는다. 백엔드 로그에는 `outcome=SENT`가 한 번만 남아야 한다. Mock 회신은 내부 메모리 sink로 전달되며 실제 LINE으로 전송하지 않는다. 자동 통합 테스트가 정확한 회신 문구까지 검증한다. 종료는 Ctrl+C. Mock DB는 재시작 시 초기화된다.

## 집 PC의 Qwen 사용 — 현재 진행 방식

Windows 10 + 기존 OpenAI 호환 `Qwen3-14B` API를 지원한다. 외부 유료 AI 대신 `LocalQwenProvider`를 선택하며, [Windows Qwen 실행 가이드](docs/windows-qwen.md)와 `.env.qwen.example`을 사용한다. LINE Webhook URL은 Qwen `/v1` 주소와 별도다. 집 PC 실행/공개 HTTPS 연결은 아직 별도 진행해야 한다.

## Railway 클라우드 배포 — 선택 사항

[Railway 배포 가이드](docs/railway-deploy.md)에 MariaDB 영속 볼륨, 비밀 환경변수, 공개 HTTPS와 LINE 연결 절차를 정리했다. 계정 연결과 실제 배포는 별도 진행해야 한다.

## 실제 LINE 봇 연결

자세한 절차: [LINE 설정 가이드](docs/line-setup.md).

1. LINE Official Account 생성 → LINE Official Account Manager에서 Messaging API 활성화.
2. LINE Developers Console에서 채널 Secret과 Channel access token(long-lived)을 준비.
3. 봇의 그룹/복수 사용자 채팅 참여 허용, Webhook 사용 및 재전송 활성화.
4. `.env.example`을 `.env`로 복사하고 LINE/AI/DB 정보를 입력.
5. `docker compose up --build -d`로 실행.
6. HTTPS 도메인 또는 개발용 HTTPS 터널을 `http://localhost:8080`에 연결. Webhook URL을 `https://도메인/webhooks/line`으로 설정하고 Verify.
7. 봇을 LINE 친구로 추가한 뒤 그룹에 초대. 한국어/태국어 메시지 전송.

Compose는 DB와 백엔드를 외부 포트로 열지 않고 Nginx만 localhost:8080에 공개한다. 외부 LINE 웹훅은 신뢰할 수 있는 HTTPS reverse proxy/터널을 통해 전달해야 한다. Nginx 설정은 로컬 HTTP용이며 공인 TLS 인증서를 제공하지 않는다.

```powershell
Copy-Item .env.example .env
# .env에 실제 값 입력
# Mock 서버가 8090에서 실행 중이면 Ctrl+C로 종료
docker compose up --build -d
docker compose logs -f backend
```

`.env`는 Compose에서 읽는다. **Maven/Java 실행은 .env를 자동으로 읽지 않는다.** Windows 로컬 live 실행은 환경변수를 직접 설정한다 (이력에 남는 공유 터미널에 키를 입력하지 않는다).

```powershell
$env:SPRING_PROFILES_ACTIVE="live"
$env:DB_URL="jdbc:mariadb://localhost:3306/ai_translator"
$env:DB_USER="ai_translator"
# DB_PASSWORD, LINE_CHANNEL_SECRET, LINE_CHANNEL_ACCESS_TOKEN, AI_API_KEY는 OS 환경변수로 제공
cd backend
mvn spring-boot:run
```

MariaDB에는 `ai_translator` DB와 사용자 권한을 먼저 생성한다. 테이블은 시작 시 자동 생성하며 초기 SQL은 [database/001-schema.sql](database/001-schema.sql)에 있다. 외부 AI 기본값은 OpenAI 호환 Chat Completions API이고 `AI_BASE_URL`, `AI_MODEL`, `AI_TIMEOUT_SECONDS`로 변경한다. 실제 사용 계정에서 모델 사용 가능 여부를 확인한다.

## 구현 범위와 운영 제한

- raw HTTP body HMAC-SHA256 검증 후 수신, 그룹 text/active 이벤트만 처리.
- DB 접수 완료 후 200, 별도 worker에서 번역/회신. DB 오류는 5xx.
- 한국어/태국어 유니코드 문자 감지. 다른 언어·혼합 한태·명령어·봇 자신은 무시.
- webhookEventId PK 중복 방지 (MariaDB에서 재시작 이후에도 7일 보존).
- 기본 번역문만 회신. 역번역/문맥/명령/관리자 등록은 미구현.
- LINE/AI 실패는 상태/안전한 로그로 기록. 불확실한 전달은 UNKNOWN이며 자동 재전송하지 않는다.
- 기본 Push 비활성. 비용을 이해한 후 `LINE_PUSH_ENABLED=true`일 때만 만료 Reply 대신 Push. Reply 실패 후 무조건 Push하지 않는다.
- DB 작업 원문/토큰은 완료 시 삭제. 원문 로그 없음. API 호출 횟수·실패·처리 시간은 내부 Micrometer 지표로 기록.
- 관리자 인증/UI가 도입되기 전까지 health/webhook 이외 모든 HTTP 접근은 차단한다.
- 단일 순차 worker MVP라 대규모 트래픽 SLA를 보장하지 않는다. 외부 API 응답 지연/실패와 정확도는 별도 실계정 검증이 필요하다.

[구조·오류 정책](docs/architecture.md) / [검증 기록](docs/validation.md).

## 테스트

```powershell
cd backend
mvn clean verify
```

서명 누락/위조/본문 변조, LINE Verify 이벤트, 한↔태 회신, 동시 중복 웹훅, 개인정보 제거, 다른 언어·봇·그룹 설정·오래된 토큰, AI 실패, 외부 HTTP Provider, LINE 요청 형식 및 호출 횟수를 자동 검증한다. 실제 LINE 채널이나 유료 AI 호출 없이 실행한다.
