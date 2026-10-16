# AI-Translator · Phase 1

한국어 ↔ 선택 언어(태국어·베트남어·영어·일본어) LINE 1:1·그룹 자동 번역 백엔드. Java 21 / Spring Boot 3.5 / Maven / MyBatis / MariaDB.
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

Windows 10 + 기존 OpenAI 호환 `Qwen3-14B` API를 지원한다. 외부 유료 AI 대신 `LocalQwenProvider`를 선택한다. 로컬 실행은 별도 설정 파일을 만든 뒤 `java -jar`로 실행한다. Docker 실행은 [Windows Qwen 실행 가이드](docs/windows-qwen.md)와 `.env.qwen.example`을 참고한다. LINE Webhook URL은 Qwen `/v1` 주소와 별도다.

### 로컬 설정 파일 위치

프로젝트를 `C:\ai\project\AI-Translator`에 설치한 경우:

| 파일 | 역할 |
| --- | --- |
| `C:\ai\project\AI-Translator\config\application.properties` | 실제 LINE Secret·Token, Qwen 주소·모델, 서버 포트, DB 경로, 기본 언어. Git에서 제외된다. |
| `C:\ai\project\AI-Translator\config\application.properties.example` | 값이 비어 있는 설정 예제. GitHub에 포함된다. |

실제 설정 파일은 일반 텍스트로 직접 편집한다. 키·토큰은 이 파일에만 넣고 GitHub나 채팅에 공유하지 않는다. 이전 `.env.line.credentials.xml` 파일은 더 이상 읽지 않는다. 실행 스크립트는 선택 사항이며 별도 입력 없이 Java 실행을 돕는 용도로만 사용한다.

### 번역 언어 선택

`config/application.properties`의 `app.translation.partner-language=TH` 값을 `TH`(태국어), `VI`(베트남어), `EN`(영어), `JA`(일본어) 중 하나로 설정한다. 기본값은 `TH`이며, 대화에서 별도로 변경하지 않은 경우 이 설정을 따른다. 선택한 언어와 한국어 사이에서만 번역한다. 임시로 기본 언어를 바꾸려면 실행 인수를 사용한다.

```powershell
java -jar backend/target/ai-translator-0.1.0.jar --app.translation.partner-language=EN
```

기본 언어 변경은 재시작 후 적용되며 키·토큰은 그대로 재사용한다. Docker에서는 환경변수 `TRANSLATION_PARTNER_LANGUAGE`로 지정한다. 각 대화에 저장된 언어 설정은 서버의 기본 언어보다 우선한다.

### LINE 1:1 대화에서 번호로 번역 언어 선택

봇과의 **1:1 대화**에서 `/설정`을 보내면 현재 언어와 다음 메뉴를 보여준다. 각 사용자의 설정은 다른 사용자·그룹과 분리된다. 기존 그룹에서도 같은 명령어를 참여자 누구나 사용할 수 있다.

| 대화에 보내는 메시지 | 동작 |
| --- | --- |
| `/언어 1` | 한국어 ↔ 태국어 |
| `/언어 2` | 한국어 ↔ 베트남어 |
| `/언어 3` | 한국어 ↔ 영어 |
| `/언어 4` | 한국어 ↔ 일본어 |
| `/설정` | 현재 번역 언어와 1~4번 메뉴 확인 |
| `/기본언어 한국어` | 선택한 번역 쌍 이외의 메시지를 한국어로 번역 |

`/언어 1`만 보내도 즉시 저장되고 봇이 변경 결과를 답장한다. 서버 재시작 없이 이후 해당 대화의 메시지부터 새 언어로 번역한다. `/언어 태국어`, `/언어 베트남어`, `/언어 영어`, `/언어 일본어`와 언어 코드 `TH`, `VI`, `EN`, `JA`도 지원한다. 잘못된 번호·언어를 입력하면 현재 설정과 메뉴를 안내하고 기존 설정을 유지한다. 명령어 자체는 AI로 번역하지 않는다.

### 선택 언어 외 메시지의 기본 번역 언어

`/기본언어 한국어`, `/기본언어 태국어`, `/기본언어 베트남어`, `/기본언어 영어`, `/기본언어 일본어`로 해당 대화의 기본 번역 언어를 설정한다. 언어 코드 `KO`, `TH`, `VI`, `EN`, `JA`도 지원한다. 예를 들어 `/언어 1`과 `/기본언어 한국어`를 설정하면 한국어↔태국어는 그대로 양방향 번역하고, 영어·일본어 등 그 밖의 입력은 한국어로 번역한다. `/기본언어 태국어`로 바꾸면 그 밖의 입력을 태국어로 번역한다.

`/설정`에서 현재 기본언어를 확인한다. 기본언어를 한 번도 설정하지 않은 대화는 기존처럼 선택 쌍 밖의 메시지를 건너뛴다. 설정은 `chat_default_languages` 테이블에 대화별로 저장하며 기존 번역 쌍을 변경하지 않는다. 재시작 후에도 유지된다. 이미 기본언어로 감지된 쌍 밖의 입력은 중복 번역하지 않는다. 언어를 확정할 수 없는 문자 입력은 AI에 원문 언어 감지를 맡겨 기본언어로 번역한다. 숫자·이모지만 있는 메시지는 계속 건너뛴다.

설정은 기존 DB의 `chat_group_languages` 테이블에 저장한다. 기존 `group_id` 열을 대화 식별자로 사용하며 1:1은 LINE userId, 그룹은 groupId로 구분한다. 파일형 H2 또는 MariaDB를 사용하면 서버 재시작 후에도 유지된다. 메모리 DB를 쓰는 Mock에서는 재시작 시 초기화된다. 기존 그룹 설정과 데이터는 유지한다. 이미 처리된 명령어의 재전송과 오래된 명령어로 설정이 다시 바뀌지 않도록 처리한다.

영어는 라틴 문자, 일본어는 히라가나·가타카나를 기준으로 감지한다. 베트남어 모드에서는 성조가 없는 문장도 처리하도록 라틴 문자 메시지를 베트남어로 해석하므로 영어 문장도 베트남어 입력으로 처리될 수 있다. 라틴 문자만으로 언어를 완벽히 구분하지는 못한다. 한자만 있는 문장·다른 문자권·혼합 한태일 문장은 기본언어가 설정되면 AI 자동 감지로 번역하고, 미설정이면 건너뛴다. 숫자·이모지만 있는 메시지는 건너뛴다. Mock에서는 `안녕하세요`, `สวัสดีค่ะ`, `Xin chào`, `Hello`, `こんにちは`, `你好` 인사 예문을 지원한다.

### 최초 저장 및 키·토큰 변경

새로 설치하는 경우 LINE Developers에서 해당 채널의 **Basic settings → Channel secret**과 **Messaging API → Channel access token (long-lived)**을 준비한다. PowerShell에서 예제를 한 번만 복사한다. 기존 설정이 있으면 덮어쓰지 않는다.

```powershell
cd C:\ai\project\AI-Translator
if (!(Test-Path config/application.properties)) {
    Copy-Item config/application.properties.example config/application.properties
}
notepad config/application.properties
```

`app.line.secret=` 오른쪽에 Channel Secret을, `app.line.token=` 오른쪽에 Access Token을 따옴표 없이 넣고 저장한다. `spring.profiles.active=qwen`이 실제 번역 모드를 선택한다. 키·토큰을 바꿀 때도 이 파일만 편집한다. 환경변수나 매번 입력하는 절차는 필요 없다.

### 설정 적용 및 서버 실행

실행 중인 번역 서버가 있으면 해당 창에서 **Ctrl+C**로 종료한 뒤 실행한다.

```powershell
cd C:\ai\project\AI-Translator
java -jar backend/target/ai-translator-0.1.0.jar
```

Spring Boot가 프로젝트 루트의 `config/application.properties`를 자동으로 읽고 `qwen` 프로필로 시작한다. 반드시 프로젝트 루트에서 실행한다. 기본 연결은 번역 서버 `127.0.0.1:8090`, Qwen `http://127.0.0.1:8082/v1`, 모델 `Qwen3-14B`이며, DB는 `backend/data/translator` 파일에 저장한다. Push 발송은 꺼져 있다. 이 파일이 없으면 기본 Mock 모드로 시작하므로 로그의 활성 프로필이 `qwen`인지 확인한다.

키·토큰 또는 연결 설정을 변경한 후에는 서버를 재시작해야 적용된다. 포트를 바꾸면 Caddy의 번역 서버 연결 포트도 함께 맞춘다.

실행 후 상태 확인: http://localhost:8090/actuator/health → `{"status":"UP"}`. `/`는 웹 화면이 없어 403을 반환한다. 현재 PC의 Caddy 설정에서는 공개 상태 확인 주소가 `https://dsss.mooo.com/actuator/health`, LINE Webhook URL이 `https://dsss.mooo.com/webhooks/line`이다. Caddy 설정은 프로젝트 외부의 `C:\ai\caddy\Caddyfile`에서 관리한다.

LINE에서 **Use webhook**을 켜고 봇을 친구로 추가한 뒤 1:1 대화에서 `/설정`을 보낸다. 그룹에서도 사용하려면 **Allow bot to join group chats**를 켜고 봇을 초대한다. 한국어·선택 언어 텍스트를 번역한다. LINE 기본 안내 문구가 번역과 함께 오면 Official Account Manager의 **설정 → 응답 설정 → 자동응답 메시지**를 끄고 Webhook은 켜 둔다.

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

- raw HTTP body HMAC-SHA256 검증 후 수신, 1:1·그룹 text/active 이벤트만 처리.
- DB 접수 완료 후 200, 별도 worker에서 번역/회신. DB 오류는 5xx.
- 한국어/태국어/베트남어/영어/일본어를 설정된 언어 쌍으로 번역. 쌍 밖의 문자 입력은 대화의 기본언어가 있으면 해당 언어로 번역하며, 미설정이면 무시한다. 감지 한계는 위 안내 참고. 미지원 명령어·봇 자신은 무시.
- webhookEventId PK 중복 방지 (MariaDB에서 재시작 이후에도 7일 보존).
- 기본 번역문과 대화별 언어 설정 명령어 결과를 회신. 역번역/문맥/관리자 등록은 미구현. 1:1은 각 사용자가, 그룹은 참여자 누구나 해당 대화의 설정을 변경 가능.
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
