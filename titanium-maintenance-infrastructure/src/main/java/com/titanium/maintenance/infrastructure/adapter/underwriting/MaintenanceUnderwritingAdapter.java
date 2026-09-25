package com.titanium.maintenance.infrastructure.adapter.underwriting;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.maintenance.MaintenanceUnderwritingPort;
import com.titanium.metadata.enums.underwriting.MaintenanceUnderwritingConclusion;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.underwriting.api.MaintenanceUnderwritingApi;
import com.titanium.underwriting.api.request.maintenance.AssessMaintenanceUnderwritingRequest;
import com.titanium.underwriting.api.request.maintenance.AssessMaintenanceUnderwritingRequest.RiskFieldChangeRequest;
import com.titanium.underwriting.api.response.maintenance.MaintenanceUnderwritingResponse;

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
            ApiResponse<MaintenanceUnderwritingResponse> response = api.assess(toApiRequest(request));
            MaintenanceUnderwritingResponse body = requireSuccess(response);
            validateEcho(request, body);
            return new AssessmentFact(
                    body.underwritingCaseId(), body.idempotencyKey(), body.payloadHash(),
                    body.ruleVersion(), body.modelVersion(),
                    MaintenanceUnderwritingConclusion.fromCode(body.conclusion()),
                    body.additionalConditions(), body.summary(), body.completedAt());
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("调用保全核保失败, tenantId={}, maintenanceId={}, itemCode={}, errorType={}",
                    request.tenantId(), request.maintenanceId(), request.itemCode(),
                    exception.getClass().getSimpleName(), exception);
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

    /**
     * 解包统一信封并取有效载荷。
     * <p>
     * 🔴 m24-01a：核保契约统一返回 {@code ApiResponse} 信封。错误信封（或成功但无载荷）一律按
     * 「远程不可用」失败关闭——绝不通融为「无结论」，否则保全案件会把依赖故障读成业务事实。
     * </p>
     */
    private MaintenanceUnderwritingResponse requireSuccess(
            ApiResponse<MaintenanceUnderwritingResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw remoteError("Underwriting 未返回有效保全核保结果");
        }
        return response.getData();
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
