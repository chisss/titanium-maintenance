package com.titanium.maintenance.valueobject.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

class MaintenanceItemCodeTest {

    @Test
    void shouldMapSurrenderAliasToPolicyTermination() {
        assertEquals(
                MaintenanceType.POLICY_TERMINATION,
                MaintenanceItemCode.of("SURRENDER").legacyMaintenanceType());
    }

    @Test
    void shouldRejectItemWithoutLegacyTypeMapping() {
        MaintenanceItemCode code = MaintenanceItemCode.of("CUSTOM_CHANGE");

        assertThrows(MaintenanceValidationException.class, code::legacyMaintenanceType);
    }
}
