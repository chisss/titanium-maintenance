package com.titanium.maintenance.web.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.common.enums.config.MaintenanceConfigurationAction;
import com.titanium.maintenance.configuration.MaintenanceItemConfiguration;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditPage;
import com.titanium.maintenance.repository.MaintenanceItemConfigurationRepository.ConfigurationAuditRecord;
import com.titanium.maintenance.web.mapper.MaintenanceConfigurationWebMapper;

class MaintenanceConfigurationWebMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MaintenanceConfigurationWebMapper mapper =
            new MaintenanceConfigurationWebMapper(objectMapper);

    @Test
    void shouldRedactFieldStrategyWithoutSensitivePermission() {
        var response = mapper.toVO(MaintenanceConfigurationWebTestFixture.stored(5L), false);

        var field = response.definition().fieldRules().getFirst();
        assertThat(field.fieldCode()).isEqualTo("policy.holder.mobile");
        assertThat(field.conditionRuleCode()).isNull();
        assertThat(field.expectedValueType()).isNull();
        assertThat(field.detailsRedacted()).isTrue();
    }

    @Test
    void shouldExposeFieldStrategyWithSensitivePermission() {
        var response = mapper.toVO(MaintenanceConfigurationWebTestFixture.stored(5L), true);

        var field = response.definition().fieldRules().getFirst();
        assertThat(field.conditionRuleCode()).isEqualTo("RULE_MOBILE");
        assertThat(field.expectedValueType()).isNotNull();
        assertThat(field.detailsRedacted()).isFalse();
    }

    @Test
    void shouldReturnStructuredRedactedAuditInsteadOfRawJson() throws Exception {
        MaintenanceItemConfiguration before = MaintenanceConfigurationWebTestFixture.configuration("RULE_MOBILE");
        MaintenanceItemConfiguration after = MaintenanceConfigurationWebTestFixture.configuration("RULE_MOBILE");
        after.replaceDraftContent(
                MaintenanceConfigurationWebTestFixture.definition("RULE_MOBILE_V2"),
                MaintenanceConfigurationWebTestFixture.OPERATED_AT.plusDays(2), null,
                "editor", MaintenanceConfigurationWebTestFixture.OPERATED_AT.plusMinutes(1));
        ConfigurationAuditRecord record = new ConfigurationAuditRecord(
                "audit-1", 2, MaintenanceConfigurationAction.CONTENT_REPLACED,
                "editor", null, before, after, null, null,
                "127.0.0.1", "correlation-1", "SUCCESS",
                MaintenanceConfigurationWebTestFixture.OPERATED_AT.plusMinutes(1),
                MaintenanceConfigurationWebTestFixture.OPERATED_AT.plusMinutes(1));

        var response = mapper.toAuditPageVO(
                new ConfigurationAuditPage(List.of(record), 1, 0, 20), false);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.items().getFirst().changes()).isNotEmpty();
        assertThat(response.items().getFirst().after().definition().fieldRules().getFirst()
                .conditionRuleCode()).isNull();
        assertThat(json).doesNotContain("beforeJson", "afterJson", "RULE_MOBILE_V2");
    }
}
