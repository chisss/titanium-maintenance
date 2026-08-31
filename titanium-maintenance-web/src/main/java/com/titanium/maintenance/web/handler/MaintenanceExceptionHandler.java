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
import com.titanium.maintenance.common.exception.CustomerNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceAuthenticationException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationConflictException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationDependencyException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationFeatureDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationPreconditionFailedException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationStateException;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationValidationException;
import com.titanium.maintenance.common.exception.MaintenanceConflictException;
import com.titanium.maintenance.common.exception.MaintenanceForbiddenException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyCreationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyExecutionDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyPremiumCalculationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.MaintenanceSettlementConflictException;
import com.titanium.maintenance.common.exception.PolicyNotFoundException;
import com.titanium.maintenance.web.response.MaintenanceErrorVO;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * 将保全业务异常映射为稳定的 HTTP 状态与业务错误码。
 * <p>
 * 异常只携带 {@code BaseErrorCode} 业务错误码（红线 8.2：业务错误码 ≠ HTTP 状态码），
 * HTTP 状态码由本处理器按异常类型在传输层表达：未找到类 404、冲突类 409、远程调用失败 502、
 * 认证/授权 401/403、配置写能力关闭 503 等。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.titanium.maintenance.web")
@Slf4j
public class MaintenanceExceptionHandler {

    private static final String CONFIGURATION_PATH = "/api/v1/maintenance/configurations";

    /** 默认业务异常映射为 400（参数/规则不满足）。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<MaintenanceErrorVO> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({
            MaintenanceNotFoundException.class,
            PolicyNotFoundException.class,
            CustomerNotFoundException.class,
            MaintenanceConfigurationNotFoundException.class
    })
    public ResponseEntity<MaintenanceErrorVO> handleNotFoundException(
            BusinessException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(MaintenanceRemoteCallException.class)
    public ResponseEntity<MaintenanceErrorVO> handleRemoteCallException(
            MaintenanceRemoteCallException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.BAD_GATEWAY, request);
    }

    @ExceptionHandler(MaintenanceSettlementConflictException.class)
    public ResponseEntity<MaintenanceErrorVO> handleSettlementConflictException(
            MaintenanceSettlementConflictException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(MaintenanceConfigurationStateException.class)
    public ResponseEntity<MaintenanceErrorVO> handleConfigurationStateException(
            MaintenanceConfigurationStateException exception, HttpServletRequest request) {
        return respond(exception.getErrorCode(), exception.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(MaintenanceConfigurationConflictException.class)
    public ResponseEntity<MaintenanceErrorVO> handleConfigurationConflictException(
            MaintenanceConfigurationConflictException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.CONFLICT, request);
    }

    /** 幂等载荷和任务状态冲突必须由客户端按 409 处理，而不是当作参数格式错误。 */
    @ExceptionHandler(MaintenanceConflictException.class)
    public ResponseEntity<MaintenanceErrorVO> handleMaintenanceConflict(
            MaintenanceConflictException exception, HttpServletRequest request) {
        return respond(exception.getErrorCode(), exception.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(MaintenanceConfigurationPreconditionFailedException.class)
    public ResponseEntity<MaintenanceErrorVO> handlePreconditionFailed(
            MaintenanceConfigurationPreconditionFailedException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.PRECONDITION_FAILED, request);
    }

    @ExceptionHandler(MaintenanceConfigurationValidationException.class)
    public ResponseEntity<MaintenanceErrorVO> handleConfigurationValidationException(
            MaintenanceConfigurationValidationException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler({
            MaintenanceConfigurationDependencyException.class,
            MaintenanceConfigurationFeatureDisabledException.class,
            MaintenanceLegacyCreationDisabledException.class,
            MaintenanceLegacyExecutionDisabledException.class,
            MaintenanceLegacyPremiumCalculationDisabledException.class
    })
    public ResponseEntity<MaintenanceErrorVO> handleUnavailableException(
            BusinessException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(MaintenanceAuthenticationException.class)
    public ResponseEntity<MaintenanceErrorVO> handleAuthenticationException(
            MaintenanceAuthenticationException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(MaintenanceForbiddenException.class)
    public ResponseEntity<MaintenanceErrorVO> handleForbiddenException(
            MaintenanceForbiddenException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.FORBIDDEN, request);
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
        auditFailure(request, MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(new MaintenanceErrorVO(MaintenanceErrorCode.MAINTENANCE_INVALID_REQUEST.getCode(),
                        "请求参数格式或约束校验失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MaintenanceErrorVO> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        auditFailure(request, MaintenanceErrorCode.MAINTENANCE_INTERNAL_ERROR.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        log.error("保全 Web 请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MaintenanceErrorVO(MaintenanceErrorCode.MAINTENANCE_INTERNAL_ERROR.getCode(),
                        "系统处理失败，请稍后重试"));
    }

    private ResponseEntity<MaintenanceErrorVO> respond(
            BusinessException exception, HttpStatus status, HttpServletRequest request) {
        return respond(exception.getErrorCode(), exception.getMessage(), status, request);
    }

    private ResponseEntity<MaintenanceErrorVO> respond(
            String errorCode, String message, HttpStatus status, HttpServletRequest request) {
        auditFailure(request, errorCode, status);
        return ResponseEntity.status(status)
                .body(new MaintenanceErrorVO(errorCode, message));
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
