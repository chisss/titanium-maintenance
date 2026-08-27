package com.titanium.maintenance.application.orchestration.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.application.command.MaintenanceConfigurationCommandService;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationOperationContext;
import com.titanium.maintenance.application.model.configuration.MaintenanceConfigurationValidationCriteria;
import com.titanium.maintenance.application.query.MaintenanceConfigurationQueryService;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceFieldRule;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort.ReferenceValidationEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationSearchCriteria;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.SaveContext;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceConfigurationLifecycleIntegrationTest {

    private static final String TENANT_ID = "tenant-1";
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);
    private static final LocalDateTime VALID_FROM = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    void shouldCompleteLifecycleAndResolvePublishedVersionAtBusinessTime() {
        InMemoryConfigurationRepository repository = new InMemoryConfigurationRepository();
        MaintenanceConfigurationValidator validator = new MaintenanceConfigurationValidator(
                request -> catalog(request.businessDate()),
                request -> new ReferenceValidationEvidence(true, "reference-v1",
                        request.ruleCodes(), request.permissionCodes(), request.templateCodes(), null));
        var managementService = new MaintenanceConfigurationManagementApplicationService(
                repository, validator, tenantId -> true);
        var commandService = new MaintenanceConfigurationCommandService(managementService);
        var queryService = new MaintenanceConfigurationQueryService(
                new MaintenanceConfigurationQueryApplicationService(repository));
        MaintenanceConfigurationValidationCriteria criteria = new MaintenanceConfigurationValidationCriteria(
                "LIFE", "INDIVIDUAL", LocalDate.of(2026, 9, 1));

        StoredConfiguration draft = commandService.createDraft(
                "config-1", definition(), VALID_FROM, null, context("maker", 0));
        assertEquals(0L, draft.rowVersion());
        assertTrue(commandService.validate("config-1", criteria, context("maker", 1)).valid());

        StoredConfiguration submitted = commandService.submitForApproval(
                "config-1", draft.rowVersion(), criteria, context("maker", 2));
        StoredConfiguration approved = commandService.approve(
                "config-1", submitted.rowVersion(), criteria, context("checker", 3));
        StoredConfiguration published = commandService.publish(
                "config-1", approved.rowVersion(), criteria, context("publisher", 4));
        StoredConfiguration resolved = queryService.resolveEffective(
                TENANT_ID, "CONTACT_CHANGE", VALID_FROM.plusHours(1));

        assertEquals(3L, published.rowVersion());
        assertEquals(published.rowVersion(), resolved.rowVersion());
        assertSame(published.configuration(), resolved.configuration());
        assertEquals(MaintenanceItemConfigurationStatus.PUBLISHED,
                resolved.configuration().getStatus());
        assertEquals(64, resolved.configuration().getContentHash().length());
        assertEquals("catalog-v1",
                resolved.configuration().getPublicationEvidence().catalogVersion());
        assertEquals("a".repeat(64),
                resolved.configuration().getPublicationEvidence().catalogHash());
    }

    private MaintenanceItemDefinition definition() {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL)),
                List.of(), Set.of(), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"),
                        Set.of("maintenance:item:view", "maintenance:sensitive:view")),
                MaintenanceOutputRule.empty());
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(MaintenanceFieldRule.editable(
                        "policy.holder.mobile", false, true, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
    }

    private PolicyFieldCatalogEvidence catalog(LocalDate businessDate) {
        PolicyFieldCapabilityEvidence capability = new PolicyFieldCapabilityEvidence(
                true, true, true, true, false, "CONTACT_CHANGE");
        PolicyFieldDescriptorEvidence field = new PolicyFieldDescriptorEvidence(
                "policy.holder.mobile", PolicyFieldObjectType.POLICY_HOLDER, PolicyFieldValueType.TEXT,
                "policy.holder.mobile", false, null, capability,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE, null);
        return new PolicyFieldCatalogEvidence(
                TENANT_ID, "LIFE", "INDIVIDUAL", businessDate,
                "catalog-v1", "a".repeat(64), List.of(field));
    }

    private MaintenanceConfigurationOperationContext context(String operatorId, int minuteOffset) {
        return new MaintenanceConfigurationOperationContext(
                TENANT_ID, operatorId, "127.0.0.1", "correlation-" + minuteOffset,
                STARTED_AT.plusMinutes(minuteOffset));
    }

    private static final class InMemoryConfigurationRepository
            implements MaintenanceItemConfigurationRepository {

        private final Map<String, StoredConfiguration> configurations = new HashMap<>();

        @Override
        public boolean existsByBusinessKey(String tenantId, String itemCode, String configurationVersion) {
            return configurations.values().stream().map(StoredConfiguration::configuration)
                    .anyMatch(configuration -> configuration.getTenantId().equals(tenantId)
                            && configuration.getDefinition().itemCode().equals(itemCode)
                            && configuration.getDefinition().version().equals(configurationVersion));
        }

        @Override
        public Optional<StoredConfiguration> findById(String tenantId, String configurationId) {
            return Optional.ofNullable(configurations.get(configurationId))
                    .filter(stored -> stored.configuration().getTenantId().equals(tenantId));
        }

        @Override
        public Optional<StoredConfiguration> findEffective(
                String tenantId, String itemCode, LocalDateTime businessTime) {
            return configurations.values().stream()
                    .filter(stored -> stored.configuration().getTenantId().equals(tenantId))
                    .filter(stored -> stored.configuration().getDefinition().itemCode().equals(itemCode))
                    .filter(stored -> stored.configuration().isEffectiveAt(businessTime))
                    .findFirst();
        }

        @Override
        public ConfigurationPage search(String tenantId, ConfigurationSearchCriteria criteria) {
            return new ConfigurationPage(List.of(), 0, criteria.page(), criteria.size());
        }

        @Override
        public ConfigurationAuditPage findAuditHistory(
                String tenantId, String configurationId, int page, int size) {
            return new ConfigurationAuditPage(List.of(), 0, page, size);
        }

        @Override
        public boolean existsPublishedOverlap(String tenantId, String itemCode,
                String excludedConfigurationId, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
            return configurations.values().stream().map(StoredConfiguration::configuration)
                    .filter(configuration -> configuration.getTenantId().equals(tenantId))
                    .filter(configuration -> configuration.getDefinition().itemCode().equals(itemCode))
                    .filter(configuration -> !configuration.getConfigurationId().equals(excludedConfigurationId))
                    .filter(configuration -> configuration.getStatus()
                            == MaintenanceItemConfigurationStatus.PUBLISHED)
                    .anyMatch(configuration -> overlaps(configuration, effectiveFrom, effectiveTo));
        }

        @Override
        public StoredConfiguration save(MaintenanceItemConfiguration configuration,
                long expectedRowVersion, SaveContext context) {
            StoredConfiguration current = configurations.get(configuration.getConfigurationId());
            long nextVersion;
            if (expectedRowVersion == NEW_CONFIGURATION_VERSION) {
                if (current != null) {
                    throw new IllegalStateException("配置已存在");
                }
                nextVersion = 0L;
            } else {
                if (current == null || current.rowVersion() != expectedRowVersion) {
                    throw new IllegalStateException("配置版本冲突");
                }
                nextVersion = expectedRowVersion + 1;
            }
            StoredConfiguration saved = new StoredConfiguration(configuration, nextVersion);
            configurations.put(configuration.getConfigurationId(), saved);
            return saved;
        }

        @Override
        public void deleteDraft(MaintenanceItemConfiguration configuration,
                long expectedRowVersion, SaveContext context) {
            StoredConfiguration current = configurations.get(configuration.getConfigurationId());
            if (current == null || current.rowVersion() != expectedRowVersion) {
                throw new IllegalStateException("配置版本冲突");
            }
            configurations.remove(configuration.getConfigurationId());
        }

        private boolean overlaps(MaintenanceItemConfiguration configuration,
                LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
            boolean startsBeforeEnd = effectiveTo == null || configuration.getValidFrom().isBefore(effectiveTo);
            boolean endsAfterStart = configuration.getValidTo() == null
                    || configuration.getValidTo().isAfter(effectiveFrom);
            return startsBeforeEnd && endsAfterStart;
        }
    }
}
