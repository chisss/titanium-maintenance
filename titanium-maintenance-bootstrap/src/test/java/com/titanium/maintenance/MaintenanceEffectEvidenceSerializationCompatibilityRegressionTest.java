package com.titanium.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectEvidence;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectRequestEvidence;

/**
 * Regression: API-28 生效请求事件因派生 applied 属性无法回放。
 * Found by /qa on 2026-08-26
 * Report: .gstack/qa-reports/qa-report-maintenance-fullstack-2026-08-26.md
 */
class MaintenanceEffectEvidenceSerializationCompatibilityRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldIgnoreLegacyAppliedPropertyAndStopWritingIt() throws Exception {
        MaintenanceEffectEvidence evidence = MaintenanceEffectEvidence.requested(
                new MaintenanceEffectRequestEvidence(
                        "request-1", "a".repeat(64), 0, EffectiveTimeType.IMMEDIATE,
                        LocalDateTime.of(2026, 8, 26, 17, 0), "b".repeat(64),
                        LocalDateTime.of(2026, 8, 26, 16, 59)));
        ObjectNode json = objectMapper.valueToTree(evidence);

        assertFalse(json.has("applied"));
        json.put("applied", false);

        assertEquals(evidence, objectMapper.treeToValue(json, MaintenanceEffectEvidence.class));
    }
}
