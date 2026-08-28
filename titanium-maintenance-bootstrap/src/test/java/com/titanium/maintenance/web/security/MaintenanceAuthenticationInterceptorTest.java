package com.titanium.maintenance.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MaintenanceAuthenticationInterceptorTest {

    private final MaintenanceAuthenticationInterceptor interceptor =
            new MaintenanceAuthenticationInterceptor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectAnonymousCaseApiRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request("tenant-1", null), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectJwtTenantMismatch() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator-1", "N/A", java.util.List.of());
        authentication.setDetails("tenant-1");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                request("tenant-2", "operator-1"), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldAllowMatchingAuthenticatedOperatorAndTenant() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator-1", "N/A", java.util.List.of());
        authentication.setDetails("tenant-1");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertTrue(interceptor.preHandle(
                request("tenant-1", "operator-1"), new MockHttpServletResponse(), new Object()));
    }

    private MockHttpServletRequest request(String tenantId, String operatorId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", tenantId);
        if (operatorId != null) {
            request.addHeader("X-Operator-Id", operatorId);
        }
        return request;
    }
}
