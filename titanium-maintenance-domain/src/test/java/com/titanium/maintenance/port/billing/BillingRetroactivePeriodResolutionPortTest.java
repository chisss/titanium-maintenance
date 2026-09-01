package com.titanium.maintenance.port.billing;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionFact;
import com.titanium.maintenance.port.billing.BillingRetroactivePeriodResolutionPort.ResolutionLineFact;

class BillingRetroactivePeriodResolutionPortTest {

    @Test
    void shouldRejectDuplicatePeriodLines() {
        ResolutionLineFact line = line("period-1", YearMonth.of(2026, 8));

        assertThrows(MaintenanceValidationException.class, () -> fact(List.of(line, line)));
    }

    @Test
    void shouldRejectLineWithDifferentTargetPeriod() {
        ResolutionLineFact line = line("period-1", YearMonth.of(2026, 9));

        assertThrows(MaintenanceValidationException.class, () -> fact(List.of(line)));
    }

    private ResolutionFact fact(List<ResolutionLineFact> lines) {
        return new ResolutionFact(
                "billing-resolution-1", "request-1", "billing-batch-1", "tenant-1",
                "maintenance-1", "policy-1", hash('a'), YearMonth.of(2026, 8),
                "COMPLETED", lines.size(), hash('b'), hash('c'), "结转至开放期间",
                "operator-1", LocalDateTime.of(2026, 8, 26, 14, 0), lines);
    }

    private ResolutionLineFact line(String periodId, YearMonth targetPeriod) {
        return new ResolutionLineFact(
                periodId, YearMonth.of(2026, 7), targetPeriod,
                MaintenanceBalanceDirection.DEBIT, new BigDecimal("20.00"), "CNY",
                "posting-1", hash('d'), hash('e'));
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
