package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 独立保全案件误用旧版整案执行入口的通道冲突异常（HTTP 409）。
 * <p>
 * 保全域并存两代案件，按案件代数二选一：
 * <ul>
 *   <li><b>独立建案</b>（{@code t_maintenance_view.independent_case = true}）：案件平台任务级生效，
 *       经 {@code MaintenanceEffectApplicationService} → Policy 同步应用链回写保单；</li>
 *   <li><b>历史案件</b>（{@code independent_case = false}）：旧版整案执行，
 *       经 {@code maintenance-executed} Kafka 主题回写保单。</li>
 * </ul>
 * 独立案件若再走旧入口，同一保单会被两条通道重复施加影响（缺陷 D9），故在旧入口前置拒绝。
 * </p>
 */
public class MaintenanceLegacyExecutionIndependentCaseForbiddenException extends BusinessException {

    private static final MaintenanceErrorCode ERROR_CODE =
            MaintenanceErrorCode.MAINTENANCE_LEGACY_EXECUTION_INDEPENDENT_CASE_FORBIDDEN;

    public MaintenanceLegacyExecutionIndependentCaseForbiddenException() {
        super(ERROR_CODE.getMessage(), ERROR_CODE);
    }
}
