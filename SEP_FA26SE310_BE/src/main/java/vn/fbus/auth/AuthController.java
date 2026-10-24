package vn.fbus.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.common.*;
import java.util.Map;

@RestController @RequestMapping("/api")
public class AuthController {
    private final AuthService service;
    private final AuthRepository repository;
    public AuthController(AuthService service, AuthRepository repository) { this.service=service; this.repository=repository; }
    @GetMapping("/health") ApiResponse<?> health() { return ApiResponse.ok(Map.of("status","UP")); }
    @PostMapping("/login") ApiResponse<?> login(@Valid @RequestBody Contracts.Login input) { return ApiResponse.ok(service.login(input)); }
    @PostMapping("/refresh-token") ApiResponse<?> refresh(@Valid @RequestBody Contracts.Refresh input) { return ApiResponse.ok(service.refresh(input.refreshToken())); }
    @PostMapping("/session/logout") ApiResponse<?> logout(@AuthenticationPrincipal AuthFilter.Identity identity) {
        repository.revoke(identity.sessionId()); return ApiResponse.ok(null);
    }
}
