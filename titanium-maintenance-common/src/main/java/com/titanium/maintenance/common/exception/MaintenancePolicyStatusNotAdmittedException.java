package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 状态准入拒绝异常——(保全项, 保单状态) 组合不在准入矩阵取值内。
 *
 * <p>
 * 由 {@code MaintenanceAdmissionMatrix.require(...)} 在建案前置校验中抛出，两条建案路径共用
 * （旧版入口与独立建案入口）。错误码随准入规则登记：既有 8 类沿用
 * {@link MaintenanceErrorCode#POLICY_NOT_ACTIVE} / {@link MaintenanceErrorCode#POLICY_NOT_TERMINATED}
 * 保持对外契约不变，其余类型使用
 * {@link MaintenanceErrorCode#MAINTENANCE_POLICY_STATUS_NOT_ADMITTED}。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
public class MaintenancePolicyStatusNotAdmittedException extends DomainException {

    /**
     * 构造状态准入拒绝异常。
     *
     * @param errorCode 拒绝错误码（取自准入规则登记），须为 71 段 MaintenanceErrorCode
     * @param message 拒绝描述（含保全项编码、当前保单状态与准入状态集合）
     */
    public MaintenancePolicyStatusNotAdmittedException(MaintenanceErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
