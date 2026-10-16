package com.aitranslator.admin;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestControllerAdvice(assignableTypes=AdminController.class)
public class AdminExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","입력 값을 확인해 주세요. URL·모델·시간 제한과 설정된 API 키를 확인하세요."));}
 @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> unavailable(){return ResponseEntity.status(503).body(Map.of("message","번역 요청을 완료하지 못했습니다. 오류 로그와 AI 서버 상태를 확인하세요."));}
}
