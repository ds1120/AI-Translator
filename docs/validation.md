# 검증 기록 — 2026-10-16 UTC

- JDK 21 / Maven 3.9.9로 `mvn clean verify`: **BUILD SUCCESS**.
- 자동 테스트 **19개**, 실패 0 / 오류 0 / 생략 0 (H2 + Mock + 로컬 HTTP Provider).
- 실제 MariaDB **11.4.13** 컨테이너를 별도 기동하고 동일 Webhook 통합 테스트 **13개** 재실행: 실패 0 / 오류 0.
- Compose `config --quiet`: 통과 (테스트용 환경변수로 보간 확인).
- 실행 JAR로 Mock 서버 기동: 127.0.0.1:8080.
- 실제 HTTP `GET /actuator/health`: 200, `{"status":"UP"}`.
- 한국어 서명 웹훅 동일 이벤트 2회: 200 × 2, SENT × 1.
- 태국어 서명 웹훅 동일 이벤트 2회: 200 × 2, SENT × 1.
- 회신 문구의 정확한 Mock fixture 일치는 자동 통합 테스트에서 확인.

실제 LINE/유료 AI 인증정보가 없어 실계정 메시지 전송 및 번역 품질은 아직 검증하지 않았다. Docker Compose 전체 서비스 이미지 빌드/기동은 미실행이며 MariaDB 컨테이너와 Java JAR를 개별 실행해 연동 검증했다. 관리자 화면은 Phase 3 예정이며 React + TypeScript + Vite로 변경했다.

클라우드 환경에서 별도 JDK/Maven을 준비했다. 사용자 Windows에 자동 설치하거나 실행한 것은 아니다. 서버 프로세스와 임시 환경은 세션 종료 시 유지되지 않을 수 있다.
