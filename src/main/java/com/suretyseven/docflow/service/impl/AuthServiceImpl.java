package com.suretyseven.docflow.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.suretyseven.docflow.dto.request.LoginRequestDto;
import com.suretyseven.docflow.dto.response.LoginResponseDto;
import com.suretyseven.docflow.service.AuthService;
import com.suretyseven.docflow.util.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String configuredUsername;
    private final String configuredPasswordHash;

    public AuthServiceImpl(PasswordEncoder passwordEncoder,
                            JwtUtil jwtUtil,
                            @Value("${app.auth.username}") String configuredUsername,
                            @Value("${app.auth.password}") String configuredPassword) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.configuredUsername = configuredUsername;
        this.configuredPasswordHash = passwordEncoder.encode(configuredPassword);
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        boolean usernameMatches = configuredUsername.equals(loginRequestDto.getUsername());
        boolean passwordMatches = passwordEncoder.matches(loginRequestDto.getPassword(), configuredPasswordHash);

        if (!usernameMatches || !passwordMatches) {
            log.warn("Failed login attempt for username: {}", loginRequestDto.getUsername());
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(loginRequestDto.getUsername());
        log.info("User {} logged in successfully", loginRequestDto.getUsername());
        return new LoginResponseDto(token);
    }
}
