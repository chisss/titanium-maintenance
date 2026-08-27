package com.titanium.maintenance.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.titanium.maintenance.common.context.TenantContext;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.exception.MaintenanceConfigurationStateException;
import com.titanium.maintenance.web.response.MaintenanceErrorVO;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver;
import com.titanium.metadata.exception.DomainException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/** 将保全业务异常映射为稳定的 HTTP 状态与业务错误码。 */
@RestControllerAdvice(basePackages = "com.titanium.maintenance.web")
@Slf4j
public class MaintenanceExceptionHandler {

    private static final String CONFIGURATION_PATH = "/api/v1/maintenance/configurations";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<MaintenanceErrorVO> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        auditFailure(request, exception.getErrorCode(), exception.getStatus());
        return ResponseEntity.status(exception.getStatus())
                .body(new MaintenanceErrorVO(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MaintenanceConfigurationStateException.class)
    public ResponseEntity<MaintenanceErrorVO> handleConfigurationStateException(
            MaintenanceConfigurationStateException exception, HttpServletRequest request) {
        auditFailure(request, exception.getErrorCode(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new MaintenanceErrorVO(exception.getErrorCode(), exception.getMessage()));
    }

    /** 幂等载荷和任务状态冲突必须由客户端按 409 处理，而不是当作参数格式错误。 */
    @ExceptionHandler(MaintenanceConflictException.class)
    public ResponseEntity<MaintenanceErrorVO> handleConflict(
            MaintenanceConflictException exception, HttpServletRequest request) {
        auditFailure(request, "MAINTENANCE_CONFLICT", HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new MaintenanceErrorVO("MAINTENANCE_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<MaintenanceErrorVO> handleDomainException(
            DomainException exception, HttpServletRequest request) {
        auditFailure(request, exception.getErrorCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(new MaintenanceErrorVO(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<MaintenanceErrorVO> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        auditFailure(request, "MAINTENANCE_INVALID_REQUEST", HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(new MaintenanceErrorVO("MAINTENANCE_INVALID_REQUEST", "请求参数格式或约束校验失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MaintenanceErrorVO> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        auditFailure(request, "MAINTENANCE_INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        log.error("保全 Web 请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MaintenanceErrorVO("MAINTENANCE_INTERNAL_ERROR", "系统处理失败，请稍后重试"));
    }

    private void auditFailure(HttpServletRequest request, String errorCode, HttpStatus status) {
        if (!request.getRequestURI().startsWith(CONFIGURATION_PATH)) {
            return;
        }
        Object tenantId = valueOrDefault(
                request.getAttribute(MaintenanceConfigurationRequestContextResolver.AUDIT_TENANT_ATTRIBUTE),
                TenantContext.getCurrentTenant());
        Object sourceIp = valueOrDefault(
                request.getAttribute(MaintenanceConfigurationRequestContextResolver.AUDIT_SOURCE_IP_ATTRIBUTE),
                request.getRemoteAddr());
        Object correlationId = valueOrDefault(
                request.getAttribute(MaintenanceConfigurationRequestContextResolver.AUDIT_CORRELATION_ATTRIBUTE),
                request.getHeader("X-Correlation-Id"));
        log.warn("保全配置访问审计: method={}, path={}, tenantId={}, operatorId={}, permission={}, "
                        + "sourceIp={}, correlationId={}, status={}, errorCode={}, result=FAILED",
                request.getMethod(), request.getRequestURI(),
                tenantId,
                request.getAttribute(MaintenanceConfigurationRequestContextResolver.AUDIT_OPERATOR_ATTRIBUTE),
                request.getAttribute(MaintenanceConfigurationRequestContextResolver.AUDIT_PERMISSION_ATTRIBUTE),
                sourceIp, correlationId,
                status.value(), errorCode);
    }

    private Object valueOrDefault(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }
}
