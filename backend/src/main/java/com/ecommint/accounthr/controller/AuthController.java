package com.ecommint.accounthr.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.auth.AuthResponse;
import com.ecommint.accounthr.dto.auth.LoginRequest;
import com.ecommint.accounthr.dto.auth.RefreshRequest;
import com.ecommint.accounthr.dto.auth.UserResponse;
import com.ecommint.accounthr.service.AuthService;

import jakarta.validation.Valid;

/**
 * Kimlik doğrulama uçları: login / refresh / logout / me.
 * Hata yanıtları {"error":...,"message":...} formatındadır (bkz. GlobalExceptionHandler,
 * 401 entry point ve 403 access denied handler).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/login → 200 access + refresh + user; hatalı kimlik → 401. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** POST /api/auth/refresh → 200 yeni access + rotate edilmiş refresh; geçersiz → 401. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** POST /api/auth/logout → 204; verilen refresh token iptal edilir. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/auth/me → 200 mevcut kullanıcı; token yoksa/geçersizse → 401. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    /**
     * Method security kanıtı: yalnızca ADMIN erişebilir. ADMIN olmayan kimlik → 403.
     * (Placeholder uç; ilerideki admin uçları için örnek.)
     */
    @GetMapping("/admin-check")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.Map<String, Object> adminCheck(Authentication authentication) {
        return java.util.Map.of("ok", true, "user", authentication.getName());
    }
}
