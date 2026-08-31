package com.titanium.maintenance.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceItemConfigurationStatus;
import com.titanium.maintenance.common.enums.config.MaintenancePremiumRecalculationTiming;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.exception.MaintenanceConfigurationStateException;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.configuration.control.MaintenanceAccessRule;
import com.titanium.maintenance.configuration.control.MaintenanceChannelCapability;
import com.titanium.maintenance.configuration.control.MaintenanceFeeRule;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.configuration.control.MaintenanceMaterialRequirement;
import com.titanium.maintenance.configuration.control.MaintenanceOutputRule;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceItemConfigurationTest {

    private static final LocalDateTime VALID_FROM = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime VALID_TO = LocalDateTime.of(2027, 9, 1, 0, 0);
    private static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Test
    void shouldCompleteConfigurationLifecycleAndRetainAuditTrail() {
        MaintenanceItemConfiguration configuration = draft(completeDefinition("VOUCHER_CONTACT"));

        configuration.submitForApproval("maker", OPERATED_AT.plusMinutes(1));
        configuration.approve("checker", OPERATED_AT.plusMinutes(2));
        String contentHash = configuration.publish(
                "publisher", OPERATED_AT.plusMinutes(3), publicationEvidence(OPERATED_AT.plusMinutes(3)));

        assertEquals(MaintenanceItemConfigurationStatus.PUBLISHED, configuration.getStatus());
        assertTrue(contentHash.matches("[0-9a-f]{64}"));
        assertEquals(contentHash, configuration.getContentHash());
        assertTrue(configuration.isEffectiveAt(VALID_FROM));
        assertFalse(configuration.isEffectiveAt(VALID_TO));
        assertEquals(List.of(
                MaintenanceConfigurationAction.CREATED,
                MaintenanceConfigurationAction.SUBMITTED,
                MaintenanceConfigurationAction.APPROVED,
                MaintenanceConfigurationAction.PUBLISHED),
                configuration.getAuditTrail().stream()
                        .map(MaintenanceConfigurationAuditEntry::action)
                        .toList());
    }

    @Test
    void shouldOnlyAllowDraftContentReplacement() {
        MaintenanceItemConfiguration configuration = draft(completeDefinition("VOUCHER_CONTACT"));
        MaintenanceItemDefinition replacement = completeDefinition("VOUCHER_CONTACT_V2");

        configuration.replaceDraftContent(
                replacement, VALID_FROM.plusDays(1), VALID_TO.plusDays(1), "editor", OPERATED_AT.plusMinutes(1));
        configuration.submitForApproval("maker", OPERATED_AT.plusMinutes(2));

        assertEquals("VOUCHER_CONTACT_V2", configuration.getDefinition().controls()
                .outputRule().voucherTemplateCode());
        assertThrows(MaintenanceConfigurationStateException.class, () -> configuration.replaceDraftContent(
                completeDefinition("VOUCHER_CONTACT_V3"), VALID_FROM, VALID_TO,
                "editor", OPERATED_AT.plusMinutes(3)));
    }

    @Test
    void shouldRejectIncompleteLegacyDefinitionBeforeSubmission() {
        MaintenanceItemDefinition incomplete = legacyDefinition();
        MaintenanceItemConfiguration configuration = draft(incomplete);

        assertThrows(MaintenanceValidationException.class,
                () -> configuration.submitForApproval("maker", OPERATED_AT.plusMinutes(1)));
        assertEquals(MaintenanceItemConfigurationStatus.DRAFT, configuration.getStatus());
        assertEquals(1, configuration.getAuditTrail().size());
    }

    @Test
    void shouldSeparateSubmitterAndApproverWithoutPartialTransition() {
        MaintenanceItemConfiguration configuration = draft(completeDefinition("VOUCHER_CONTACT"));
        configuration.submitForApproval("operator-a", OPERATED_AT.plusMinutes(1));

        assertThrows(MaintenanceValidationException.class,
                () -> configuration.approve("operator-a", OPERATED_AT.plusMinutes(2)));
        assertEquals(MaintenanceItemConfigurationStatus.PENDING_APPROVAL, configuration.getStatus());
        assertEquals(2, configuration.getAuditTrail().size());
    }

    @Test
    void shouldReturnRejectedConfigurationToDraftWithReason() {
        MaintenanceItemConfiguration configuration = draft(completeDefinition("VOUCHER_CONTACT"));
        configuration.submitForApproval("maker", OPERATED_AT.plusMinutes(1));

        configuration.reject("checker", "材料规则需补充", OPERATED_AT.plusMinutes(2));

        MaintenanceConfigurationAuditEntry rejection = configuration.getAuditTrail().get(2);
        assertEquals(MaintenanceItemConfigurationStatus.DRAFT, configuration.getStatus());
        assertEquals(MaintenanceConfigurationAction.REJECTED, rejection.action());
        assertEquals("材料规则需补充", rejection.detail());
        assertEquals("", configuration.getContentHash());
    }

    @Test
    void shouldCreateIndependentRevisionWithoutChangingPublishedVersion() {
        MaintenanceItemConfiguration original = published(completeDefinition("VOUCHER_CONTACT"));

        MaintenanceItemConfiguration revision = original.createRevision(
                "config-2", "2.0.0", VALID_TO, null, "editor", OPERATED_AT.plusMinutes(4));

        assertEquals(MaintenanceItemConfigurationStatus.PUBLISHED, original.getStatus());
        assertEquals(MaintenanceItemConfigurationStatus.DRAFT, revision.getStatus());
        assertEquals("2.0.0", revision.getDefinition().version());
        assertEquals("config-1", revision.getRevisionOfConfigurationId());
        assertEquals("", revision.getContentHash());
        assertEquals(List.of(MaintenanceConfigurationAction.CREATED,
                MaintenanceConfigurationAction.REVISION_CREATED), revision.getAuditTrail().stream()
                .map(MaintenanceConfigurationAuditEntry::action)
                .toList());
    }

    @Test
    void shouldPreventPublishedContentMutationAndDuplicateRevisionIdentity() {
        MaintenanceItemConfiguration configuration = published(completeDefinition("VOUCHER_CONTACT"));

        assertThrows(MaintenanceConfigurationStateException.class, () -> configuration.replaceDraftContent(
                completeDefinition("OTHER"), VALID_FROM, VALID_TO, "editor", OPERATED_AT.plusMinutes(4)));
        assertThrows(MaintenanceValidationException.class, () -> configuration.createRevision(
                "config-1", "2.0.0", VALID_TO, null, "editor", OPERATED_AT.plusMinutes(4)));
        assertThrows(MaintenanceValidationException.class, () -> configuration.createRevision(
                "config-2", "1.0.0", VALID_TO, null, "editor", OPERATED_AT.plusMinutes(4)));
    }

    @Test
    void shouldRetirePublishedVersionAndExcludeItFromNewCases() {
        MaintenanceItemConfiguration configuration = published(completeDefinition("VOUCHER_CONTACT"));

        configuration.retire("publisher", OPERATED_AT.plusMinutes(4));

        assertEquals(MaintenanceItemConfigurationStatus.RETIRED, configuration.getStatus());
        assertFalse(configuration.isEffectiveAt(VALID_FROM.plusDays(1)));
        assertTrue(configuration.getContentHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldGenerateStableHashForEquivalentUnorderedContentAndIgnoreAudit() {
        MaintenanceItemDefinition firstDefinition = definitionWithOrder(false);
        MaintenanceItemDefinition secondDefinition = definitionWithOrder(true);
        MaintenanceItemConfiguration first = draft(firstDefinition);
        MaintenanceItemConfiguration second = MaintenanceItemConfiguration.createDraft(
                "config-other", "tenant-other", secondDefinition, VALID_FROM, VALID_TO,
                "other-maker", OPERATED_AT.plusDays(1));

        String firstHash = publish(first, "maker", "checker", "publisher", OPERATED_AT);
        String secondHash = publish(second, "other-maker", "other-checker", "other-publisher",
                OPERATED_AT.plusDays(1));

        assertEquals(firstHash, secondHash);
    }

    @Test
    void shouldChangeHashWhenBusinessContentChanges() {
        MaintenanceItemConfiguration first = published(completeDefinition("VOUCHER_CONTACT"));
        MaintenanceItemConfiguration second = published(completeDefinition("VOUCHER_CONTACT_V2"));

        assertNotEquals(first.getContentHash(), second.getContentHash());
    }

    @Test
    void shouldRejectInvalidConfigurationEffectivePeriod() {
        assertThrows(MaintenanceValidationException.class, () -> MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", completeDefinition("VOUCHER_CONTACT"),
                VALID_FROM, VALID_FROM, "maker", OPERATED_AT));
    }

    @Test
    void shouldOnlyAllowApiChannelToEnableAutomaticApproval() {
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceChannelCapability(MaintenanceChannel.MANUAL, true));
    }

    @Test
    void shouldRequireCompleteFinancialControlsForPayableItem() {
        MaintenanceItemControls controls = new MaintenanceItemControls(
                Set.of(MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL)),
                List.of(), Set.of(), "APPROVAL_FINANCIAL",
                new MaintenanceFeeRule("FORMULA_COVERAGE_CHANGE", "GATE_PAYMENT_SETTLED",
                        MaintenancePremiumRecalculationTiming.BEFORE_SETTLEMENT),
                new MaintenanceAccessRule(Set.of("maintenance:item:operate"), Set.of("maintenance:item:view")),
                MaintenanceOutputRule.empty());
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                "COVERAGE_CHANGE", "1.0.0", "保额变更", MaintenanceItemCategory.COVERAGE,
                Set.of(MaintenanceChannel.MANUAL), List.of(), List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.required(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.REQUIRED, MaintenanceEffectiveRule.immediate(), Set.of(), true, controls);
        MaintenanceItemControls incompleteControls = new MaintenanceItemControls(
                controls.channelCapabilities(), List.of(), Set.of(), "APPROVAL_FINANCIAL",
                MaintenanceFeeRule.none(), controls.accessRule(), MaintenanceOutputRule.empty());
        MaintenanceItemDefinition incomplete = new MaintenanceItemDefinition(
                "COVERAGE_CHANGE", "1.0.0", "保额变更", MaintenanceItemCategory.COVERAGE,
                Set.of(MaintenanceChannel.MANUAL), List.of(), definition.steps(),
                MaintenanceFeeMode.REQUIRED, MaintenanceEffectiveRule.immediate(), Set.of(), true,
                incompleteControls);

        definition.validateForSubmission();

        assertThrows(MaintenanceValidationException.class, incomplete::validateForSubmission);
        assertNotNull(definition.controls().feeRule().formulaCode());
        assertEquals(MaintenancePremiumRecalculationTiming.BEFORE_SETTLEMENT,
                definition.controls().feeRule().recalculationTiming());
    }

    private MaintenanceItemConfiguration draft(MaintenanceItemDefinition definition) {
        return MaintenanceItemConfiguration.createDraft(
                "config-1", "tenant-1", definition, VALID_FROM, VALID_TO, "maker", OPERATED_AT);
    }

    private MaintenanceItemConfiguration published(MaintenanceItemDefinition definition) {
        MaintenanceItemConfiguration configuration = draft(definition);
        publish(configuration, "maker", "checker", "publisher", OPERATED_AT);
        return configuration;
    }

    private String publish(MaintenanceItemConfiguration configuration, String maker, String checker,
            String publisher, LocalDateTime baseTime) {
        configuration.submitForApproval(maker, baseTime.plusMinutes(1));
        configuration.approve(checker, baseTime.plusMinutes(2));
        return configuration.publish(
                publisher, baseTime.plusMinutes(3), publicationEvidence(baseTime.plusMinutes(3)));
    }

    private MaintenancePublicationEvidence publicationEvidence(LocalDateTime validatedAt) {
        return new MaintenancePublicationEvidence("2026.08.24.1", "a".repeat(64), validatedAt);
    }

    private MaintenanceItemDefinition completeDefinition(String voucherTemplateCode) {
        return definition(new LinkedHashSet<>(Set.of(MaintenanceChannel.MANUAL, MaintenanceChannel.API)),
                List.of(
                        MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, false, PolicyFieldValueType.TEXT),
                        MaintenanceFieldRule.editable(
                                "policy.holder.email", false, true, PolicyFieldValueType.TEXT)),
                List.of(
                        new MaintenanceMaterialRequirement("IDENTITY", true, null),
                        new MaintenanceMaterialRequirement("AUTHORIZATION", false, "RULE_AUTH")),
                Set.of("RULE_PHONE_AND_EMAIL"), Set.of("maintenance:config:operate", "maintenance:item:operate"),
                Set.of("maintenance:config:view", "maintenance:sensitive:view"),
                Set.of("NOTICE_SMS", "NOTICE_EMAIL"), voucherTemplateCode);
    }

    private MaintenanceItemDefinition definitionWithOrder(boolean reversed) {
        LinkedHashSet<MaintenanceChannel> channels = reversed
                ? linkedSet(MaintenanceChannel.API, MaintenanceChannel.MANUAL)
                : linkedSet(MaintenanceChannel.MANUAL, MaintenanceChannel.API);
        List<MaintenanceFieldRule> fields = reversed
                ? List.of(MaintenanceFieldRule.editable(
                                "policy.holder.email", false, true, PolicyFieldValueType.TEXT),
                        MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, false, PolicyFieldValueType.TEXT))
                : List.of(MaintenanceFieldRule.editable(
                                "policy.holder.mobile", true, false, PolicyFieldValueType.TEXT),
                        MaintenanceFieldRule.editable(
                                "policy.holder.email", false, true, PolicyFieldValueType.TEXT));
        List<MaintenanceMaterialRequirement> materials = reversed
                ? List.of(new MaintenanceMaterialRequirement("AUTHORIZATION", false, "RULE_AUTH"),
                        new MaintenanceMaterialRequirement("IDENTITY", true, null))
                : List.of(new MaintenanceMaterialRequirement("IDENTITY", true, null),
                        new MaintenanceMaterialRequirement("AUTHORIZATION", false, "RULE_AUTH"));
        return definition(channels, fields, materials,
                linkedSet("RULE_PHONE_AND_EMAIL", "RULE_CONTACT"),
                linkedSet("maintenance:item:operate", "maintenance:config:operate"),
                linkedSet("maintenance:sensitive:view", "maintenance:config:view"),
                linkedSet("NOTICE_SMS", "NOTICE_EMAIL"), "VOUCHER_CONTACT");
    }

    private MaintenanceItemDefinition definition(Set<MaintenanceChannel> channels,
            List<MaintenanceFieldRule> fields, List<MaintenanceMaterialRequirement> materials,
            Set<String> crossFieldRules, Set<String> operationPermissions, Set<String> viewPermissions,
            Set<String> notificationTemplates, String voucherTemplateCode) {
        Set<MaintenanceChannelCapability> capabilities = Set.of(
                MaintenanceChannelCapability.manualApproval(MaintenanceChannel.MANUAL),
                new MaintenanceChannelCapability(MaintenanceChannel.API, true));
        MaintenanceItemControls controls = new MaintenanceItemControls(capabilities, materials,
                crossFieldRules, "APPROVAL_STANDARD", MaintenanceFeeRule.none(),
                new MaintenanceAccessRule(operationPermissions, viewPermissions),
                new MaintenanceOutputRule(voucherTemplateCode, notificationTemplates, "ARCHIVE_POLICY"));
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, channels, fields, standardSteps(),
                MaintenanceFeeMode.NONE, new MaintenanceEffectiveRule(
                        Set.of(EffectiveTimeType.IMMEDIATE, EffectiveTimeType.FUTURE),
                        EffectiveTimeType.IMMEDIATE, 0, 30),
                Set.of("SURRENDER"), true, controls);
    }

    private MaintenanceItemDefinition legacyDefinition() {
        return new MaintenanceItemDefinition("CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION, Set.of(MaintenanceChannel.MANUAL),
                List.of(), standardSteps(), MaintenanceFeeMode.NONE,
                MaintenanceEffectiveRule.immediate(), Set.of(), true);
    }

    private List<MaintenanceStepDefinition> standardSteps() {
        return List.of(
                MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT));
    }

    @SafeVarargs
    private static <T> LinkedHashSet<T> linkedSet(T... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}
