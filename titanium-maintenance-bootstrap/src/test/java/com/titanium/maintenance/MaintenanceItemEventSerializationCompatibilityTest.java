package com.titanium.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.configuration.control.MaintenanceItemControls;
import com.titanium.maintenance.event.MaintenanceItemAddedEvent;
import com.titanium.maintenance.event.MaintenanceWorkflowInitializedEvent;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

class MaintenanceItemEventSerializationCompatibilityTest {

    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 8, 24, 10, 0);

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldIgnoreLegacyDerivedNullPropertyAndStopWritingIt() throws Exception {
        MaintenanceFieldValue value = MaintenanceFieldValue.text("13800138000");
        ObjectNode valueJson = objectMapper.valueToTree(value);

        assertFalse(valueJson.has("null"));
        valueJson.put("null", false);

        assertEquals(value, objectMapper.treeToValue(valueJson, MaintenanceFieldValue.class));
    }

    @Test
    void shouldDeserializeLegacyItemEventWithoutSelectionEvidence() throws Exception {
        MaintenanceItemInstance item = MaintenanceItemInstance.from(definition(), EVENT_TIME);
        MaintenanceItemAddedEvent event = new MaintenanceItemAddedEvent(
                MaintenanceId.of("maintenance-legacy-item"), item, EVENT_TIME, "operator-1", "tenant-1");
        ObjectNode eventJson = objectMapper.valueToTree(event);
        ((ObjectNode) eventJson.get("item")).remove("selectionEvidence");
        ((ObjectNode) eventJson.get("item")).remove("controls");

        MaintenanceItemAddedEvent restored = objectMapper.treeToValue(
                eventJson, MaintenanceItemAddedEvent.class);

        assertEquals("1.0.0", restored.item().selectionEvidence().configurationVersion());
        assertFalse(restored.item().selectionEvidence().authoritative());
        assertEquals(MaintenanceItemControls.defaults(restored.item().channels()), restored.item().controls());
    }

    @Test
    void shouldDeserializeM401WorkflowEventWithoutTransitionEvidence() throws Exception {
        MaintenanceWorkflowTask task = new MaintenanceWorkflowTask(
                "maintenance-legacy-workflow:CONTACT_CHANGE:DATA_ENTRY",
                "CONTACT_CHANGE", 0, 1, MaintenanceStepType.DATA_ENTRY,
                MaintenanceStepMode.REQUIRED, null, MaintenanceWorkflowTaskStatus.READY);
        MaintenanceWorkflowInitializedEvent event = new MaintenanceWorkflowInitializedEvent(
                MaintenanceId.of("maintenance-legacy-workflow"), List.of(task), EVENT_TIME,
                "operator-1", "tenant-1");
        ObjectNode eventJson = objectMapper.valueToTree(event);
        ObjectNode taskJson = (ObjectNode) eventJson.withArray("tasks").get(0);
        taskJson.remove(List.of(
                "assignment", "retryCount", "failure", "conditionEvidence",
                "reviewEvidence", "lastOperation", "underwritingEvidence", "premiumQuoteEvidence",
                "billingPostingEvidence", "fundSettlementEvidence", "effectEvidence"));

        MaintenanceWorkflowInitializedEvent restored = objectMapper.treeToValue(
                eventJson, MaintenanceWorkflowInitializedEvent.class);

        MaintenanceWorkflowTask restoredTask = restored.tasks().get(0);
        assertEquals(MaintenanceWorkflowTaskStatus.READY, restoredTask.status());
        assertEquals(0, restoredTask.retryCount());
        assertNull(restoredTask.assignment());
        assertNull(restoredTask.failure());
        assertNull(restoredTask.conditionEvidence());
        assertNull(restoredTask.reviewEvidence());
        assertNull(restoredTask.underwritingEvidence());
        assertNull(restoredTask.premiumQuoteEvidence());
        assertNull(restoredTask.billingPostingEvidence());
        assertNull(restoredTask.fundSettlementEvidence());
        assertNull(restoredTask.effectEvidence());
        assertNull(restoredTask.lastOperation());
    }

    private MaintenanceItemDefinition definition() {
        return new MaintenanceItemDefinition(
                "CONTACT_CHANGE", "1.0.0", "联系方式变更",
                MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL), List.of(),
                List.of(
                        MaintenanceStepDefinition.required(1, MaintenanceStepType.DATA_ENTRY),
                        MaintenanceStepDefinition.skipped(2, MaintenanceStepType.FEE_SETTLEMENT),
                        MaintenanceStepDefinition.required(3, MaintenanceStepType.EFFECT)),
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
    }
}
