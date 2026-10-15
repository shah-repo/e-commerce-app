package com.shah.gateway.security;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping({"/api/v1/auth/login", "/api/v1/login", "/auth/login", "/login"})
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping({"/api/v1/auth/register", "/api/v1/register", "/auth/register", "/register"})
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @GetMapping({"/api/v1/auth/validate", "/auth/validate"})
    public ResponseEntity<Boolean> validateToken(@RequestParam("token") String token) {
        return ResponseEntity.ok(jwtService.isTokenValid(token));
    }
}