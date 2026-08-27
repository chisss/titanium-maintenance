package com.titanium.maintenance.configuration.control;

import com.titanium.maintenance.common.enums.config.MaintenanceFeeMode;
import com.titanium.maintenance.common.enums.config.MaintenancePremiumRecalculationTiming;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全收退费公式、结算门禁与重算时点。 */
public record MaintenanceFeeRule(String formulaCode, String settlementGateRuleCode,
        MaintenancePremiumRecalculationTiming recalculationTiming) {

    public MaintenanceFeeRule {
        formulaCode = normalize(formulaCode);
        settlementGateRuleCode = normalize(settlementGateRuleCode);
        if (recalculationTiming == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceFeeRule", "recalculationTiming", "费用重算时点不能为空");
        }
    }

    /** 创建无收退费规则。 */
    public static MaintenanceFeeRule none() {
        return new MaintenanceFeeRule(null, null, MaintenancePremiumRecalculationTiming.NOT_APPLICABLE);
    }

    /** 校验费用配置与保全项费用模式一致。 */
    public void validateFor(MaintenanceFeeMode feeMode) {
        if (feeMode == MaintenanceFeeMode.NONE) {
            if (formulaCode != null || settlementGateRuleCode != null
                    || recalculationTiming != MaintenancePremiumRecalculationTiming.NOT_APPLICABLE) {
                throw invalid("无收退费保全项不能配置费用公式、结算门禁或重算时点");
            }
            return;
        }
        if (formulaCode == null || settlementGateRuleCode == null
                || recalculationTiming == MaintenancePremiumRecalculationTiming.NOT_APPLICABLE) {
            throw invalid("存在收退费的保全项必须配置费用公式、结算门禁和重算时点");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static MaintenanceValidationException invalid(String message) {
        return new MaintenanceValidationException("MaintenanceFeeRule", message);
    }
}
