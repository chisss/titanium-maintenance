package com.titanium.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenanceItemCategory;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.configuration.MaintenanceEffectiveRule;
import com.titanium.maintenance.configuration.MaintenanceItemDefinition;
import com.titanium.maintenance.configuration.MaintenanceStepDefinition;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;
import com.titanium.maintenance.valueobject.workflow.MaintenanceWorkflowTask;

class MaintenanceWorkflowPlannerTest {

    private static final LocalDateTime SELECTED_AT = LocalDateTime.parse("2026-08-25T10:00:00");

    @Test
    void shouldInstantiateRequiredConditionalAndSkippedTasksInFrozenItemOrder() {
        MaintenanceItemInstance first = item("POLICY_INFO_CHANGE", List.of(
                MaintenanceStepDefinition.required(1, MaintenanceStepType.CREATE),
                MaintenanceStepDefinition.required(2, MaintenanceStepType.DATA_ENTRY),
                new MaintenanceStepDefinition(
                        3, MaintenanceStepType.REVIEW, MaintenanceStepMode.CONDITIONAL, "review-rule-v1"),
                MaintenanceStepDefinition.skipped(4, MaintenanceStepType.FEE_SETTLEMENT),
                MaintenanceStepDefinition.required(5, MaintenanceStepType.EFFECT)));
        MaintenanceItemInstance second = item("BENEFICIARY_CHANGE", List.of(
                new MaintenanceStepDefinition(
                        1, MaintenanceStepType.REVIEW, MaintenanceStepMode.CONDITIONAL, "beneficiary-review-v1"),
                MaintenanceStepDefinition.required(2, MaintenanceStepType.EFFECT)));

        List<MaintenanceWorkflowTask> tasks = new MaintenanceWorkflowPlanner().plan(
                MaintenanceId.of("case-1"), List.of(first, second));

        assertEquals(7, tasks.size());
        assertEquals(List.of(
                MaintenanceWorkflowTaskStatus.COMPLETED,
                MaintenanceWorkflowTaskStatus.READY,
                MaintenanceWorkflowTaskStatus.PENDING,
                MaintenanceWorkflowTaskStatus.SKIPPED,
                MaintenanceWorkflowTaskStatus.PENDING,
                MaintenanceWorkflowTaskStatus.WAITING_CONDITION,
                MaintenanceWorkflowTaskStatus.PENDING),
                tasks.stream().map(MaintenanceWorkflowTask::status).toList());
        assertEquals(List.of(0, 0, 0, 0, 0, 1, 1),
                tasks.stream().map(MaintenanceWorkflowTask::itemOrder).toList());
        assertEquals("case-1:BENEFICIARY_CHANGE:REVIEW", tasks.get(5).taskId());
        assertEquals("beneficiary-review-v1", tasks.get(5).conditionRuleCode());
    }

    private MaintenanceItemInstance item(
            String itemCode,
            List<MaintenanceStepDefinition> steps) {
        MaintenanceItemDefinition definition = new MaintenanceItemDefinition(
                itemCode, "1.0.0", itemCode, MaintenanceItemCategory.BASIC_INFORMATION,
                Set.of(MaintenanceChannel.MANUAL), List.of(), steps,
                MaintenanceFeeMode.NONE, MaintenanceEffectiveRule.immediate(), Set.of(), false);
        return MaintenanceItemInstance.from(definition, SELECTED_AT);
    }
}
