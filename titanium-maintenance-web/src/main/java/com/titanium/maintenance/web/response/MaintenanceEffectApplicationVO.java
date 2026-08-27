package com.titanium.maintenance.web.response;

import java.time.LocalDateTime;

/** Maintenance 已记录 Policy 权威回执后的接口结果。 */
public record MaintenanceEffectApplicationVO(
        String requestId,
        String endorsementNo,
        long actualPolicyVersion,
        String applicationHash,
        LocalDateTime appliedAt) {
}
