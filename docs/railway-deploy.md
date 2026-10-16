# Railway 클라우드 배포

GitHub `ds1120/AI-Translator`를 연결한다. 프로젝트 루트는 저장소 루트로 유지한다. `railway.json`이 `docker/Dockerfile` 빌드와 health check를 지정한다. Railway가 지정하는 PORT를 Spring Boot에서 사용한다.

## 서비스 두 개

1. MariaDB: Docker 이미지 `mariadb:11.4`. `/var/lib/mysql`에 영속 Volume을 연결한다. Public Domain/TCP Proxy는 만들지 않고 private network에서만 접근한다. 초기 환경변수:
   - `MARIADB_DATABASE=ai_translator`
   - `MARIADB_USER=ai_translator`
   - `MARIADB_PASSWORD`: 안전한 임의 비밀번호
   - `MARIADB_ROOT_PASSWORD`: 별도의 안전한 임의 비밀번호
2. Backend: GitHub 저장소 연결, `main` 브랜치, 저장소 루트에서 Dockerfile 빌드. 다음 변수를 Railway Variables에 설정한다.

| 변수 | 값 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | live |
| SERVER_ADDRESS | 0.0.0.0 |
| DB_URL | jdbc:mariadb://실제-MariaDB-private-host:3306/ai_translator |
| DB_USER | ai_translator |
| DB_PASSWORD | MariaDB의 MARIADB_PASSWORD를 변수 참조로 연결 |
| LINE_CHANNEL_SECRET | 재발급한 Secret |
| LINE_CHANNEL_ACCESS_TOKEN | 발급한 Access Token |
| LINE_BOT_USER_ID | 봇 user ID (선택) |
| LINE_PUSH_ENABLED | false |
| AI_BASE_URL | https://api.openai.com/v1 |
| AI_API_KEY | AI 서비스 키 |
| AI_MODEL | 계정에서 사용 가능한 모델 (기본 gpt-4.1-mini) |
| AI_TIMEOUT_SECONDS | 15 |

PORT는 Railway가 제공하며 SERVER_PORT는 별도로 고정하지 않는다. 환경변수 값과 인증정보는 GitHub나 채팅에 올리지 않는다. 먼저 MariaDB의 Volume/접속을 확인하고 Backend를 배포한다. DB 장애 시 Backend가 시작에 실패할 수 있으므로 DB 준비 후 재배포한다. 백엔드는 복제본 1개, sleep/serverless 비활성으로 시작한다.

## 공개 HTTPS 및 LINE

Backend의 Networking에서 Public Domain을 생성한다. Railway의 TLS 종료를 사용하므로 Nginx 서비스는 별도 생성하지 않는다. 실제 발급된 도메인으로 `/actuator/health` 응답 UP을 확인한다. 서명 없는 `/webhooks/line` POST가 401인지를 확인하고, LINE Console에는 `https://실제-발급-도메인/webhooks/line`을 등록한다.

LINE Console에서 Verify 성공 후 Use webhook과 redelivery를 켠다. 봇이 참가한 그룹에서 양방향 번역을 확인한다. URL은 배포 후 실제 발급된 값으로 안내해야 하며 미리 존재하는 것처럼 사용하지 않는다.

## 과금 및 계정 연결

Railway 계정/프로젝트 접근과 서비스 플랜이 필요하다. 서버·볼륨 사용료와 AI API 호출비는 별개이다. 유료 플랜/예산을 선택하기 전 실제 계정의 요금을 확인한다. 자동 휴면/체험 종료/사용량 제한은 Webhook 수신을 중단할 수 있으므로 운영 설정을 확인한다.

참고: https://docs.railway.com/networking/public-networking · https://docs.railway.com/builds/dockerfiles · https://railway.com/pricing
