package com.titanium.maintenance.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.common.enums.config.MaintenanceStepMode;
import com.titanium.maintenance.common.enums.config.MaintenanceStepType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowTaskStatus;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.query.query.MaintenanceCaseSearchCriteria;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.FieldChangeQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotReferenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.SnapshotSetQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowAppliedFieldEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowEffectRequestEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowPolicyApplicationEvidenceQueryResult;
import com.titanium.maintenance.query.result.MaintenanceCaseDetailQueryResult.WorkflowTaskQueryResult;
import com.titanium.maintenance.query.service.MaintenanceCaseQueryService;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

class MaintenanceCaseQueryApplicationServiceTest {

    private MaintenanceCaseQueryService queryService;
    private MaintenanceCaseQueryApplicationService service;

    @BeforeEach
    void setUp() {
        queryService = mock(MaintenanceCaseQueryService.class);
        service = new MaintenanceCaseQueryApplicationService(
                queryService, new MaintenanceCaseFieldMasker());
    }

    @Test
    void shouldMaskSensitiveFourValueViewByDefault() {
        when(queryService.findDetail("tenant-1", "case-1")).thenReturn(Optional.of(detail()));

        MaintenanceCaseDetailQueryResult result = service.findDetail("tenant-1", "case-1", false);

        FieldChangeQueryResult field = result.fieldChanges().getFirst();
        assertEquals("138****0000", field.baseValue());
        assertEquals("137****0000", field.currentValue());
        assertEquals("139****0000", field.proposedValue());
        assertEquals("139****0000", result.workflowTasks().getFirst()
                .effectEvidence().application().appliedFields().getFirst().canonicalValue());
        assertEquals(MaintenanceWorkflowTaskStatus.READY, result.workflowTasks().getFirst().status());
    }

    @Test
    void shouldReturnOriginalValuesOnlyWithSensitivePermission() {
        when(queryService.findDetail("tenant-1", "case-1")).thenReturn(Optional.of(detail()));

        MaintenanceCaseDetailQueryResult result = service.findDetail("tenant-1", "case-1", true);

        assertEquals("13900000000", result.fieldChanges().getFirst().proposedValue());
        assertEquals("13900000000", result.workflowTasks().getFirst()
                .effectEvidence().application().appliedFields().getFirst().canonicalValue());
    }

    @Test
    void shouldRejectInvalidCreatedTimeRangeBeforeQuery() {
        MaintenanceCaseSearchCriteria criteria = new MaintenanceCaseSearchCriteria(
                null, null, null, null, null, null, null,
                LocalDateTime.parse("2026-08-24T12:00:00"),
                LocalDateTime.parse("2026-08-24T11:00:00"), 0, 20);

        assertThrows(MaintenanceValidationException.class, () -> service.search("tenant-1", criteria));
    }

    private MaintenanceCaseDetailQueryResult detail() {
        FieldChangeQueryResult field = new FieldChangeQueryResult(
                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile", "policy.field.holder.mobile",
                PolicyFieldDataType.TEXT, "13800000000", "13700000000", "13900000000", null,
                MaintenanceFieldConflictStatus.DETECTED, null,
                PolicyFieldSensitivityLevel.SENSITIVE, PolicyFieldMaskingPolicy.MOBILE,
                "POLICY_INFO_CHANGE");
        WorkflowEffectRequestEvidenceQueryResult request = new WorkflowEffectRequestEvidenceQueryResult(
                "effect-request-1", "a".repeat(64), 7, EffectiveTimeType.IMMEDIATE,
                LocalDateTime.parse("2026-08-24T10:04:00"), "b".repeat(64),
                LocalDateTime.parse("2026-08-24T10:04:00"));
        WorkflowPolicyApplicationEvidenceQueryResult application =
                new WorkflowPolicyApplicationEvidenceQueryResult(
                        "effect-request-1", "END-001", 7, 8, "c".repeat(64),
                        new SnapshotReferenceQueryResult(
                                "snapshot://case-1/applied", "d".repeat(64), 8L,
                                "2026-08-24T10:05:00+08:00"),
                        List.of(new WorkflowAppliedFieldEvidenceQueryResult(
                                "POLICY_INFO_CHANGE", "policy-1", "policy.holder.mobile",
                                PolicyFieldDataType.TEXT, "13900000000")),
                        LocalDateTime.parse("2026-08-24T10:05:00"));
        WorkflowTaskQueryResult task = new WorkflowTaskQueryResult(
                "case-1:POLICY_INFO_CHANGE:DATA_ENTRY", "POLICY_INFO_CHANGE", 0, 2,
                MaintenanceStepType.DATA_ENTRY, MaintenanceStepMode.REQUIRED, null,
                MaintenanceWorkflowTaskStatus.READY, null, 0, null, null, null,
                null, null, null, null,
                new WorkflowEffectEvidenceQueryResult(request, application), null);
        return new MaintenanceCaseDetailQueryResult(
                "case-1", "policy-1", "P202608240001", "customer-1",
                "product-1", "product-v1", "plan-v1", 7L,
                "2026-08-01T00:00:00+08:00", MaintenanceChannel.MANUAL,
                MaintenanceStatus.PENDING, EffectiveTimeType.IMMEDIATE, null, "联系方式变更",
                "operator-1", LocalDateTime.parse("2026-08-24T10:00:00"),
                "operator-1", LocalDateTime.parse("2026-08-24T10:05:00"),
                List.of(), List.of(task), List.of(field), new SnapshotSetQueryResult(null, null, null));
    }
}
