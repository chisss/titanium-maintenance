package com.titanium.maintenance.application.command.premium;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementInput;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.application.model.settlement.MaintenanceReversalSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementInput;
import com.titanium.maintenance.application.model.settlement.MaintenanceSurrenderSettlementResult;
import com.titanium.maintenance.application.orchestration.MaintenancePremiumSettlementOrchestrator;

import lombok.RequiredArgsConstructor;

/** Web/API 可调用的保全费用登记写用例入口。 */
@Service
@RequiredArgsConstructor
public class MaintenancePremiumSettlementCommandService {

    private final MaintenancePremiumSettlementOrchestrator orchestrator;

    public MaintenancePremiumSettlementResult settle(
            String maintenanceId, String tenantId, MaintenancePremiumSettlementInput input) {
        return orchestrator.settle(maintenanceId, tenantId, input);
    }

    public MaintenanceSurrenderSettlementResult settleSurrender(
            String maintenanceId, String tenantId, MaintenanceSurrenderSettlementInput input) {
        return orchestrator.settleSurrender(maintenanceId, tenantId, input);
    }

    public MaintenancePremiumSettlementResult settleReversal(
            String maintenanceId, String tenantId, MaintenanceReversalSettlementInput input) {
        return orchestrator.settleReversal(maintenanceId, tenantId, input);
    }
}
