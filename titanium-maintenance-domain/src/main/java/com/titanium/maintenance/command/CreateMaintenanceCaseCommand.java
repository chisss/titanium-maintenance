package com.titanium.maintenance.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.maintenance.valueobject.casecreation.MaintenanceCaseIdempotencyKey;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;
import com.titanium.maintenance.valueobject.item.MaintenanceItemCode;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/** 通过独立保全入口创建案件的领域命令。 */
public record CreateMaintenanceCaseCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        PolicyId policyId,
        CustomerId customerId,
        MaintenanceType primaryMaintenanceType,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        String description,
        PolicyMaintenanceSnapshot policySnapshot,
        MaintenanceCaseIdempotencyKey idempotencyKey,
        String createdBy,
        List<String> selectedItemCodes) {

    private static final String FINGERPRINT_VERSION_V1 = "maintenance-case-create:v1";
    private static final String FINGERPRINT_VERSION_V2 = "maintenance-case-create:v2";
    private static final int MAX_ITEMS_PER_CASE = 10;

    public CreateMaintenanceCaseCommand {
        if (id == null || policyId == null || customerId == null || primaryMaintenanceType == null
                || effectiveTimeType == null || policySnapshot == null || idempotencyKey == null
                || createdBy == null || createdBy.isBlank()
                || selectedItemCodes == null || selectedItemCodes.isEmpty()) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "独立保全建案必填参数不完整");
        }
        if (policyId.id() == null || policyId.id().isBlank()) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "policyId", "保单标识不能为空");
        }
        if (customerId.id() == null || customerId.id().isBlank()) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "customerId", "客户标识不能为空");
        }
        if (!id.equals(idempotencyKey.maintenanceId())) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "id", "案件标识必须由幂等键稳定派生");
        }
        if (!policyId.equals(policySnapshot.policyId())
                || !customerId.equals(policySnapshot.customerId())
                || !idempotencyKey.tenantId().equals(policySnapshot.tenantId())) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "policySnapshot", "Policy快照与建案标识不一致");
        }
        if (requiresSpecificDate(effectiveTimeType) && specificEffectiveDate == null) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "specificEffectiveDate", "当前生效类型必须提供指定生效时间");
        }
        selectedItemCodes = selectedItemCodes.stream()
                .map(code -> MaintenanceItemCode.of(code).value())
                .toList();
        if (selectedItemCodes.size() > MAX_ITEMS_PER_CASE
                || new LinkedHashSet<>(selectedItemCodes).size() != selectedItemCodes.size()) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "selectedItemCodes", "保全项不能重复且单案最多选择10项");
        }
        if (MaintenanceItemCode.of(selectedItemCodes.getFirst()).legacyMaintenanceType()
                != primaryMaintenanceType) {
            throw new MaintenanceValidationException(
                    "CreateMaintenanceCaseCommand", "primaryMaintenanceType", "首个保全项与旧主类型映射不一致");
        }
        createdBy = createdBy.trim();
    }

    /** 兼容 M3-04 前的单主类型命令构造。 */
    public CreateMaintenanceCaseCommand(
            MaintenanceId id,
            PolicyId policyId,
            CustomerId customerId,
            MaintenanceType primaryMaintenanceType,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            PolicyMaintenanceSnapshot policySnapshot,
            MaintenanceCaseIdempotencyKey idempotencyKey,
            String createdBy) {
        this(id, policyId, customerId, primaryMaintenanceType, effectiveTimeType,
                specificEffectiveDate, description, policySnapshot, idempotencyKey, createdBy,
                List.of(primaryMaintenanceType.getCode()));
    }

    public static CreateMaintenanceCaseCommand of(
            String policyId,
            MaintenanceType primaryMaintenanceType,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            PolicyMaintenanceSnapshot policySnapshot,
            String clientRequestKey,
            MaintenanceChannel source,
            String createdBy,
            String tenantId) {
        MaintenanceCaseIdempotencyKey idempotencyKey = new MaintenanceCaseIdempotencyKey(
                tenantId, source, clientRequestKey);
        return new CreateMaintenanceCaseCommand(
                idempotencyKey.maintenanceId(), PolicyId.of(policyId),
                policySnapshot == null ? null : policySnapshot.customerId(),
                primaryMaintenanceType, effectiveTimeType, specificEffectiveDate, description,
                policySnapshot, idempotencyKey, createdBy, List.of(primaryMaintenanceType.getCode()));
    }

    /** 创建支持多保全项且保留旧主类型兼容字段的独立建案命令。 */
    public static CreateMaintenanceCaseCommand of(
            String policyId,
            List<String> selectedItemCodes,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            PolicyMaintenanceSnapshot policySnapshot,
            String clientRequestKey,
            MaintenanceChannel source,
            String createdBy,
            String tenantId) {
        List<String> itemCodes = selectedItemCodes == null ? List.of() : List.copyOf(selectedItemCodes);
        MaintenanceType primaryType = itemCodes.isEmpty()
                ? null
                : MaintenanceItemCode.of(itemCodes.getFirst()).legacyMaintenanceType();
        MaintenanceCaseIdempotencyKey idempotencyKey = new MaintenanceCaseIdempotencyKey(
                tenantId, source, clientRequestKey);
        return new CreateMaintenanceCaseCommand(
                idempotencyKey.maintenanceId(), PolicyId.of(policyId),
                policySnapshot == null ? null : policySnapshot.customerId(),
                primaryType, effectiveTimeType, specificEffectiveDate, description,
                policySnapshot, idempotencyKey, createdBy, itemCodes);
    }

    /** 用于识别相同幂等键是否携带同一创建载荷。 */
    public String requestFingerprint() {
        MessageDigest digest = sha256();
        boolean legacySingleItem = selectedItemCodes.size() == 1
                && selectedItemCodes.getFirst().equals(primaryMaintenanceType.getCode());
        update(digest, legacySingleItem ? FINGERPRINT_VERSION_V1 : FINGERPRINT_VERSION_V2);
        update(digest, idempotencyKey.tenantId());
        update(digest, idempotencyKey.source().getCode());
        update(digest, policyId.id());
        update(digest, customerId.id());
        update(digest, primaryMaintenanceType.getCode());
        update(digest, effectiveTimeType.getCode());
        update(digest, specificEffectiveDate == null ? null : specificEffectiveDate.toString());
        update(digest, description);
        update(digest, createdBy);
        if (!legacySingleItem) {
            selectedItemCodes.forEach(itemCode -> update(digest, itemCode));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String tenantId() {
        return idempotencyKey.tenantId();
    }

    private static boolean requiresSpecificDate(EffectiveTimeType type) {
        return type == EffectiveTimeType.SPECIFIED_DATE
                || type == EffectiveTimeType.RETROACTIVE
                || type == EffectiveTimeType.FUTURE;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) -1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
