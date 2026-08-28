package com.titanium.maintenance.web.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 统一阻止匿名主体访问保全案件和配置数据。 */
@Component
public class MaintenanceAuthenticationInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String OPERATOR_HEADER = "X-Operator-Id";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "保全接口需要有效身份认证");
            return false;
        }
        String tenantId = request.getHeader(TENANT_HEADER);
        if (authentication.getDetails() instanceof String authenticatedTenant
                && hasText(authenticatedTenant) && !authenticatedTenant.equals(tenantId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "认证租户与请求租户不一致");
            return false;
        }
        String operatorId = request.getHeader(OPERATOR_HEADER);
        if (hasText(operatorId) && !operatorId.trim().equals(authentication.getName())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "认证操作人与请求操作人不一致");
            return false;
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
