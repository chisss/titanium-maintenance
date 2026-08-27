package com.titanium.maintenance.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class MaintenanceLocalAuthenticationFilterTest {

    private final MaintenanceLocalAuthenticationFilter filter = new MaintenanceLocalAuthenticationFilter();

    @Test
    void shouldBridgeLocalOperatorAndAuthoritiesForCurrentRequestOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Operator-Id", "qa-operator");
        request.addHeader("X-Authorities", "maintenance:config:view, maintenance:config:create");

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
}
