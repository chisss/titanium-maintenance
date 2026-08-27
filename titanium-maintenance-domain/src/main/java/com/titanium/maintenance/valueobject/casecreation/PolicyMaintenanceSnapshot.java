package com.titanium.maintenance.valueobject.casecreation;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;
import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

/** Policy 提供并由案件冻结的不可变建案基准快照。 */
public record PolicyMaintenanceSnapshot(
        String tenantId,
        PolicyId policyId,
        String policyNumber,
        CustomerId customerId,
        String productId,
        String productVersion,
        String planVersion,
        PolicyStatus policyStatus,
        long policyVersion,
        OffsetDateTime businessEffectiveAt,
        OffsetDateTime nextBillingDateAt,
        OffsetDateTime nextPolicyAnniversaryAt,
        MaintenanceSnapshotReference beforeSnapshot,
        Map<String, MaintenanceFieldValue> fieldValues) {

    public PolicyMaintenanceSnapshot {
        tenantId = requireText("tenantId", tenantId);
        requireIdentifier("policyId", policyId == null ? null : policyId.id());
        policyNumber = requireText("policyNumber", policyNumber);
        requireIdentifier("customerId", customerId == null ? null : customerId.id());
        productId = requireText("productId", productId);
        productVersion = requireText("productVersion", productVersion);
        planVersion = requireText("planVersion", planVersion);
        if (policyStatus == null) {
            throw validation("policyStatus", "保单状态不能为空");
        }
        if (policyVersion < 0) {
            throw validation("policyVersion", "保单基准版本不能为负数");
        }
        if (businessEffectiveAt == null) {
            throw validation("businessEffectiveAt", "业务有效时点不能为空");
        }
        if (beforeSnapshot == null) {
            throw validation("beforeSnapshot", "变更前快照引用不能为空");
        }
        if (beforeSnapshot.policyVersion() != policyVersion) {
            throw validation("beforeSnapshot", "快照引用版本与保单基准版本不一致");
        }
        fieldValues = immutableFields(fieldValues);
    }

    /** 兼容 M5-04 之前不含未来计划日期的事件和测试构造。 */
    public PolicyMaintenanceSnapshot(
            String tenantId,
            PolicyId policyId,
            String policyNumber,
            CustomerId customerId,
            String productId,
            String productVersion,
            String planVersion,
            PolicyStatus policyStatus,
            long policyVersion,
            OffsetDateTime businessEffectiveAt,
            MaintenanceSnapshotReference beforeSnapshot,
            Map<String, MaintenanceFieldValue> fieldValues) {
        this(tenantId, policyId, policyNumber, customerId, productId, productVersion, planVersion,
                policyStatus, policyVersion, businessEffectiveAt, null, null, beforeSnapshot, fieldValues);
    }

    public boolean active() {
        return policyStatus == PolicyStatus.EFFECTIVE;
    }

    /**
     * 判断两次 Policy 读取是否指向同一业务基准。
     *
     * <p>快照采集时间是读取时元数据，同一保单版本重试时可以不同；版本、引用摘要和结构化字段
     * 才是幂等建案需要保护的业务事实。</p>
     */
    public boolean sameBusinessBaseline(PolicyMaintenanceSnapshot other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(policyId, other.policyId)
                && Objects.equals(policyNumber, other.policyNumber)
                && Objects.equals(customerId, other.customerId)
                && Objects.equals(productId, other.productId)
                && Objects.equals(productVersion, other.productVersion)
                && Objects.equals(planVersion, other.planVersion)
                && Objects.equals(policyStatus, other.policyStatus)
                && policyVersion == other.policyVersion
                && Objects.equals(businessEffectiveAt, other.businessEffectiveAt)
                && Objects.equals(nextBillingDateAt, other.nextBillingDateAt)
                && Objects.equals(nextPolicyAnniversaryAt, other.nextPolicyAnniversaryAt)
                && sameReference(beforeSnapshot, other.beforeSnapshot)
                && Objects.equals(fieldValues, other.fieldValues);
    }

    private boolean sameReference(MaintenanceSnapshotReference left, MaintenanceSnapshotReference right) {
        return Objects.equals(left.storageKey(), right.storageKey())
                && Objects.equals(left.contentHash(), right.contentHash())
                && left.policyVersion() == right.policyVersion();
    }

    private static Map<String, MaintenanceFieldValue> immutableFields(
            Map<String, MaintenanceFieldValue> values) {
        if (values == null || values.isEmpty()) {
            throw validation("fieldValues", "结构化变更前快照不能为空");
        }
        TreeMap<String, MaintenanceFieldValue> sorted = new TreeMap<>();
        values.forEach((fieldCode, fieldValue) -> {
            String normalizedCode = requireText("fieldValues", fieldCode);
            if (!normalizedCode.equals(fieldCode) || fieldValue == null) {
                throw validation("fieldValues", "字段编码和值必须规范且非空");
            }
            sorted.put(normalizedCode, fieldValue);
        });
        return Collections.unmodifiableMap(sorted);
    }

    private static void requireIdentifier(String fieldName, String value) {
        requireText(fieldName, value);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("PolicyMaintenanceSnapshot", fieldName, message);
    }
}
