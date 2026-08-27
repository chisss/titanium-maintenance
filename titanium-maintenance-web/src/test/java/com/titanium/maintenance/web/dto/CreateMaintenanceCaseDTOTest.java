package com.titanium.maintenance.web.dto;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.EffectiveTimeType;

class CreateMaintenanceCaseDTOTest {

    @Test
    void shouldRejectUnknownField() {
        CreateMaintenanceCaseDTO request = new CreateMaintenanceCaseDTO(
                "policy-1", null, List.of("POLICY_INFO_CHANGE"), EffectiveTimeType.IMMEDIATE,
                null, "变更手机号", "request-1");

        assertThrows(IllegalArgumentException.class,
                () -> request.rejectUnknownField("baseValue", "forged"));
    }
}
