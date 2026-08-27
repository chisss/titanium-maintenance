package com.titanium.maintenance.valueobject.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceChangeType;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

class MaintenanceFieldChangeTest {

    @Test
    void shouldNormalizeTypedValues() {
        MaintenanceFieldValue decimal = MaintenanceFieldValue.decimal(new BigDecimal("100.000"));
        MaintenanceFieldValue object = MaintenanceFieldValue.object("{\"b\":2,\"a\":1}");

        assertEquals("100", decimal.canonicalValue());
        assertEquals("{\"a\":1,\"b\":2}", object.canonicalValue());
    }

    @Test
    void shouldDetectResolveAndApplyOutOfSequenceConflict() {
        MaintenanceFieldChange proposed = MaintenanceFieldChange.propose(
                "CONTACT_CHANGE", "policy-1", "policy.contact.mobile",
                MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"));

        MaintenanceFieldChange conflict = proposed.refreshCurrent(MaintenanceFieldValue.text("13700000000"));
        assertTrue(conflict.hasUnresolvedConflict());
        assertEquals(MaintenanceFieldConflictStatus.DETECTED, conflict.conflictStatus());

        MaintenanceFieldChange resolved = conflict.resolveUsingProposed("KEEP_PROPOSED");
        MaintenanceFieldChange applied = resolved.markApplied(MaintenanceFieldValue.text("13900000000"));

        assertFalse(applied.hasUnresolvedConflict());
        assertEquals(MaintenanceFieldConflictStatus.RESOLVED, applied.conflictStatus());
        assertEquals(MaintenanceChangeType.MODIFY, applied.changeType());
        assertEquals("13900000000", applied.appliedValue().canonicalValue());
    }

    @Test
    void shouldResolveUsingCurrentOrReenteredValue() {
        MaintenanceFieldChange conflict = MaintenanceFieldChange.propose(
                        "CONTACT_CHANGE", "policy-1", "policy.contact.mobile",
                        MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"));

        MaintenanceFieldChange current = conflict.resolveUsingCurrent();
        MaintenanceFieldChange reentered = conflict.resolveUsingReentered(MaintenanceFieldValue.text("13600000000"));

        assertEquals(current.currentValue(), current.proposedValue());
        assertEquals(MaintenanceFieldConflictResolutionAction.USE_CURRENT.getCode(), current.resolutionCode());
        assertEquals("13600000000", reentered.proposedValue().canonicalValue());
        assertEquals(MaintenanceFieldConflictResolutionAction.REENTER.getCode(), reentered.resolutionCode());
    }

    @Test
    void shouldKeepExplicitResolutionWhenPolicyCurrentValueIsUnchanged() {
        MaintenanceFieldChange resolved = MaintenanceFieldChange.propose(
                        "CONTACT_CHANGE", "policy-1", "policy.contact.mobile",
                        MaintenanceFieldValue.text("13800000000"), MaintenanceFieldValue.text("13900000000"))
                .refreshCurrent(MaintenanceFieldValue.text("13700000000"))
                .resolveUsingProposed(MaintenanceFieldConflictResolutionAction.USE_PROPOSED.getCode());

        assertEquals(resolved, resolved.refreshCurrent(MaintenanceFieldValue.text("13700000000")));
    }

    @Test
    void shouldRejectDifferentFieldValueTypes() {
        assertThrows(MaintenanceValidationException.class, () -> MaintenanceFieldChange.propose(
                "COVERAGE_CHANGE", "coverage-1", "coverage.sumInsured",
                MaintenanceFieldValue.decimal(new BigDecimal("100000")), MaintenanceFieldValue.text("200000")));
    }

    @Test
    void shouldRequireProposedSnapshotBeforeAppliedSnapshot() {
        MaintenanceSnapshotReference before = snapshot("before", 3);
        MaintenanceSnapshotReference applied = snapshot("applied", 4);

        assertThrows(MaintenanceValidationException.class,
                () -> MaintenanceSnapshotSet.capturedBefore(before).attachApplied(applied));
    }

    @Test
    void shouldRejectInvalidTypedValue() {
        assertThrows(MaintenanceValidationException.class,
                () -> new MaintenanceFieldValue(PolicyFieldDataType.DATE, "2026-02-30"));
    }

    private MaintenanceSnapshotReference snapshot(String storageKey, long policyVersion) {
        return new MaintenanceSnapshotReference(storageKey, "a".repeat(64), policyVersion,
                OffsetDateTime.parse("2026-08-24T10:00:00+08:00"));
    }
}
