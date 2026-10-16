# 관리자 화면

React + TypeScript + Vite. 대시보드, 대화 관리, AI 설정, 테스트 번역, 오류 로그를 제공합니다.

개발: frontend 폴더에서 npm ci, npm run dev. 주소는 http://localhost:5174/admin/ 이며 백엔드 8090으로 API를 전달합니다.
배포: 프로젝트 루트의 scripts/build.ps1 실행. Maven만 실행한다면 먼저 frontend에서 npm run build를 실행해야 정적 파일이 포함됩니다.

관리 계정은 Git에서 제외된 config/application.properties에 설정합니다. 세션 인증과 CSRF 검증을 적용하고 브라우저 저장소에 비밀번호나 API 키를 보관하지 않습니다.