package com.titanium.maintenance.web.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.maintenance.application.command.configuration.MaintenanceConfigurationCommandService;
import com.titanium.maintenance.application.query.MaintenanceConfigurationQueryService;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationDTO;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationDecisionDTO;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationRevisionDTO;
import com.titanium.maintenance.web.dto.configuration.MaintenanceConfigurationValidationDTO;
import com.titanium.maintenance.web.mapper.MaintenanceConfigurationWebMapper;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationAuditPageVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationPageVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationPreviewVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationVO;
import com.titanium.maintenance.web.response.configuration.MaintenanceConfigurationValidationVO;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver;
import com.titanium.maintenance.web.security.MaintenanceConfigurationRequestContextResolver.ResolvedRequestContext;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 独立于保单查询入口的保全项配置管理 API。 */
@RestController
@RequestMapping("/api/v1/maintenance/configurations")
@Validated
@RequiredArgsConstructor
public class MaintenanceConfigurationController {

    private static final String VIEW = "maintenance:config:view";
    private static final String CREATE = "maintenance:config:create";
    private static final String EDIT = "maintenance:config:edit";
    private static final String SUBMIT = "maintenance:config:submit";
    private static final String APPROVE = "maintenance:config:approve";
    private static final String PUBLISH = "maintenance:config:publish";
    private static final String RETIRE = "maintenance:config:retire";

    private final MaintenanceConfigurationCommandService commandService;
    private final MaintenanceConfigurationQueryService queryService;
    private final MaintenanceConfigurationWebMapper mapper;
    private final MaintenanceConfigurationRequestContextResolver contextResolver;

    @PostMapping
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> create(
            @Valid @RequestBody MaintenanceConfigurationDTO request,
            HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, CREATE);
        StoredConfiguration stored = commandService.createDraft(
                UUID.randomUUID().toString(), mapper.toDefinition(request),
                request.validFrom(), request.validTo(), context.toOperationContext());
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code；
        // LOCATION/ETag 是资源定位与乐观锁契约，不因信封化省略
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/v1/maintenance/configurations/"
                        + stored.configuration().getConfigurationId())
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toVO(stored, context.sensitiveDetailsVisible())));
    }

    @PutMapping("/{configurationId}")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> replaceDraft(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, EDIT);
        StoredConfiguration stored = commandService.updateDraft(
                configurationId, expectedVersion(ifMatch), mapper.toDefinition(request),
                request.validFrom(), request.validTo(), context.toOperationContext());
        return ok(stored, context);
    }

    @DeleteMapping("/{configurationId}")
    public ApiResponse<Void> deleteDraft(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, EDIT);
        commandService.deleteDraft(
                configurationId, expectedVersion(ifMatch), context.toOperationContext());
        return ApiResponse.success();
    }

    @PostMapping("/{configurationId}/validate")
    public ApiResponse<MaintenanceConfigurationValidationVO> validate(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @Valid @RequestBody MaintenanceConfigurationValidationDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        return ApiResponse.success(mapper.toValidationVO(commandService.validate(
                configurationId, mapper.toCriteria(request), context.toOperationContext())));
    }

    @PostMapping("/{configurationId}/submit")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> submit(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationValidationDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, SUBMIT);
        StoredConfiguration stored = commandService.submitForApproval(
                configurationId, expectedVersion(ifMatch), mapper.toCriteria(request),
                context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/approve")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> approve(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationValidationDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, APPROVE);
        StoredConfiguration stored = commandService.approve(
                configurationId, expectedVersion(ifMatch), mapper.toCriteria(request),
                context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/reject")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> reject(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationDecisionDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, APPROVE);
        StoredConfiguration stored = commandService.reject(
                configurationId, expectedVersion(ifMatch), request.reason(),
                context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/return-to-draft")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> returnToDraft(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationDecisionDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, APPROVE);
        StoredConfiguration stored = commandService.returnToDraft(
                configurationId, expectedVersion(ifMatch), request.reason(),
                context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/publish")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> publish(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         @Valid @RequestBody MaintenanceConfigurationValidationDTO request,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, PUBLISH);
        StoredConfiguration stored = commandService.publish(
                configurationId, expectedVersion(ifMatch), mapper.toCriteria(request),
                context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/retire")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> retire(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, RETIRE);
        StoredConfiguration stored = commandService.retire(
                configurationId, expectedVersion(ifMatch), context.toOperationContext());
        return ok(stored, context);
    }

    @PostMapping("/{configurationId}/revisions")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> createRevision(
            @PathVariable @NotBlank @Size(max = 64) String configurationId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody MaintenanceConfigurationRevisionDTO request,
            HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, CREATE);
        StoredConfiguration stored = commandService.createRevision(
                configurationId, expectedVersion(ifMatch), UUID.randomUUID().toString(),
                request.version(), request.validFrom(), request.validTo(), context.toOperationContext());
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code；
        // LOCATION/ETag 是资源定位与乐观锁契约，不因信封化省略
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/v1/maintenance/configurations/"
                        + stored.configuration().getConfigurationId())
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toVO(stored, context.sensitiveDetailsVisible())));
    }

    @GetMapping("/{configurationId}")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> get(
            @PathVariable @NotBlank @Size(max = 64) String configurationId,
            HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        StoredConfiguration stored = queryService.get(context.tenantId(), configurationId);
        return ResponseEntity.ok()
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toVO(stored, context.sensitiveDetailsVisible())));
    }

    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> resolveEffective(
            @RequestParam @NotBlank @Size(max = 64) String itemCode,
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime businessTime,
            HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        StoredConfiguration stored = queryService.resolveEffective(
                context.tenantId(), itemCode, businessTime);
        return ResponseEntity.ok()
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toVO(stored, context.sensitiveDetailsVisible())));
    }

    @GetMapping("/{configurationId}/preview")
    public ResponseEntity<ApiResponse<MaintenanceConfigurationPreviewVO>> preview(
            @PathVariable @NotBlank @Size(max = 64) String configurationId,
            HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        StoredConfiguration stored = queryService.get(context.tenantId(), configurationId);
        return ResponseEntity.ok()
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toPreviewVO(stored, context.sensitiveDetailsVisible())));
    }

    @GetMapping
    public ApiResponse<MaintenanceConfigurationPageVO> search(
         @RequestParam(required = false) @Size(max = 64) String itemCode,
         @RequestParam(required = false) MaintenanceItemConfigurationStatus status,
         @RequestParam(required = false)
         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
         @RequestParam(defaultValue = "0") @Min(0) int page,
         @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        return ApiResponse.success(mapper.toPageVO(queryService.search(
                context.tenantId(), new ConfigurationSearchCriteria(
                        itemCode, status, effectiveAt, page, size))));
    }

    @GetMapping("/{configurationId}/audits")
    public ApiResponse<MaintenanceConfigurationAuditPageVO> auditHistory(
         @PathVariable @NotBlank @Size(max = 64) String configurationId,
         @RequestParam(defaultValue = "0") @Min(0) int page,
         @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
         HttpServletRequest servletRequest) {
        ResolvedRequestContext context = contextResolver.require(servletRequest, VIEW);
        return ApiResponse.success(mapper.toAuditPageVO(
                queryService.findAuditHistory(context.tenantId(), configurationId, page, size),
                context.sensitiveDetailsVisible()));
    }

    /**
     * 配置回执统一形态：200 + 强 ETag + 信封体。
     * <p>ETag 承载乐观锁版本，是客户端下次 If-Match 的唯一来源，信封化不得吞掉它。</p>
     */
    private ResponseEntity<ApiResponse<MaintenanceConfigurationVO>> ok(
            StoredConfiguration stored, ResolvedRequestContext context) {
        return ResponseEntity.ok()
                .eTag(etag(stored.rowVersion()))
                .body(ApiResponse.success(mapper.toVO(stored, context.sensitiveDetailsVisible())));
    }

    private long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.startsWith("W/")) {
            throw invalidEtag();
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw invalidEtag();
        }
        try {
            long version = Long.parseLong(value.substring(1, value.length() - 1));
            if (version < 0) {
                throw invalidEtag();
            }
            return version;
        } catch (NumberFormatException exception) {
            throw invalidEtag();
        }
    }

    private DomainException invalidEtag() {
        return new DomainException(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_INVALID_ETAG, "If-Match 必须是当前响应返回的强 ETag");
    }

    private String etag(long rowVersion) {
        return "\"" + rowVersion + "\"";
    }
}
