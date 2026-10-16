# LINE에 번역 봇 추가하기

## 1. Official Account와 Messaging API 채널

1. [LINE Official Account](https://entry.line.biz/)에서 새 Official Account를 만든다.
2. [LINE Official Account Manager](https://manager.line.biz/) → 해당 계정 → 설정 → Messaging API에서 **Messaging API 사용**을 활성화하고 Provider를 선택한다.
3. [LINE Developers Console](https://developers.line.biz/console/) → 선택한 Provider → Messaging API 채널을 연다. 현재는 Console에서 새 Messaging API 채널을 직접 만드는 대신 Official Account Manager를 통해 활성화한다.
4. **Basic settings → Channel secret**을 `LINE_CHANNEL_SECRET`으로 설정한다.
5. **Messaging API → Channel access token (long-lived)**에서 발급하고 `LINE_CHANNEL_ACCESS_TOKEN`으로 설정한다.
6. Bot의 user ID를 알고 있다면 `LINE_BOT_USER_ID`로 설정한다. Channel ID나 Basic ID(@...)를 user ID로 사용하지 않는다.

Secret/Token/AI 키를 Git, 캡처 화면, 로그, 공개 채팅에 붙이지 않는다. `.env`에만 저장한다.

## 2. 서버와 HTTPS 연결

- `docker compose up --build -d`로 실제 API/live 모드를 실행한다. `.env`에 AI 키와 DB 비밀번호도 있어야 한다.
- 로컬 상태 확인: `http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- 개발 중에는 HTTPS 터널(예: 설치된 ngrok의 `ngrok http 8080`)을 사용하거나 공인 HTTPS reverse proxy를 구성한다.
- 터널/프록시는 raw body와 `X-Line-Signature`를 바꾸지 않고 전달해야 한다.
- LINE Console → Messaging API → Webhook settings:
  - URL: `https://발급받은-주소/webhooks/line`
  - **Verify** 성공 확인 (events가 비어 있는 요청도 지원).
  - **Use webhook** 활성화.
  - 필요 시 **Webhook redelivery** 활성화. 동일 이벤트는 DB에서 중복 차단.
- Official Account Manager의 자동 응답/인사 메시지는 꺼 두면 봇 번역과 기본 응답이 겹치지 않는다.

## 3. 친구 추가와 그룹 초대

1. LINE Console의 Messaging API 화면에 있는 **QR code / Basic ID**로 봇을 본인 LINE 친구에 추가한다.
2. Console → Messaging API → **Allow bot to join group chats**를 켠다 (그룹 및 여러 사용자 채팅 참여 허용).
3. 휴대폰 LINE에서 사용할 그룹 → 메뉴 → 초대 → 친구 목록의 봇 계정을 선택한다.
4. 봇이 들어온 그룹에서 `내일 아침 9시에 출발할게.`를 입력한다.
5. 같은 그룹에 `🇹🇭` 태국어 번역이 오면 한국어→태국어 연결 성공. `พรุ่งนี้เจอกันตอนสิบโมงนะ`로 반대 방향도 확인한다.

그룹에는 보통 Official Account 봇을 한 개만 둘 수 있으므로 다른 봇이 있으면 LINE 정책을 확인한다. 이 서비스의 `/번역 켜기` 등 관리자 명령은 Phase 2 이후 구현 예정이며 지금은 입력해도 동작하지 않는다. LINE 그룹 관리자 권한을 API에서 자동 확인한다고 가정하지 않는다.

## 4. 응답이 없을 때

- Mock 모드면 실제 LINE으로 보내지 않는다. live 프로파일과 키 설정을 확인한다.
- Verify 실패: HTTPS URL, 경로, 채널 Secret, body 변조 여부 및 서버 로그 확인.
- 401: Secret/서명 문제. Token과 Secret은 서로 다른 값이다.
- Webhook은 200인데 번역 없음: 로그의 FAILED / EXPIRED / UNKNOWN 확인. AI 키/모델 권한, LINE Token, 응답 지연을 확인한다.
- 한국어/태국어 이외 메시지·혼합 메시지·비텍스트·개인 채팅은 의도적으로 무시한다.
- 오래된 메시지는 기본 Push하지 않는다. Push는 유료 메시지 수량에 영향을 줄 수 있어 명시적으로 활성화해야 한다.
- 재시작 후 중복 발생: MariaDB 볼륨 유지 여부 확인. Mock 메모리 DB는 재시작 시 중복 기록을 잃는다.

## 공식 문서

- [Messaging API 시작](https://developers.line.biz/en/docs/messaging-api/getting-started/)
- [메시지 수신과 재전송](https://developers.line.biz/en/docs/messaging-api/receiving-messages/)
- [서명 검증](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/)
- [Reply API](https://developers.line.biz/en/reference/messaging-api/#send-reply-message)
- [그룹 채팅](https://developers.line.biz/en/docs/messaging-api/group-chats/)

Reply Token은 한 번만 사용할 수 있고 신속히 사용해야 한다. 이 MVP는 45초 안전 여유를 두며 유효기간이 보장된다고 가정하지 않는다. 실제 운영 전 최신 공식 문서와 계정별 요금/제한을 확인한다.
