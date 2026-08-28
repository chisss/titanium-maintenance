package com.titanium.maintenance.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;

class MaintenanceLocalAuthenticationFilterTest {

    private static final String JWT_SECRET = "titanium-admin-jwt-secret-key-2026-must-be-at-least-32-chars";
    private static final String INTERNAL_TOKEN = "test-maintenance-internal-token";

    private final MaintenanceLocalAuthenticationFilter filter =
            new MaintenanceLocalAuthenticationFilter(JWT_SECRET, INTERNAL_TOKEN);

    @Test
    void shouldBridgeLocalOperatorAndAuthoritiesForCurrentRequestOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Operator-Id", "qa-operator");
        request.addHeader("X-Authorities", "maintenance:config:view, maintenance:config:create");
        request.addHeader("X-Internal-Token", INTERNAL_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("qa-operator", authentication.getName());
            assertEquals(Set.of("maintenance:config:view", "maintenance:config:create"),
                    authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toSet()));
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateBearerAndExposeTenantForAuthorizationGate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken("operator-1", "tenant-1"));

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("operator-1", authentication.getName());
            assertEquals("tenant-1", authentication.getDetails());
            assertEquals(Set.of("maintenance:case:view"), authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .collect(Collectors.toSet()));
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldKeepRequestAnonymousWhenInternalCredentialIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Operator-Id", "operator-1");
        request.addHeader("X-Authorities", "maintenance:case:view");
        request.addHeader("X-Internal-Token", "invalid-token");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void shouldReauthenticateInternalRequestDuringAsyncDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader("X-Operator-Id", "operator-1");
        request.addHeader("X-Authorities", "maintenance:case:create");
        request.addHeader("X-Internal-Token", INTERNAL_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("operator-1", authentication.getName());
            assertEquals("tenant-1", authentication.getDetails());
            assertEquals(Set.of("maintenance:case:create"), authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .collect(Collectors.toSet()));
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private String accessToken(String operatorId, String tenantId) {
        return Jwts.builder()
                .claim("tokenType", "access")
                .claim("userId", operatorId)
                .claim("tenantId", tenantId)
                .claim("roles", Set.of("maintenance:case:view"))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
