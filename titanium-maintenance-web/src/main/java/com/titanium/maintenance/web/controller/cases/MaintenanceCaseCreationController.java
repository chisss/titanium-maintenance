package com.titanium.maintenance.web.controller.cases;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.maintenance.application.command.casecreation.CreateMaintenanceCaseInput;
import com.titanium.maintenance.application.command.casecreation.MaintenanceCaseCommandService;
import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput;
import com.titanium.maintenance.application.command.field.RecordMaintenanceFieldChangesInput.FieldProposalInput;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.web.dto.casecreation.CreateMaintenanceCaseDTO;
import com.titanium.maintenance.web.dto.field.RecordMaintenanceFieldChangesDTO;
import com.titanium.maintenance.web.response.casecreation.MaintenanceCaseCreationVO;
import com.titanium.metadata.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 保全案件建档入口：系统 API / 后台手工建案，以及建案后回写变更字段。
 *
 * <p>由原 {@code MaintenanceCaseController} 按业务主题拆分而来，端点 URL 与拆分前逐字一致。</p>
 */

@RestController
@Validated
@RequiredArgsConstructor
public class MaintenanceCaseCreationController extends MaintenanceCaseControllerSupport {

    private final MaintenanceCaseCommandService caseCommandService;

    /** 系统 API 自动建案；来源由路由固定，不接受请求体覆盖。 */
    @PostMapping("/api/v1/maintenance/cases")
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceCaseCreationVO>>> createByApi(
            @Valid @RequestBody CreateMaintenanceCaseDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return create(request, tenantId, operatorId, MaintenanceChannel.API);
    }

    /** 保全后台人工建案；来源由路由固定，不接受请求体覆盖。 */
    @PostMapping("/web/v1/maintenance/cases")
    public CompletableFuture<ResponseEntity<ApiResponse<MaintenanceCaseCreationVO>>> createManually(
            @Valid @RequestBody CreateMaintenanceCaseDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return create(request, tenantId, operatorId, MaintenanceChannel.MANUAL);
    }

    /** 系统 API 保存字段草稿；敏感字段值不在响应中回显。 */
    @PutMapping("/api/v1/maintenance/cases/{caseId}/items/{itemCode}/changes")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> recordApiFieldChanges(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody RecordMaintenanceFieldChangesDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return recordFieldChanges(caseId, itemCode, request, tenantId, operatorId);
    }

    /** 保全后台保存字段草稿；与 API 使用相同领域命令。 */
    @PutMapping("/web/v1/maintenance/cases/{caseId}/items/{itemCode}/changes")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> recordManualFieldChanges(
            @PathVariable @NotBlank @Size(max = 64) String caseId,
            @PathVariable @NotBlank @Size(max = 64) String itemCode,
            @Valid @RequestBody RecordMaintenanceFieldChangesDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        String operatorId = RequestContextHolder.requireOperatorId();
        return recordFieldChanges(caseId, itemCode, request, tenantId, operatorId);
    }

    private CompletableFuture<ResponseEntity<ApiResponse<MaintenanceCaseCreationVO>>> create(
            CreateMaintenanceCaseDTO request,
            String tenantId,
            String operatorId,
            MaintenanceChannel source) {
        CreateMaintenanceCaseInput input = new CreateMaintenanceCaseInput(
                request.policyId(), request.resolvedItemCodes(), request.effectiveTimeType(),
                request.specificEffectiveDate(), request.description(), request.clientRequestKey(),
                source, operatorId, tenantId);
        return caseCommandService.create(input)
                .thenApply(maintenanceId -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(new MaintenanceCaseCreationVO(maintenanceId))));
    }

    private CompletableFuture<ResponseEntity<ApiResponse<Void>>> recordFieldChanges(
            String caseId,
            String itemCode,
            RecordMaintenanceFieldChangesDTO request,
            String tenantId,
            String operatorId) {
        List<FieldProposalInput> proposals = request.proposals().stream()
                .map(proposal -> new FieldProposalInput(
                        proposal.objectId(), proposal.fieldCode(), proposal.dataType(), proposal.canonicalValue()))
                .toList();
        RecordMaintenanceFieldChangesInput input = new RecordMaintenanceFieldChangesInput(
                caseId, itemCode, proposals, operatorId, tenantId);
        return caseCommandService.recordFieldChanges(input)
                .thenApply(ignored -> ResponseEntity.ok(ApiResponse.success()));
    }
}
