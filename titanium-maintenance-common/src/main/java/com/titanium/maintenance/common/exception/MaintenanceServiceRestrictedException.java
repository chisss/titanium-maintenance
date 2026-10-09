package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 服务受限准入拒绝异常——保单处于挂失等服务受限之下时，减损类保全项不予受理。
 *
 * <p>
 * 由 {@code MaintenanceServiceRestrictionAdmission.require(...)} 在建案前置校验中抛出，两条建案路径共用
 * （旧版入口与独立建案入口）。错误码固定为
 * {@link MaintenanceErrorCode#MAINTENANCE_SERVICE_RESTRICTED}。
 * </p>
 *
 * <p>
 * 🔴 <b>与生效期权威拒绝的分层</b>：本异常表达「<b>不受理</b>」（案件未建、零副作用）；保单域在合同应用时
 * 抛出的 {@code PolicyErrorCode.POLICY_SERVICE_RESTRICTED} 表达「<b>已受理但不放行</b>」。两层都做——
 * 前置只求明显受限的案件不进入流程、避免走完审批才在生效环节被拒，权威判定仍在保单域写侧。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
public class MaintenanceServiceRestrictedException extends DomainException {

    /**
     * 构造服务受限准入拒绝异常。
     *
     * @param errorCode 拒绝错误码（71 段 MaintenanceErrorCode）
     * @param message 拒绝描述（含受限种类、被拒保全项）
     */
    public MaintenanceServiceRestrictedException(MaintenanceErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
