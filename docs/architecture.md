# Phase 1 구조

LINE → raw body HMAC 검증 → MyBatis DB 접수 → 200 OK → DB worker → 언어 감지 → TranslationService → TranslationProvider → LineClient.

TranslationService와 TranslationProvider는 LINE SDK에 의존하지 않는다. Request에는 문맥/옵션 확장 필드를 두었으나 Phase 1에서는 사용하지 않는다. 역번역·검증 상태·문맥 저장·관리자 명령은 미구현이며 Phase 2 이후 추가한다. 로컬 Qwen은 OpenAI 호환 API용 LocalQwenProvider로 연결한다. React + TypeScript + Vite UI는 Phase 3이다.

DB 큐로 접수 이후 재시작되어도 PENDING 작업은 남는다. 동일 webhookEventId는 PK로 7일간 차단한다. PROCESSING을 획득한 작업은 자동 재전송하지 않는다. 외부 전달과 DB 변경은 원자적이지 않아 네트워크 단절/프로세스 종료 시 exactly-once를 보장할 수 없다. 미확정 전달은 UNKNOWN으로 남겨 중복 회신보다 보수적인 전달 정책을 택한다. 10분 이상 중단된 PROCESSING도 UNKNOWN으로 정리한다. webhook DB 접수 실패는 5xx이며 LINE의 redelivery 설정이 필요하다.

원문과 Reply Token은 대기 작업 동안만 DB에 저장하고 모든 종료 상태에서 제거한다. 대화 문맥은 저장하지 않는다. 그룹 ID는 그룹 설정 테이블에 남고 사용자가 탈퇴한 그룹 삭제 정책은 운영에서 관리한다. 원문·토큰·AI 응답 본문을 로그에 남기지 않는다. 운영 DB 볼륨/백업은 암호화하고 접근을 제한해야 한다.

작업자는 한 번에 하나를 실행하는 MVP이다. 부하가 높으면 Reply Token이 만료될 수 있으므로 큐 지연을 감시한다. 토큰 수신 시점과 이벤트 발생 시점 모두 45초 이내일 때 Reply를 시도한다. 기본 Push 비활성. 활성화 시 5분 이내 이벤트만 만료된 Reply 대신 Push한다. Reply 실패 후 Push로 자동 전환하지 않는다. LINE 호출 횟수/오류 및 번역 처리 시간은 Micrometer에 기록하고 공개 HTTP 지표 엔드포인트는 열지 않는다.

LINE 봇 메시지는 일반적으로 웹훅 수신 대상이 아니며, bot user ID가 설정되어 있으면 source.userId도 명시적으로 제외한다. 그룹 텍스트·active mode만 처리하고 한국어/태국어 혼합, 다른 언어, 비텍스트, 슬래시 명령은 무시한다. 그룹 설정은 현재 DB에서만 관리하며 웹/채팅 설정 명령을 열지 않는다.

외부 AI는 OpenAI 호환 /chat/completions API. Timeout/HTTP 오류 시 작업 FAILED, 자동 AI 재시도는 하지 않아 중복 비용을 막는다. LINE Reply는 사용 횟수 제한 때문에 무조건 재시도하지 않는다. Push에는 이벤트 기반 retry key를 사용한다. 향후 재시도는 영속 outbox와 공급자별 정책을 추가해 구현한다.
