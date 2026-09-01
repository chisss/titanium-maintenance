package com.titanium.maintenance.port.billing;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductRecalculationEvidence;

/** Maintenance 调用 Billing 登记追溯期间调整的出口端口。 */
public interface BillingRetroactivePeriodAdjustmentPort {

    AdjustmentFact adjust(AdjustmentRequest request);

    record AdjustmentRequest(
            String tenantId,
            String maintenanceId,
            String policyId,
            String customerId,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            String adjustmentRequestId,
            String operatorId,
            MaintenanceRetroactiveProductRecalculationEvidence productEvidence) {

        public AdjustmentRequest {
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            customerId = text("customerId", customerId);
            analysisId = text("analysisId", analysisId);
            adjustmentRequestId = text("adjustmentRequestId", adjustmentRequestId);
            operatorId = text("operatorId", operatorId);
            if (analysisVersion < 1 || analysisResultHash == null || !analysisResultHash.matches("[0-9a-fA-F]{64}")
                    || productEvidence == null) {
                throw invalid("request", "Billing期间调整请求不完整");
            }
        }
    }

    record AdjustmentFact(
            String tenantId,
            String maintenanceId,
            String policyId,
            String customerId,
            String analysisId,
            int analysisVersion,
            String analysisResultHash,
            String recalculationId,
            String recalculationVersion,
            String productInputHash,
            String productResultHash,
            String batchId,
            String status,
            int postedCount,
            int reviewCount,
            String requestHash,
            String resultHash,
            LocalDateTime adjustedAt,
            List<MaintenanceRetroactiveBillingPeriodAdjustment> periods) {

        public AdjustmentFact {
            tenantId = text("tenantId", tenantId);
            maintenanceId = text("maintenanceId", maintenanceId);
            policyId = text("policyId", policyId);
            customerId = text("customerId", customerId);
            analysisId = text("analysisId", analysisId);
            recalculationId = text("recalculationId", recalculationId);
            recalculationVersion = text("recalculationVersion", recalculationVersion);
            batchId = text("batchId", batchId);
            status = text("status", status);
            periods = periods == null ? List.of() : List.copyOf(periods);
            if (analysisVersion < 1 || postedCount < 0 || reviewCount < 0 || adjustedAt == null) {
                throw invalid("fact", "Billing期间调整事实不完整");
            }
        }
    }

    private static String text(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("BillingRetroactivePeriodAdjustmentPort", field, message);
    }
}
