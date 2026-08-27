package com.titanium.maintenance.application.model;

import java.time.LocalDateTime;

/** Maintenance 已记录 Policy 权威回执后的同步结果。 */
public record MaintenanceEffectApplicationResult(
        String requestId,
        String endorsementNo,
        long actualPolicyVersion,
        String applicationHash,
        LocalDateTime appliedAt) {
}
