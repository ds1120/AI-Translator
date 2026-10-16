# LINE에 번역 봇 추가하기

계정 생성부터 실제 답장 확인까지의 최신 절차는 [README의 LINE 계정 생성부터 봇 연결까지](../README.md#line-계정-생성부터-봇-연결까지)에 통합했다.

## 관리 페이지

- [Business ID 로그인](https://account.line.biz/): 관리자 로그인 계정.
- [Official Account 생성](https://entry.line.biz/): 친구 추가할 공식 계정 생성.
- [Official Account Manager](https://manager.line.biz/): Messaging API 활성화와 자동응답 설정.
- [Developers Console](https://developers.line.biz/console/): 채널 Secret, Token, Webhook 관리.
- [현재 채널 2012046604](https://developers.line.biz/console/channel/2012046604): 해당 관리 계정의 로그인·권한 필요.

## 현재 로컬 연결값

| 항목 | 값 |
| --- | --- |
| 실제 설정 파일 | `config/application.properties` (Git 제외) |
| 실행 | 프로젝트 루트에서 `java -jar backend/target/ai-translator-0.1.0.jar` |
| 프로필 | `qwen` |
| 번역 서버 | `http://127.0.0.1:8090` |
| Qwen 추론 API | `http://127.0.0.1:8082/v1` |
| Webhook URL | `https://dsss.mooo.com/webhooks/line` |
| 상태 확인 | `https://dsss.mooo.com/actuator/health` |

1:1과 그룹 텍스트를 지원한다. `/설정`, `/언어 1`~`/언어 4`, `/기본언어 한국어` 등으로 대화별 설정을 변경한다. 기본 자동응답은 끄고 Webhook은 켜 둔다. Docker/Compose 실행은 별도 `.env`와 Nginx 8080 구성을 사용한다.

## 공식 문서

- [계정 생성과 Messaging API 활성화](https://developers.line.biz/en/docs/messaging-api/getting-started/)
- [Token·Webhook·자동응답 설정](https://developers.line.biz/en/docs/messaging-api/building-bot/)
- [Webhook Verify](https://developers.line.biz/en/docs/messaging-api/verify-webhook-url/)
- [그룹 참여](https://developers.line.biz/en/docs/messaging-api/group-chats/)
- [메시지 수신과 재전송](https://developers.line.biz/en/docs/messaging-api/receiving-messages/)
- [서명 검증](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/)
- [메시지 요금·집계](https://developers.line.biz/en/docs/messaging-api/pricing/)
