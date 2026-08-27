package com.titanium.maintenance.web.security;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.common.context.TenantContext;
import com.titanium.maintenance.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/** 从受信认证与请求上下文解析配置管理身份，不读取业务请求体中的身份字段。 */
@Component
@Slf4j
public class MaintenanceConfigurationRequestContextResolver {

    public static final String SENSITIVE_VIEW_PERMISSION = "maintenance:sensitive:view";
    public static final String AUDIT_TENANT_ATTRIBUTE =
            MaintenanceConfigurationRequestContextResolver.class.getName() + ".tenantId";
    public static final String AUDIT_OPERATOR_ATTRIBUTE =
            MaintenanceConfigurationRequestContextResolver.class.getName() + ".operatorId";
    public static final String AUDIT_SOURCE_IP_ATTRIBUTE =
            MaintenanceConfigurationRequestContextResolver.class.getName() + ".sourceIp";
    public static final String AUDIT_CORRELATION_ATTRIBUTE =
            MaintenanceConfigurationRequestContextResolver.class.getName() + ".correlationId";
    public static final String AUDIT_PERMISSION_ATTRIBUTE =
            MaintenanceConfigurationRequestContextResolver.class.getName() + ".permission";

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_CORRELATION_LENGTH = 128;

    public ResolvedRequestContext require(HttpServletRequest request, String permission) {
        String tenantId = TenantContext.getCurrentTenant();
        String sourceIp = hasText(request.getRemoteAddr()) ? request.getRemoteAddr().trim() : "UNKNOWN";
        String correlationId = correlationId(request.getHeader(CORRELATION_HEADER));
        request.setAttribute(AUDIT_TENANT_ATTRIBUTE, tenantId);
        request.setAttribute(AUDIT_SOURCE_IP_ATTRIBUTE, sourceIp);
        request.setAttribute(AUDIT_CORRELATION_ATTRIBUTE, correlationId);
        request.setAttribute(AUDIT_PERMISSION_ATTRIBUTE, permission);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !hasText(authentication.getName())) {
            throw accessDenied(request, tenantId, null, sourceIp, correlationId,
                    "未认证的配置管理请求", "MAINTENANCE_CONFIGURATION_UNAUTHENTICATED",
                    HttpStatus.UNAUTHORIZED);
        }
        request.setAttribute(AUDIT_OPERATOR_ATTRIBUTE, authentication.getName());
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> permission.equals(authority.getAuthority()))) {
            throw accessDenied(request, tenantId, authentication.getName(), sourceIp, correlationId,
                    "无权执行该保全配置操作", "MAINTENANCE_CONFIGURATION_FORBIDDEN",
                    HttpStatus.FORBIDDEN);
        }
        if (!hasText(tenantId)) {
            throw accessDenied(request, null, authentication.getName(), sourceIp, correlationId,
                    "认证请求缺少租户上下文", "MAINTENANCE_CONFIGURATION_TENANT_REQUIRED",
                    HttpStatus.UNAUTHORIZED);
        }
        if (tenantId.trim().length() > MAX_ID_LENGTH
                || authentication.getName().trim().length() > MAX_ID_LENGTH) {
            throw accessDenied(request, tenantId, authentication.getName(), sourceIp, correlationId,
                    "认证主体或租户标识超过长度限制",
                    "MAINTENANCE_CONFIGURATION_IDENTITY_INVALID", HttpStatus.BAD_REQUEST);
        }
        boolean sensitiveDetailsVisible = authentication.getAuthorities().stream()
                .anyMatch(authority -> SENSITIVE_VIEW_PERMISSION.equals(authority.getAuthority()));
        ResolvedRequestContext context = new ResolvedRequestContext(
                tenantId.trim(), authentication.getName().trim(), sourceIp,
                correlationId, sensitiveDetailsVisible);
        request.setAttribute(AUDIT_TENANT_ATTRIBUTE, context.tenantId());
        request.setAttribute(AUDIT_OPERATOR_ATTRIBUTE, context.operatorId());
        log.info("保全配置访问审计: method={}, path={}, tenantId={}, operatorId={}, permission={}, "
                        + "sensitiveView={}, sourceIp={}, correlationId={}, result=AUTHORIZED",
                request.getMethod(), request.getRequestURI(), context.tenantId(), context.operatorId(),
                permission, context.sensitiveDetailsVisible(), context.sourceIp(), context.correlationId());
        return context;
    }

    private String correlationId(String requestedValue) {
        if (hasText(requestedValue) && requestedValue.trim().length() <= MAX_CORRELATION_LENGTH) {
            return requestedValue.trim();
        }
        return UUID.randomUUID().toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException accessDenied(HttpServletRequest request, String tenantId, String operatorId,
            String sourceIp, String correlationId, String message, String errorCode, HttpStatus status) {
        log.warn("保全配置访问审计: method={}, path={}, tenantId={}, operatorId={}, sourceIp={}, "
                        + "correlationId={}, errorCode={}, result=DENIED",
                request.getMethod(), request.getRequestURI(), tenantId, operatorId,
                sourceIp, correlationId, errorCode);
        return new BusinessException(message, errorCode, status);
    }

    /** 单次管理请求解析出的可信身份与审计信息。 */
    public record ResolvedRequestContext(
            String tenantId,
            String operatorId,
            String sourceIp,
            String correlationId,
            boolean sensitiveDetailsVisible) {

        public MaintenanceConfigurationOperationContext toOperationContext() {
            return new MaintenanceConfigurationOperationContext(
                    tenantId, operatorId, sourceIp, correlationId, LocalDateTime.now());
        }
    }
}
