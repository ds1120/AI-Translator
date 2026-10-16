# Windows 10 + 기존 Qwen API 사용

기존 Qwen 서버는 OpenAI 호환 API이다. 2026-10-16에 `/v1/models` HTTP 200, 모델 ID `Qwen3-14B`를 확인했다. `/v1/chat/completions`에서 한국어→태국어 및 태국어→한국어 예문 응답을 확인했다. 태국어 예문 요청은 전체 HTTP 약 1.66초였다. 이는 간단한 연결 시험이며 번역 품질 전반/부하 응답시간을 보장하지 않는다.

번역 서버는 `LocalQwenProvider`로 연결한다. 인증키가 없는 Qwen API에는 Authorization 헤더를 보내지 않는다. 키가 설정되어 있으면 Bearer 인증을 사용한다. 외부 유료 LLM은 호출하지 않는다. `enable_thinking=false`, 최대 출력 512 토큰을 요청하며, 출력이 잘리거나 추론 태그가 완결되지 않으면 회신하지 않고 실패 처리한다. API 구현이 옵션을 실제로 적용하는지는 서버 설정에 따라 다르다.

## Windows 로컬 실행 (파일 DB)

프로젝트 루트에서 `config/application.properties.example`을 `config/application.properties`로 한 번 복사하고 `app.line.secret`, `app.line.token`을 채운다. 실제 설정 파일은 Git에서 제외된다. 기존 XML에 저장한 인증정보는 더 이상 사용하지 않는다.

이후 프로젝트 루트에서 `java -jar backend/target/ai-translator-0.1.0.jar`만 실행한다. Spring Boot가 설정 파일을 자동으로 읽으므로 키·토큰을 다시 입력하거나 별도 스크립트를 사용할 필요 없다. 실행 중인 서버는 먼저 Ctrl+C로 종료한다. 로컬 Qwen `http://127.0.0.1:8082/v1`, 번역 서버 8090, 파일형 H2 DB `backend/data/translator`를 사용한다. Push는 비활성화한다.

설정을 변경하려면 `config/application.properties`를 편집하고 서버를 재시작한다. 자세한 파일 위치·설정 예제와 LINE 그룹 명령어는 [README](../README.md)를 참고한다.

## Windows Docker 실행

필수: Windows 10에서 Docker Desktop(WSL2 기반) 사용 가능한 환경, Git. 기존 사용 중인 Docker가 있다면 새로 설치할 필요 없다.

```powershell
cd C:\ai\project\AI-Translator
git pull
Copy-Item .env.qwen.example .env.qwen
# .env.qwen에 재발급 LINE Secret과 Access Token, DB 비밀번호를 입력
docker compose --env-file .env.qwen up --build -d
docker compose --env-file .env.qwen logs -f backend
```

- 확인: `http://localhost:8080/actuator/health` → UP.
- `.env.qwen`은 Git에서 제외된다. 키/비밀번호를 채팅에 붙이지 않는다.
- Compose 안에서는 MariaDB를 별도 컨테이너로 자동 실행한다. 이미 PC에서 MariaDB를 운영하고 있다면 환경에 맞춰 개별 Java 실행 또는 DB 연결 변경을 할 수 있다.
- Docker 없이 실행: JDK 21/Maven/MariaDB 필요. 환경변수 `SPRING_PROFILES_ACTIVE=qwen`, LINE 인증정보, `DB_URL`/`DB_USER`/`DB_PASSWORD` 설정 후 `mvn spring-boot:run`. Maven은 `.env.qwen` 파일을 자동으로 읽지 않는다.
- Qwen도 같은 PC에 있다면 실제 API 포트를 확인해 localhost로 연결할 수 있다. Docker 컨테이너에서 PC 호스트를 부를 때는 `localhost`가 PC를 가리키지 않는다. 현재 구성은 제공된 HTTPS 주소를 사용한다.

## LINE Webhook 주소는 별도

`https://dsss.mooo.com/v1`은 **AI 추론 API 주소**이며 LINE Webhook URL이 아니다. LINE은 번역 서버의 `/webhooks/line`으로 연결해야 한다. 번역 서버를 실행한 뒤 무료 HTTPS 터널이나 기존 reverse proxy에 별도 경로를 추가한다. 예를 들어 실제 프록시가 `/webhooks/line`을 PC의 8090으로 전달하도록 구성된 경우에만 그 공개 주소를 등록한다. 기존 Qwen API 경로 `/v1`을 덮어쓰지 않는다.

현재 작업 환경에서 집 PC에 접속할 권한/연결은 없으므로 PC 설치·실행·프록시 변경은 아직 수행하지 않았다. 공개 API가 현재 인증 없이 응답하므로 외부 접근 범위와 인증은 운영자가 확인한다. 모델 추론 자체는 로컬 GPU이며 외부 AI API 사용료는 없으나 전기/기존 도메인·네트워크 비용은 별개다.
