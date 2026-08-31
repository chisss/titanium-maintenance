package com.titanium.maintenance.web.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.titanium.maintenance.common.context.TenantContext;
import com.titanium.maintenance.common.exception.MaintenanceAuthenticationException;
import com.titanium.maintenance.common.exception.MaintenanceForbiddenException;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class MaintenanceConfigurationRequestContextResolverTest {

    private final MaintenanceConfigurationRequestContextResolver resolver =
            new MaintenanceConfigurationRequestContextResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldBuildOperationContextFromAuthenticationAndRequest() {
        authenticate("operator-1", "maintenance:config:edit", "maintenance:sensitive:view");
        TenantContext.setCurrentTenant("tenant-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Correlation-Id", "correlation-1");

        var resolved = resolver.require(request, "maintenance:config:edit");
        var operation = resolved.toOperationContext();

        assertThat(operation.tenantId()).isEqualTo("tenant-1");
        assertThat(operation.operatorId()).isEqualTo("operator-1");
        assertThat(operation.sourceIp()).isEqualTo("10.0.0.8");
        assertThat(operation.correlationId()).isEqualTo("correlation-1");
        assertThat(resolved.sensitiveDetailsVisible()).isTrue();
    }

    @Test
    void shouldFailClosedWhenPermissionIsMissing() {
        authenticate("operator-1", "maintenance:config:view");
        TenantContext.setCurrentTenant("tenant-1");

        assertThatThrownBy(() -> resolver.require(
                new MockHttpServletRequest(), "maintenance:config:publish"))
                .isInstanceOfSatisfying(MaintenanceForbiddenException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_FORBIDDEN.getCode()));
    }

    @Test
    void shouldFailClosedWhenAuthenticationIsMissing() {
        TenantContext.setCurrentTenant("tenant-1");

        assertThatThrownBy(() -> resolver.require(
                new MockHttpServletRequest(), "maintenance:config:view"))
                .isInstanceOfSatisfying(MaintenanceAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_UNAUTHENTICATED.getCode()));
    }

    private void authenticate(String operatorId, String... authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(
                operatorId, "N/A", AuthorityUtils.createAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
