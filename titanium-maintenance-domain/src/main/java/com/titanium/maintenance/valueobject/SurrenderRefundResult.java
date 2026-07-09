package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;

import com.titanium.maintenance.common.enums.RefundType;

/**
 * 退保退费计算结果值对象
 *
 * @param refundType 退费类型（全额退 / 扣现金价值）
 * @param refundAmount 退费金额
 * @param withinCoolingOff 是否犹豫期内退保
 */
public record SurrenderRefundResult(RefundType refundType, BigDecimal refundAmount, boolean withinCoolingOff) {
}
