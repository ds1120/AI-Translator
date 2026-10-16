# Phase 2 구조

LINE → raw body HMAC 검증 → MyBatis DB 접수 → 200 OK → DB worker → 대화 설정·언어 감지·문맥 → TranslationService → 번역 Provider → ReverseTranslationChecker → VerificationProvider → 회신 포맷 → LineClient.

TranslationService와 검증·문맥 계층은 LINE에 의존하지 않는다. Request는 원문, 원문·목표 언어, 문맥, 옵션을 받는다. Result는 번역문·역번역·검증 상태·경고·처리 시간·번역/검증 모델·시도 수·차단 여부를 반환한다. 원문 언어를 확정할 수 없는 입력은 AUTO로 번역하고 검증 모델에 원문 언어 감지를 맡긴다.

## 품질 처리

1. TranslationProvider로 후보를 생성한다. 문맥은 별도 인용 데이터로 전달한다.
2. VerificationProvider가 별도 요청으로 역번역한다. 알려진 원문 언어와 역번역 언어가 맞지 않으면 검증 완료로 간주하지 않는다.
3. 별도 의미 평가 요청으로 숫자·시간·금액·이름·장소·부정·문장 의도·누락·추가를 검사한다. JSON 스키마와 경고 코드 허용 목록을 검사한다.
4. ReverseTranslationChecker가 원문·역번역의 숫자도 비교한다. 문자열 유사도를 의미 정확도로 사용하지 않는다.
5. FAIL이면 시간 예산 안에서 최대 한 번 재번역한다. 위험 판정은 수정본이 PASS로 검증돼야 해소한다. 해소되지 않은 위험 후보는 회신에 넣지 않는다.
6. 표시 설정에 맞춰 회신한다. 검증 불가·지속 FAIL을 정상 PASS로 숨기지 않는다.

번역·검증은 기본 같은 Qwen을 재사용하지만 검증 서버·모델·키를 분리할 수 있다. 다른 서버에 번역 키를 자동 전달하지 않는다. 기본은 3회, 재번역 포함 최대 6회 AI 호출이며 전체 예산 기본 30초, 최대 설정 35초로 제한한다. 오래 대기한 작업은 Reply 여유 시간에 맞춰 예산을 더 줄인다. 세부 정책과 제한은 [품질 가이드](phase2-quality.md)에 정리한다.

## 설정과 문맥

1:1 userId 또는 groupId를 대화 키로 사용한다. 기존 chat_groups는 번역 ON/OFF, chat_group_languages는 번역 쌍, chat_default_languages는 쌍 밖 메시지의 번역 대상, chat_quality_settings는 검증 표시·문맥 개수를 저장한다. 신규 테이블은 IF NOT EXISTS로 생성하므로 기존 데이터를 유지한다.

ConversationContextStore 인터페이스의 기본 구현은 RAM이다. 성공적으로 회신한 원문만 최근 최대 10개, 기본 600초 보관한다. 입력 문맥은 총 4,000자 이하, 전체 저장소는 최대 1,000개 대화로 제한한다. 이벤트 ID 중복을 제거하며 번역문·역번역·명령어·봇 메시지는 넣지 않는다. 접근 시와 30초 주기 정리로 만료한다. 문맥 OFF·번역 OFF·언어 쌍 변경 시 해당 대화 문맥을 비운다. 재시작하면 문맥 원문은 사라지지만 설정은 DB에 남는다. Redis는 필요 없다.

사용자의 후속 선택에 따라 설정 명령은 1:1 본인 또는 그룹 참여자 누구나 사용한다. 초기 지시서의 관리자 등록·권한 제한을 현재 명령에 적용하지 않는다. LINE 그룹 관리자 여부를 API에서 임의로 추정하지 않는다. 웹 관리 화면·인증은 Phase 3에서 구현하며 기존 React + TypeScript + Vite 계획을 유지한다.

## 이벤트·전달 보장

DB 큐의 PENDING 작업은 재시작 후 남으며 동일 webhookEventId를 7일간 차단한다. 작업 claim으로 하나의 worker가 처리한다. 외부 전달과 DB 변경은 원자적이지 않아 exactly-once를 보장하지 않는다. 전달이 불확실한 작업은 UNKNOWN으로 두며 자동 회신 재시도를 하지 않는다. 10분 이상 PROCESSING인 작업도 UNKNOWN으로 정리한다.

수신·이벤트 시각 모두 45초 이내일 때 Reply를 시도한다. 기본 Push는 OFF이며 활성화해도 5분 이내 이벤트만 대신 Push할 수 있다. Reply 실패 후 무조건 Push하지 않는다. Push는 이벤트 기반 retry key를 사용한다. DB 접수 실패는 5xx로 응답한다.

DB 작업의 원문·reply token·대화 ID는 종료 시 제거한다. 설정 테이블에는 대화 ID가 남는다. 문맥 ON이면 원문은 위 RAM 보관 정책에 따른다. 원문·AI 응답·Token·대화 식별자를 운영 로그에 기록하지 않는다. LINE 호출·오류, 처리 시간, 검증 상태는 내부 Micrometer 지표로 기록하며 공개 HTTP 지표 엔드포인트는 열지 않는다. health/webhook 외 웹 접근은 차단한다.
