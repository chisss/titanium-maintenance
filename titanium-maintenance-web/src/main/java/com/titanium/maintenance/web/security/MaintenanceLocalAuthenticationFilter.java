package com.titanium.maintenance.web.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 保全服务入口认证桥接：外部 API 校验 JWT，内部 BFF 校验共享凭据后建立可信身份。
 */
@Component
@ConditionalOnProperty(name = "maintenance.authentication.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceLocalAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String OPERATOR_HEADER = "X-Operator-Id";
    private static final String AUTHORITIES_HEADER = "X-Authorities";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /** JWT HMAC 签名密钥最小字节数（HS256 要求 ≥256 bit，与 admin 的 {@code JwtTokenProvider} 同口径） */
    private static final int MIN_JWT_SECRET_BYTES = 32;

    private final String jwtSecret;
    private final String internalToken;

    public MaintenanceLocalAuthenticationFilter(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${maintenance.authentication.internal-token}") String internalToken) {
        // 🔴 SEC-02（m28-08）：两个入参都是入口凭据，缺失即拒绝启动。
        // 为什么不能只在运行期降级：空 jwtSecret 会让 JWT 路径恒匿名、空 internalToken 会让
        // BFF 请求全量 401，两者都【不抛错、不打日志】，只表现为「所有请求都 401」——
        // 这种故障在部署后极难与「凭据不对」区分，故前移到启动期。
        // 错误消息写明环境变量名，便于部署侧直接定位缺失的是哪一个键。
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "维护域启动失败：未配置 jwt.secret，请通过环境变量 TITANIUM_ADMIN_JWT_SECRET 注入（与 admin 同源）");
        }
        int secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "维护域启动失败：jwt.secret 长度不足 " + MIN_JWT_SECRET_BYTES
                            + " 字节（HS256 要求 256 bit），当前为 " + secretBytes + " 字节");
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "维护域启动失败：未配置 maintenance.authentication.internal-token，"
                            + "请通过环境变量 MAINTENANCE_INTERNAL_TOKEN 注入");
        }
        this.jwtSecret = jwtSecret;
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateBearer(request);
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateInternalRequest(request);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateBearer(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(authorization.substring(BEARER_PREFIX.length()))
                    .getBody();
            if (!"access".equals(claims.get("tokenType", String.class))) {
                return;
            }
            String operatorId = claims.get("userId", String.class);
            String tenantId = claims.get("tenantId", String.class);
            List<?> roles = claims.get("roles", List.class);
            if (!hasText(operatorId) || !hasText(tenantId)) {
                return;
            }
            var authorities = roles == null ? List.<SimpleGrantedAuthority>of() : roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(this::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            setAuthentication(operatorId, tenantId, authorities);
        } catch (JwtException | IllegalArgumentException ignored) {
            // 无效令牌保持匿名，由统一入口门禁返回 401。
        }
    }

    private void authenticateInternalRequest(HttpServletRequest request) {
        if (!hasText(internalToken) || !internalToken.equals(request.getHeader(INTERNAL_TOKEN_HEADER))) {
            return;
        }
        String operatorId = request.getHeader(OPERATOR_HEADER);
        String authorityHeader = request.getHeader(AUTHORITIES_HEADER);
        if (!hasText(operatorId) || !hasText(authorityHeader)) {
            return;
        }
        var authorities = Arrays.stream(authorityHeader.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
        setAuthentication(operatorId.trim(), request.getHeader(TENANT_HEADER), authorities);
    }

    private void setAuthentication(
            String operatorId, String tenantId, List<SimpleGrantedAuthority> authorities) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                operatorId.trim(), "N/A", authorities);
        authentication.setDetails(tenantId);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
