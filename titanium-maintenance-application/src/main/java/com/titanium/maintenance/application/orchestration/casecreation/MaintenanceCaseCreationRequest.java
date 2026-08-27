package com.titanium.maintenance.application.orchestration.casecreation;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/** 独立保全建案的应用层输入；客户标识必须从 Policy 快照派生。 */
public record MaintenanceCaseCreationRequest(
        String policyId,
        List<String> itemCodes,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        String description,
        String clientRequestKey,
        MaintenanceChannel source,
        String createdBy,
        String tenantId) {

    public MaintenanceCaseCreationRequest {
        policyId = requireText("policyId", policyId);
        clientRequestKey = requireText("clientRequestKey", clientRequestKey);
        createdBy = requireText("createdBy", createdBy);
        tenantId = requireText("tenantId", tenantId);
        if (itemCodes == null || itemCodes.isEmpty()) {
            throw validation("itemCodes", "至少选择一个保全项");
        }
        itemCodes = itemCodes.stream().map(code -> MaintenanceItemCode.of(code).value()).toList();
        if (itemCodes.size() > 10 || new LinkedHashSet<>(itemCodes).size() != itemCodes.size()) {
            throw validation("itemCodes", "保全项不能重复且单案最多选择10项");
        }
        if (effectiveTimeType == null) {
            throw validation("effectiveTimeType", "生效时间类型不能为空");
        }
        if (source == null) {
            throw validation("source", "受理来源不能为空");
        }
    }

    /** 兼容 M3-04 前的单主类型应用调用。 */
    public MaintenanceCaseCreationRequest(
            String policyId,
            MaintenanceType primaryMaintenanceType,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            String clientRequestKey,
            MaintenanceChannel source,
            String createdBy,
            String tenantId) {
        this(policyId,
                primaryMaintenanceType == null ? List.of() : List.of(primaryMaintenanceType.getCode()),
                effectiveTimeType, specificEffectiveDate, description, clientRequestKey,
                source, createdBy, tenantId);
    }

    public MaintenanceType primaryMaintenanceType() {
        return MaintenanceItemCode.of(itemCodes.getFirst()).legacyMaintenanceType();
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceCaseCreationRequest", fieldName, message);
    }
}
