package com.titanium.maintenance.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.titanium.common.boundary.ErrorCategoryHttpStatus;
import com.titanium.common.context.RequestContextHolder;
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
import com.titanium.maintenance.common.exception.MaintenanceLegacyExecutionIndependentCaseForbiddenException;
import com.titanium.maintenance.common.exception.MaintenanceLegacyPremiumCalculationDisabledException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.MaintenanceSettlementConflictException;
import com.titanium.maintenance.common.exception.PolicyNotFoundException;
import com.titanium.maintenance.web.response.error.MaintenanceErrorVO;
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
 * HTTP 状态码由本处理器在传输层表达，分两条路径：
 * </p>
 * <ul>
 *   <li><b>具名异常</b>（{@code @ExceptionHandler(某异常类)}）：按<b>类型</b>显式映射——未找到类 404、
 *       冲突类 409、远程调用失败 502、认证/授权 401/403、配置写能力关闭 503 等。类型比错误码类别
 *       更特化，同一 advice 内 Spring 择一，故这些语义保持不变；</li>
 *   <li><b>其余 {@link DomainException}</b>：按错误码的<b>语义类别</b>映射
 *       （{@link ErrorCategoryHttpStatus}，全系统唯一一份「类别 → 状态」表），不再一律 400；
 *       响应体回显异常自身的错误码。</li>
 * </ul>
 * <p>
 * 本域信封为 {@link MaintenanceErrorVO}（与全仓 {@code ApiResponse} 形态不同，换信封属破坏性契约变更），
 * 故只复用状态映射、不复用 {@code ExceptionResponseFactory} 的信封构造。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.titanium.maintenance.web")
@Slf4j
public class MaintenanceExceptionHandler {

    private static final String CONFIGURATION_PATH = "/api/v1/maintenance/configurations";

    @ExceptionHandler({
            MaintenanceNotFoundException.class,
            PolicyNotFoundException.class,
            CustomerNotFoundException.class,
            MaintenanceConfigurationNotFoundException.class
    })
    public ResponseEntity<MaintenanceErrorVO> handleNotFoundException(
            DomainException exception, HttpServletRequest request) {
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
        return respond(exception.getErrorCodeValue(), exception.getMessage(), HttpStatus.CONFLICT, request);
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
        return respond(exception.getErrorCodeValue(), exception.getMessage(), HttpStatus.CONFLICT, request);
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
            DomainException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /** 独立建案误用旧版整案执行入口：两代案件通道互斥（D9），客户端应改走案件任务级生效链。 */
    @ExceptionHandler(MaintenanceLegacyExecutionIndependentCaseForbiddenException.class)
    public ResponseEntity<MaintenanceErrorVO> handleLegacyExecutionIndependentCaseForbidden(
            DomainException exception, HttpServletRequest request) {
        return respond(exception, HttpStatus.CONFLICT, request);
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

    /**
     * 未被上面各具体类型覆盖的业务异常 → 状态取错误码的语义类别。
     * <p>
     * 上面的 {@code @ExceptionHandler(具体异常类)} 是<b>按类型</b>的显式映射，比按错误码类别更特化，
     * Spring 在单个 advice 内按特化度择一，故它们仍然生效、语义不变。
     * 本方法是<b>兜底</b>：改造前此处写死 400，于是「资源不存在」「状态冲突」「服务端故障」
     * 这些没被上面列到的异常全被报成 400。
     * </p>
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<MaintenanceErrorVO> handleDomainException(
            DomainException exception, HttpServletRequest request) {
        return respond(exception, ErrorCategoryHttpStatus.of(exception.getErrorCode()), request);
    }

    /**
     * 方法级校验失败（{@code @Validated} 的形参约束）→ 400。
     * <p>
     * 🔴 <b>目标类型只剩 {@link ConstraintViolationException}</b>：改造前此处并列声明了 7 个类型，
     * 其中 6 个可由 {@code titanium-common} 的<b>边界处理器</b>按可赋值性接管，而边界 advice 序在前
     * （{@code HIGHEST_PRECEDENCE + 20} vs 本类无 {@code @Order}），故那 6 支<b>永不派发</b>——是死码。
     * 收窄后语义不变（这 6 类仍得 400），死码消失。
     * </p>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<MaintenanceErrorVO> handleInvalidRequest(
            ConstraintViolationException exception, HttpServletRequest request) {
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
            DomainException exception, HttpStatus status, HttpServletRequest request) {
        return respond(exception.getErrorCodeValue(), exception.getMessage(), status, request);
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
                RequestContextHolder.getTenantId());
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
