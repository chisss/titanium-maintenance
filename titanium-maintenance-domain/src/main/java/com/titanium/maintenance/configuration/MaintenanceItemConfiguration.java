package com.titanium.maintenance.configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.exception.MaintenanceConfigurationStateException;

import lombok.Getter;

/**
 * 保全项配置聚合。
 *
 * <p>该聚合采用 Repository 持久化，负责版本生命周期、有效期、内容证据和操作审计，不进入案件事件流。</p>
 */
@Getter
public final class MaintenanceItemConfiguration {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String configurationId;
    private final String tenantId;
    private final String revisionOfConfigurationId;
    private MaintenanceItemDefinition definition;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private MaintenanceItemConfigurationStatus status;
    private String contentHash;
    private MaintenancePublicationEvidence publicationEvidence;
    private List<MaintenanceConfigurationAuditEntry> auditTrail;

    private MaintenanceItemConfiguration(String configurationId, String tenantId,
            String revisionOfConfigurationId, MaintenanceItemDefinition definition,
            LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceItemConfigurationStatus status, String contentHash,
            MaintenancePublicationEvidence publicationEvidence,
            List<MaintenanceConfigurationAuditEntry> auditTrail) {
        this.configurationId = requireText("configurationId", configurationId);
        this.tenantId = requireText("tenantId", tenantId);
        this.revisionOfConfigurationId = normalize(revisionOfConfigurationId);
        this.definition = requireValue("definition", definition);
        validateEffectivePeriod(validFrom, validTo);
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = requireValue("status", status);
        this.contentHash = normalizeHash(status, contentHash);
        this.publicationEvidence = validatePublicationEvidence(status, publicationEvidence);
        this.auditTrail = immutableAuditTrail(auditTrail);
    }

    /** 创建独立业务版本的配置草稿。 */
    public static MaintenanceItemConfiguration createDraft(String configurationId, String tenantId,
            MaintenanceItemDefinition definition, LocalDateTime validFrom, LocalDateTime validTo,
            String operatorId, LocalDateTime operatedAt) {
        MaintenanceConfigurationAuditEntry created = audit(
                MaintenanceConfigurationAction.CREATED, operatorId, operatedAt, null);
        return new MaintenanceItemConfiguration(configurationId, tenantId, null, definition,
                validFrom, validTo, MaintenanceItemConfigurationStatus.DRAFT, "", null, List.of(created));
    }

    /** 从 Repository 数据恢复配置聚合。 */
    public static MaintenanceItemConfiguration restore(String configurationId, String tenantId,
            String revisionOfConfigurationId, MaintenanceItemDefinition definition,
            LocalDateTime validFrom, LocalDateTime validTo,
            MaintenanceItemConfigurationStatus status, String contentHash,
            MaintenancePublicationEvidence publicationEvidence,
            List<MaintenanceConfigurationAuditEntry> auditTrail) {
        return new MaintenanceItemConfiguration(configurationId, tenantId, revisionOfConfigurationId,
                definition, validFrom, validTo, status, contentHash, publicationEvidence, auditTrail);
    }

    /** 替换草稿内容；配置业务键保持不变。 */
    public void replaceDraftContent(MaintenanceItemDefinition replacement,
            LocalDateTime replacementValidFrom, LocalDateTime replacementValidTo,
            String operatorId, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.DRAFT, "REPLACE_CONTENT");
        replacement = requireValue("replacement", replacement);
        if (!definition.itemCode().equals(replacement.itemCode())
                || !definition.version().equals(replacement.version())) {
            throw validation("definition", "草稿修改不能变更保全项编码或版本");
        }
        validateEffectivePeriod(replacementValidFrom, replacementValidTo);
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.CONTENT_REPLACED, operatorId, operatedAt, null);
        this.definition = replacement;
        this.validFrom = replacementValidFrom;
        this.validTo = replacementValidTo;
        this.contentHash = "";
        this.publicationEvidence = null;
        append(entry);
    }

    /** 提交审批并冻结配置内容。 */
    public void submitForApproval(String operatorId, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.DRAFT, "SUBMIT");
        definition.validateForSubmission();
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.SUBMITTED, operatorId, operatedAt, null);
        this.status = MaintenanceItemConfigurationStatus.PENDING_APPROVAL;
        append(entry);
    }

    /** 审批通过；审批人与本轮提交人必须分离。 */
    public void approve(String operatorId, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.PENDING_APPROVAL, "APPROVE");
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.APPROVED, operatorId, operatedAt, null);
        requireApproverSeparation(entry.operatorId());
        this.status = MaintenanceItemConfigurationStatus.APPROVED;
        append(entry);
    }

    /** 审批驳回并返回草稿。 */
    public void reject(String operatorId, String reason, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.PENDING_APPROVAL, "REJECT");
        String rejectionReason = requireText("reason", reason);
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.REJECTED, operatorId, operatedAt, rejectionReason);
        requireApproverSeparation(entry.operatorId());
        this.status = MaintenanceItemConfigurationStatus.DRAFT;
        this.contentHash = "";
        this.publicationEvidence = null;
        append(entry);
    }

    /** 将已审批配置退回草稿。 */
    public void returnToDraft(String operatorId, String reason, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.APPROVED, "RETURN_TO_DRAFT");
        MaintenanceConfigurationAuditEntry entry = audit(MaintenanceConfigurationAction.RETURNED_TO_DRAFT,
                operatorId, operatedAt, requireText("reason", reason));
        this.status = MaintenanceItemConfigurationStatus.DRAFT;
        this.contentHash = "";
        this.publicationEvidence = null;
        append(entry);
    }

    /** 发布配置并生成规范化内容哈希。 */
    public String publish(String operatorId, LocalDateTime operatedAt,
            MaintenancePublicationEvidence evidence) {
        requireStatus(MaintenanceItemConfigurationStatus.APPROVED, "PUBLISH");
        definition.validateForSubmission();
        evidence = requireValue("publicationEvidence", evidence);
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.PUBLISHED, operatorId, operatedAt, null);
        String publishedHash = MaintenanceItemConfigurationHasher.hash(definition, validFrom, validTo);
        this.contentHash = publishedHash;
        this.publicationEvidence = evidence;
        this.status = MaintenanceItemConfigurationStatus.PUBLISHED;
        append(entry);
        return publishedHash;
    }

    /** 退役已发布配置，仅阻止新案件继续解析该版本。 */
    public void retire(String operatorId, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.PUBLISHED, "RETIRE");
        MaintenanceConfigurationAuditEntry entry = audit(
                MaintenanceConfigurationAction.RETIRED, operatorId, operatedAt, null);
        this.status = MaintenanceItemConfigurationStatus.RETIRED;
        append(entry);
    }

    /** 校验草稿可删除并追加最终删除审计；实际物理删除由 Repository 完成。 */
    public void recordDraftDeletion(String operatorId, LocalDateTime operatedAt) {
        requireStatus(MaintenanceItemConfigurationStatus.DRAFT, "DELETE_DRAFT");
        append(audit(MaintenanceConfigurationAction.DRAFT_DELETED, operatorId, operatedAt, null));
    }

    /** 从已发布或已退役版本创建独立修订草稿。 */
    public MaintenanceItemConfiguration createRevision(String newConfigurationId, String newVersion,
            LocalDateTime revisionValidFrom, LocalDateTime revisionValidTo,
            String operatorId, LocalDateTime operatedAt) {
        if (status != MaintenanceItemConfigurationStatus.PUBLISHED
                && status != MaintenanceItemConfigurationStatus.RETIRED) {
            throw new MaintenanceConfigurationStateException(
                    configurationId, status.name(), "CREATE_REVISION", "只有已发布或已退役配置可以创建修订版");
        }
        String revisionConfigurationId = requireText("newConfigurationId", newConfigurationId);
        if (configurationId.equals(revisionConfigurationId)) {
            throw validation("newConfigurationId", "修订版必须使用新的配置 ID");
        }
        String revisionVersion = requireText("newVersion", newVersion);
        if (definition.version().equals(revisionVersion)) {
            throw validation("newVersion", "修订版必须使用新版本号");
        }
        MaintenanceItemConfiguration revision = createDraft(revisionConfigurationId, tenantId,
                definition.reviseTo(revisionVersion), revisionValidFrom, revisionValidTo, operatorId, operatedAt);
        revision.append(audit(MaintenanceConfigurationAction.REVISION_CREATED,
                operatorId, operatedAt, configurationId));
        return new MaintenanceItemConfiguration(revision.configurationId, revision.tenantId, configurationId,
                revision.definition, revision.validFrom, revision.validTo, revision.status,
                revision.contentHash, null, revision.auditTrail);
    }

    /** 判断该发布版本在业务时点是否可供新案件解析。 */
    public boolean isEffectiveAt(LocalDateTime businessTime) {
        return status == MaintenanceItemConfigurationStatus.PUBLISHED
                && businessTime != null
                && !businessTime.isBefore(validFrom)
                && (validTo == null || businessTime.isBefore(validTo));
    }

    private void requireApproverSeparation(String approverId) {
        String submitterId = auditTrail.stream()
                .filter(entry -> entry.action() == MaintenanceConfigurationAction.SUBMITTED)
                .reduce((first, second) -> second)
                .orElseThrow(() -> validation("auditTrail", "缺少本轮提交审批记录"))
                .operatorId();
        if (submitterId.equals(approverId)) {
            throw validation("operatorId", "审批人与提交人必须分离");
        }
    }

    private void requireStatus(MaintenanceItemConfigurationStatus expected, String operation) {
        if (status != expected) {
            throw new MaintenanceConfigurationStateException(
                    configurationId, status.name(), operation, "当前操作要求配置状态为 " + expected.name());
        }
    }

    private void append(MaintenanceConfigurationAuditEntry entry) {
        List<MaintenanceConfigurationAuditEntry> entries = new ArrayList<>(auditTrail);
        entries.add(entry);
        this.auditTrail = List.copyOf(entries);
    }

    private static void validateEffectivePeriod(LocalDateTime validFrom, LocalDateTime validTo) {
        if (validFrom == null) {
            throw validation("validFrom", "配置生效时间不能为空");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw validation("validTo", "配置失效时间必须晚于生效时间");
        }
    }

    private static String normalizeHash(MaintenanceItemConfigurationStatus status, String value) {
        String hash = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        boolean requiresHash = status == MaintenanceItemConfigurationStatus.PUBLISHED
                || status == MaintenanceItemConfigurationStatus.RETIRED;
        if (requiresHash && !SHA_256.matcher(hash).matches()) {
            throw validation("contentHash", "已发布或已退役配置必须包含 SHA-256 内容哈希");
        }
        if (!requiresHash && !hash.isEmpty()) {
            throw validation("contentHash", "未发布配置不能包含内容哈希");
        }
        return hash;
    }

    private static MaintenancePublicationEvidence validatePublicationEvidence(
            MaintenanceItemConfigurationStatus status, MaintenancePublicationEvidence evidence) {
        boolean requiresEvidence = status == MaintenanceItemConfigurationStatus.PUBLISHED
                || status == MaintenanceItemConfigurationStatus.RETIRED;
        if (requiresEvidence && evidence == null) {
            throw validation("publicationEvidence", "已发布或已退役配置必须包含字段目录证据");
        }
        if (!requiresEvidence && evidence != null) {
            throw validation("publicationEvidence", "未发布配置不能包含字段目录证据");
        }
        return evidence;
    }

    private static List<MaintenanceConfigurationAuditEntry> immutableAuditTrail(
            List<MaintenanceConfigurationAuditEntry> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw validation("auditTrail", "配置审计记录不能为空且不能包含空项");
        }
        return List.copyOf(values);
    }

    private static MaintenanceConfigurationAuditEntry audit(MaintenanceConfigurationAction action,
            String operatorId, LocalDateTime operatedAt, String detail) {
        return new MaintenanceConfigurationAuditEntry(action, operatorId, operatedAt, detail);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T requireValue(String fieldName, T value) {
        if (value == null) {
            throw validation(fieldName, "字段不能为空");
        }
        return value;
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceItemConfiguration", fieldName, message);
    }
}
