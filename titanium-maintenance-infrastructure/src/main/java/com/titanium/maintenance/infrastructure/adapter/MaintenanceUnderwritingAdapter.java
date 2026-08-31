package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.MaintenanceUnderwritingPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.request.AssessMaintenanceUnderwritingRequest.RiskFieldChangeRequest;
import com.titanium.underwriting.api.response.MaintenanceUnderwritingResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Underwriting 保全风险结论的防腐适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceUnderwritingAdapter implements MaintenanceUnderwritingPort {

    private final MaintenanceUnderwritingApi api;

    @Override
    public AssessmentFact assess(AssessmentRequest request) {
        try {
            ResponseEntity<MaintenanceUnderwritingResponse> response = api.assess(toApiRequest(request),
                    request.tenantId());
            MaintenanceUnderwritingResponse body = requireSuccess(response);
            validateEcho(request, body);
            return new AssessmentFact(
                    body.underwritingCaseId(), body.idempotencyKey(), body.payloadHash(),
                    body.ruleVersion(), body.modelVersion(),
                    MaintenanceUnderwritingConclusion.fromCode(body.conclusion()),
                    body.additionalConditions(), body.summary(), body.completedAt());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("调用保全核保失败, tenantId={}, maintenanceId={}, itemCode={}, errorType={}",
                    request.tenantId(), request.maintenanceId(), request.itemCode(),
                    exception.getClass().getSimpleName());
            throw remoteError("Underwriting 保全核保不可用或契约无效");
        }
    }

    private AssessMaintenanceUnderwritingRequest toApiRequest(AssessmentRequest request) {
        return new AssessMaintenanceUnderwritingRequest(
                request.maintenanceId(), request.policyId(), request.policyBaselineVersion(),
                request.productId(), request.productVersion(), request.planVersion(), request.itemCode(),
                request.configurationVersion(), request.configurationContentHash(),
                request.configurationRequiresUnderwriting(),
                request.riskFieldChanges().stream()
                        .map(change -> new RiskFieldChangeRequest(
                                change.objectId(), change.fieldCode(), change.dataType(),
                                change.beforeValue(), change.proposedValue(), change.changeTypeCode()))
                        .toList(),
                request.idempotencyKey(), request.payloadHash(), request.requestedBy());
    }

    private MaintenanceUnderwritingResponse requireSuccess(
            ResponseEntity<MaintenanceUnderwritingResponse> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw remoteError("Underwriting 未返回有效保全核保结果");
        }
        return response.getBody();
    }

    private void validateEcho(AssessmentRequest request, MaintenanceUnderwritingResponse response) {
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.maintenanceId(), response.maintenanceId())
                || !Objects.equals(request.policyId(), response.policyId())
                || !Objects.equals(request.policyBaselineVersion(), response.policyBaselineVersion())
                || !Objects.equals(request.itemCode(), response.itemCode())
                || !Objects.equals(request.idempotencyKey(), response.idempotencyKey())
                || !Objects.equals(request.payloadHash(), response.payloadHash())) {
            throw remoteError("Underwriting 保全核保结果回显不一致");
        }
    }

    private MaintenanceRemoteCallException remoteError(String message) {
        return new MaintenanceRemoteCallException(message, MaintenanceErrorCode.MAINTENANCE_UNDERWRITING_REMOTE_ERROR);
    }
}
