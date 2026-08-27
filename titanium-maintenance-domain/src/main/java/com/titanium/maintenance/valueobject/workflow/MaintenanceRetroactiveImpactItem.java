package com.titanium.maintenance.valueobject.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 追溯时点之后的一条结构化跨域影响事实。 */
public record MaintenanceRetroactiveImpactItem(
        String itemId,
        MaintenanceRetroactiveImpactDomain sourceDomain,
        MaintenanceRetroactiveImpactType impactType,
        String referenceId,
        String referenceNumber,
        LocalDateTime occurredAt,
        String sourceStatus,
        BigDecimal amount,
        String currency,
        MaintenanceRetroactiveImpactSeverity severity,
        MaintenanceRetroactiveImpactItemStatus handlingStatus,
        String summary,
        String evidenceVersion,
        String evidenceHash) {

    private static final Set<MaintenanceRetroactiveImpactType> BILLING_TYPES = EnumSet.of(
            MaintenanceRetroactiveImpactType.PREMIUM_BILL, MaintenanceRetroactiveImpactType.RENEWAL);
    private static final Set<MaintenanceRetroactiveImpactType> PAYMENT_TYPES = EnumSet.of(
            MaintenanceRetroactiveImpactType.COLLECTION, MaintenanceRetroactiveImpactType.REFUND);
    private static final Set<MaintenanceRetroactiveImpactType> CLAIM_TYPES = EnumSet.of(
            MaintenanceRetroactiveImpactType.CLAIM, MaintenanceRetroactiveImpactType.BENEFIT);

    public MaintenanceRetroactiveImpactItem {
        itemId = requireText("itemId", itemId);
        referenceId = requireText("referenceId", referenceId);
        referenceNumber = normalize(referenceNumber);
        sourceStatus = requireText("sourceStatus", sourceStatus);
        summary = requireText("summary", summary);
        evidenceVersion = requireText("evidenceVersion", evidenceVersion);
        evidenceHash = requireHash(evidenceHash);
        currency = normalize(currency);
        if (sourceDomain == null || impactType == null || occurredAt == null
                || severity == null || handlingStatus == null) {
            throw invalid("item", "影响项分类、发生时间、严重度和处理状态不能为空");
        }
        validateType(sourceDomain, impactType);
        if ((amount == null) != (currency == null)) {
            throw invalid("amount", "影响金额和币种必须同时存在");
        }
        if (amount != null && amount.signum() < 0) {
            throw invalid("amount", "影响金额不能为负数");
        }
    }

    /** 只有尚未处理的阻断级影响才能阻止后续执行。 */
    public boolean blocksEffect() {
        return severity == MaintenanceRetroactiveImpactSeverity.BLOCKING
                && handlingStatus == MaintenanceRetroactiveImpactItemStatus.PENDING;
    }

    private static void validateType(
            MaintenanceRetroactiveImpactDomain domain,
            MaintenanceRetroactiveImpactType type) {
        boolean valid = switch (domain) {
            case POLICY -> type == MaintenanceRetroactiveImpactType.SUBSEQUENT_ENDORSEMENT;
            case BILLING -> BILLING_TYPES.contains(type);
            case PAYMENT -> PAYMENT_TYPES.contains(type);
            case CLAIM -> CLAIM_TYPES.contains(type);
        };
        if (!valid) {
            throw invalid("impactType", "影响类型与权威归属域不匹配");
        }
    }

    private static String requireHash(String value) {
        value = requireText("evidenceHash", value).toLowerCase();
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid("evidenceHash", "证据摘要必须是64位SHA-256");
        }
        return value;
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("MaintenanceRetroactiveImpactItem", field, message);
    }
}
