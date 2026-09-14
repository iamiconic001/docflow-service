package com.suretyseven.docflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suretyseven.docflow.common.ApiResponse;
import com.suretyseven.docflow.constants.ResponseMessages;
import com.suretyseven.docflow.dto.request.LoginRequestDto;
import com.suretyseven.docflow.dto.response.LoginResponseDto;
import com.suretyseven.docflow.service.AuthService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        // Never log the raw password here; AuthService/GlobalExceptionHandler already
        // log failed-login attempts by username only.
        log.info("Login request received: username={}", loginRequestDto.getUsername());
        LoginResponseDto loginResponseDto = authService.login(loginRequestDto);
        log.info("Login request succeeded: username={}", loginRequestDto.getUsername());
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.LOGIN_SUCCESS, loginResponseDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.info("Logout request received");
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.LOGOUT_SUCCESS, null));
    }
}
