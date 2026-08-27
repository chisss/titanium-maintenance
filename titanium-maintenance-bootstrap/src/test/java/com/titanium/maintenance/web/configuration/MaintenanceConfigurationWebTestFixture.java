package com.titanium.maintenance.web.configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
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
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.StoredConfiguration;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

final class MaintenanceConfigurationWebTestFixture {

    static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 8, 24, 12, 0);

    private MaintenanceConfigurationWebTestFixture() {
    }

    static StoredConfiguration stored(long rowVersion) {
        return new StoredConfiguration(configuration("RULE_MOBILE"), rowVersion);
    }

    static MaintenanceItemConfiguration configuration(String conditionRuleCode) {
        return MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition(conditionRuleCode),
                OPERATED_AT.plusDays(1), null, "maker", OPERATED_AT);
    }

    static MaintenanceItemDefinition definition(String conditionRuleCode) {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL)),
                List.of(), Set.of("RULE_CONTACT"), "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(
                        Set.of("maintenance:item:operate"), Set.of("maintenance:item:view")),
                new MaintenanceOutputRule("VOUCHER_CONTACT", Set.of("NOTICE_CONTACT"), "ARCHIVE_POLICY"));
        return new MaintenanceItemDefinition(
                "CONTACT_CHANGE", "1.0.0", "联系方式变更", MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL),
                List.of(new MaintenanceFieldRule(
                        "policy.holder.mobile", true, true, true, false,
                        conditionRuleCode, PolicyFieldValueType.TEXT)),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
    }
}
