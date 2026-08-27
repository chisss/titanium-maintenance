package com.titanium.maintenance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;

/** 保全项配置聚合仓储端口。 */
public interface MaintenanceItemConfigurationRepository {

    long NEW_CONFIGURATION_VERSION = -1L;

    boolean existsByBusinessKey(String tenantId, String itemCode, String configurationVersion);

    Optional<StoredConfiguration> findById(String tenantId, String configurationId);

    Optional<StoredConfiguration> findEffective(
            String tenantId, String itemCode, LocalDateTime businessTime);

    ConfigurationPage search(String tenantId, ConfigurationSearchCriteria criteria);

    ConfigurationAuditPage findAuditHistory(
            String tenantId, String configurationId, int page, int size);

    boolean existsPublishedOverlap(String tenantId, String itemCode, String excludedConfigurationId,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo);

    StoredConfiguration save(MaintenanceItemConfiguration configuration, long expectedRowVersion,
            SaveContext context);

    void deleteDraft(MaintenanceItemConfiguration configuration, long expectedRowVersion,
            SaveContext context);

    /** 带基础设施并发版本的配置。 */
    record StoredConfiguration(MaintenanceItemConfiguration configuration, long rowVersion) {

        public StoredConfiguration {
            if (configuration == null || rowVersion < 0) {
                throw validation("存储配置不能为空且行版本不能为负数");
            }
        }
    }

    /** 保存配置时补充的审计上下文。 */
    record SaveContext(String sourceIp, String correlationId) {

        public SaveContext {
            sourceIp = requireText("sourceIp", sourceIp);
            correlationId = requireText("correlationId", correlationId);
        }
    }

    /** 配置管理列表的租户内查询条件。 */
    record ConfigurationSearchCriteria(String itemCode, MaintenanceItemConfigurationStatus status,
            LocalDateTime effectiveAt, int page, int size) {

        public ConfigurationSearchCriteria {
            itemCode = normalize(itemCode);
            if (page < 0 || size < 1 || size > 200) {
                throw validation("分页参数非法");
            }
        }
    }

    /** 配置管理列表页。 */
    record ConfigurationPage(List<StoredConfiguration> items, long total, int page, int size) {

        public ConfigurationPage {
            items = items == null ? List.of() : List.copyOf(items);
            if (items.stream().anyMatch(item -> item == null)
                    || total < 0 || page < 0 || size < 1 || size > 200) {
                throw validation("配置分页结果非法");
            }
        }

        public int totalPages() {
            return total == 0 ? 0 : (int) ((total + size - 1) / size);
        }
    }

    /** 单次配置变更的前后快照审计记录。 */
    record ConfigurationAuditRecord(String auditId, int sequence, MaintenanceConfigurationAction action,
            String operatorId, String detail, MaintenanceItemConfiguration before,
            MaintenanceItemConfiguration after, String beforeHash, String afterHash,
            String sourceIp, String correlationId, String operationResult,
            LocalDateTime occurredAt, LocalDateTime recordedAt) {

        public ConfigurationAuditRecord {
            auditId = requireText("auditId", auditId);
            operatorId = requireText("operatorId", operatorId);
            sourceIp = requireText("sourceIp", sourceIp);
            correlationId = requireText("correlationId", correlationId);
            operationResult = requireText("operationResult", operationResult);
            detail = normalize(detail);
            beforeHash = normalize(beforeHash);
            afterHash = normalize(afterHash);
            if (sequence < 1 || action == null || after == null
                    || occurredAt == null || recordedAt == null) {
                throw validation("配置审计记录非法");
            }
        }
    }

    /** 配置变更审计页。 */
    record ConfigurationAuditPage(List<ConfigurationAuditRecord> items, long total, int page, int size) {

        public ConfigurationAuditPage {
            items = items == null ? List.of() : List.copyOf(items);
            if (items.stream().anyMatch(item -> item == null)
                    || total < 0 || page < 0 || size < 1 || size > 200) {
                throw validation("配置审计分页结果非法");
            }
        }

        public int totalPages() {
            return total == 0 ? 0 : (int) ((total + size - 1) / size);
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceConfigurationSaveContext", fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException validation(String message) {
        return new MaintenanceValidationException("StoredMaintenanceConfiguration", message);
    }
}
