# 개발 PC와 운영 서버 분리

- 개발·빌드: `192.168.1.10`, `C:\ai\project\AI-Translator`
- 운영: Windows `192.168.1.120`, SSH 계정 `ds1120`
- 운영 설치 경로: `C:\ai\project\AI-Translator`
- Java: `C:\ai\runtime\java\bin\java.exe`
- Caddy: `C:\ai\caddy\caddy.exe`, 설정 `C:\ai\caddy\Caddyfile`
- 도메인: `https://dsss.mooo.com/admin/`

운영 서버에는 빌드한 JAR, 로컬 설정, H2 DB와 Java/Caddy 실행 파일을 배치한다. Node.js·Maven·소스 코드는 운영에 필요하지 않다. 최초 이전은 기존 백엔드를 중지한 상태에서 H2 파일을 복사했다. 이후 배포는 운영 DB와 설정을 덮어쓰지 않는다. 운영 중인 `.mv.db`를 단순 파일 복사하지 않는다.

## 개발 및 배포

개발 PC에서:

```powershell
cd C:\ai\project\AI-Translator
.\scripts\build.ps1
.\scripts\deploy-windows.ps1
```

배포 스크립트는 SSH 키 `~\.ssh\id_ai_translator_migration`으로 접속하여 JAR를 전송하고 운영 작업을 다시 시작한다. 기존 JAR은 `.previous`로 보관한다. 운영 비밀 설정·DB·인증서는 Git과 일반 배포에서 제외된다. SSH 공개키 등록은 최초 한 번 필요하다.

현재처럼 별도 경로에 빌드한 JAR을 배포하려면:

```powershell
.\scripts\deploy-windows.ps1 -JarPath backend\target\language-build\target\ai-translator-0.1.0.jar
```

개발 서버는 `.\scripts\start-dev.ps1`로 실행한다. 별도 Mock 프로필과 메모리 DB를 사용하여 운영 LINE/API 키를 읽지 않는다. 관리자 로그인 시험은 테스트 계정 설정을 별도로 제공해야 한다. 운영 설정 파일로 개발 PC에서 `java -jar`를 실행하면 운영 DB 복제본과 실제 키를 사용하므로 구분한다.

## 자동 시작과 로그

운영 서버 작업 스케줄러:

- `AI-Translator`: SYSTEM 계정으로 Java 실행, 재부팅 후 시작.
- `AI-Translator-Caddy`: SYSTEM 계정으로 Caddy 실행, 재부팅 후 시작.

운영 서버 관리자 PowerShell에서 관리한다:

```powershell
Get-ScheduledTask -TaskName 'AI-Translator*'
Stop-ScheduledTask -TaskName AI-Translator
Start-ScheduledTask -TaskName AI-Translator
Get-Content C:\ai\project\AI-Translator\logs\server.log -Tail 50
```

로그 위치:

- Java: `C:\ai\project\AI-Translator\logs\server.log`
- Caddy: `C:\ai\caddy\logs\runtime.log`, `access.log`

설정 파일, DB, 인증서 저장소는 SYSTEM/Administrators 접근으로 제한한다. 관리자 웹 아이디·비밀번호는 이전한 로컬 설정 파일 값을 사용한다. 키 변경 후에는 운영 작업을 중지하고 다시 시작한다. 서버 기동을 실제 확인한 뒤 작업하며, 같은 파일 DB로 Java를 두 번 실행하지 않는다.

## 네트워크와 이전한 Caddy

공유기 TCP 80·443의 내부 대상은 **192.168.1.120**으로 변경한다. Java 8090은 운영 서버의 loopback에만 바인딩하며 외부에 개방하지 않는다. 120번 서버 방화벽에는 HTTP/HTTPS 규칙을 등록했다. IP가 바뀌지 않도록 DHCP 예약 또는 고정 IP를 사용한다.

기존 개발 PC Caddy 자동 실행 작업 `TravelInterpreter-Caddy-Autostart` 및 오래된 인증서 동기화 작업은 비활성화했다. 실행 중인 Caddy를 정상 종료하고 기존 폴더는 `C:\ai\caddy-retired-20261016`으로 옮겼다. 포트 전환 후 기존 Caddy를 종료한 상태에서도 공개 HTTPS 200을 확인했다. 롤백 백업에는 인증서 개인키가 포함되므로 공유하거나 Git에 넣지 않는다.

기존 Qwen `/v1/*` API와 `/` 웹 화면은 별도 서비스라 운영 Caddy에서 개발 PC의 `192.168.1.10:8082` 및 `:5173`으로 연결한다. Gemini/Gemma 번역과 `/admin/`, LINE webhook, health는 운영 서버에서 동작하며 Qwen 웹 화면과 구분한다. 개발 PC가 꺼져 있으면 기존 Qwen API/UI 경로는 동작하지 않는다.

LAN HTTPS 검증 시 IP로 직접 접속하면 인증서 이름이 다르다. 도메인을 유지해 다음처럼 확인한다:

```powershell
curl.exe --resolve dsss.mooo.com:443:192.168.1.120 https://dsss.mooo.com/actuator/health
```
