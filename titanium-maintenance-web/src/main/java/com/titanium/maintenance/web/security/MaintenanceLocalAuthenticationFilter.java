package com.titanium.maintenance.web.security;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 本地验收环境认证桥接；生产环境必须由统一认证网关建立可信身份。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "maintenance.local-auth.enabled", havingValue = "true")
public class MaintenanceLocalAuthenticationFilter extends OncePerRequestFilter {

    private static final String OPERATOR_HEADER = "X-Operator-Id";
    private static final String AUTHORITIES_HEADER = "X-Authorities";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String operatorId = request.getHeader(OPERATOR_HEADER);
        String authorityHeader = request.getHeader(AUTHORITIES_HEADER);
        if (hasText(operatorId) && hasText(authorityHeader)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authorities = Arrays.stream(authorityHeader.split(","))
                    .map(String::trim)
                    .filter(this::hasText)
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    operatorId.trim(), "N/A", authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
