package com.suretyseven.docflow.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.suretyseven.docflow.constants.AppConstants;
import com.suretyseven.docflow.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AppConstants.JWT_HEADER);

        // The token itself is a bearer credential and must never be logged, in full or in part.
        if (header != null && header.startsWith(AppConstants.JWT_PREFIX)) {
            String token = header.substring(AppConstants.JWT_PREFIX.length());
            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated request via JWT: username={}, path={}", username, request.getRequestURI());
            } else {
                log.warn("Rejected request with invalid or expired JWT: path={}", request.getRequestURI());
            }
        } else {
            log.debug("Request has no Bearer token: path={}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
