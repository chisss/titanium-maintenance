package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保额上下限值对象（g04-01）
 * <p>
 * 承载保额变更的允许区间不变量：申请保额必须落在 [minimum, maximum] 内，否则显式拒绝并携
 * 71 段 {@link MaintenanceErrorCode#MAINTENANCE_SUM_INSURED_OUT_OF_BOUND} 错误码——超限
 * 拒绝，不得静默截断到边界。区间数值来源于产品定价配置（R-1 裁示：product 定价配置承载），
 * 本值对象只承载不变量与校验，不含任何阈值字面量（AC-11 阈值单一来源 = 产品配置）。
 * </p>
 *
 * @param minimum 最低保额（null 视为 0）
 * @param maximum 最高保额（null 视为无上限）
 */
public record SumInsuredBound(BigDecimal minimum, BigDecimal maximum) {

    public SumInsuredBound {
        minimum = minimum == null ? BigDecimal.ZERO : minimum;
        if (minimum.compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND,
                    "SumInsuredBound", "最低保额不能为负数");
        }
        if (maximum != null && maximum.compareTo(minimum) < 0) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND,
                    "SumInsuredBound", "最高保额不能低于最低保额");
        }
    }

    /**
     * 校验申请保额在允许区间内：低于最低保额或高于最高保额即抛业务异常（携 71 段
     * {@link MaintenanceErrorCode#MAINTENANCE_SUM_INSURED_OUT_OF_BOUND}），
     * 超限必须显式拒绝，不得静默截断。
     *
     * @param proposed 申请保额
     * @throws MaintenanceValidationException 申请保额为 null 或越界时抛出
     */
    public void requireWithin(BigDecimal proposed) {
        if (proposed == null || proposed.compareTo(minimum) < 0
                || (maximum != null && proposed.compareTo(maximum) > 0)) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_SUM_INSURED_OUT_OF_BOUND,
                    "SumInsuredBound", "申请保额超出允许范围 [" + minimum + ", "
                            + (maximum == null ? "∞" : maximum) + "]");
        }
    }
}
