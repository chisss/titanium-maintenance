package com.titanium.maintenance.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 部分领取额度值对象（g04-01）
 * <p>
 * 承载部分领取（以现金价值为限的领取）的额度不变量：领取申请金额不得超过可用现金价值 ×
 * 可领取比例。可领取比例来源于产品定价配置（R-1 裁示：product 定价配置承载），本值对象
 * 只承载「比例须在 (0,1]」「领取额不得超上限」等不变量与校验，不含阈值字面量
 * （AC-11 阈值单一来源 = 产品配置）。超限必须显式拒绝并携 71 段
 * {@link MaintenanceErrorCode#MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA}，不得静默截断。
 * </p>
 *
 * @param availableCashValue 可用现金价值
 * @param withdrawalRatio 可领取比例（如 0.5 表示最高可领取现金价值的 50%）
 * @param requestedAmount 本次申请领取金额
 */
public record WithdrawalQuota(BigDecimal availableCashValue, BigDecimal withdrawalRatio,
                              BigDecimal requestedAmount) {

    public WithdrawalQuota {
        if (availableCashValue == null || availableCashValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA,
                    "WithdrawalQuota", "可用现金价值不能为空或负数");
        }
        if (withdrawalRatio == null || withdrawalRatio.compareTo(BigDecimal.ZERO) <= 0
                || withdrawalRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA,
                    "WithdrawalQuota", "可领取比例须在 (0,1] 区间");
        }
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA,
                    "WithdrawalQuota", "申请领取金额必须大于零");
        }
    }

    /**
     * 可领取上限 = 可用现金价值 × 可领取比例。
     *
     * @return 本次最多可领取金额（不为负）
     */
    public BigDecimal maxWithdrawableAmount() {
        BigDecimal cap = availableCashValue.multiply(withdrawalRatio).setScale(2, RoundingMode.HALF_UP);
        return cap.compareTo(BigDecimal.ZERO) > 0 ? cap : BigDecimal.ZERO;
    }

    /**
     * 申请领取金额是否在可领取上限内。
     *
     * @return 未超上限返回 {@code true}
     */
    // 🔴 @JsonIgnore 不可删（D-501-19）：派生 getter 会被 Axon 的 Jackson 序列化器写进事件载荷，
    //    重放时报 UnrecognizedPropertyException → 聚合无法加载。
    @JsonIgnore
    public boolean isWithinWithdrawalLimit() {
        return requestedAmount.compareTo(maxWithdrawableAmount()) <= 0;
    }

    /**
     * 强制校验申请领取金额在可领取上限内：超限即抛业务异常（携 71 段
     * {@link MaintenanceErrorCode#MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA} 错误码），
     * 不得静默截断到上限——额度拒绝必须是显式失败（g04-01）。
     *
     * @throws MaintenanceValidationException 申请额超过可领取上限时抛出
     */
    public void requireWithinWithdrawalLimit() {
        if (!isWithinWithdrawalLimit()) {
            throw new MaintenanceValidationException(MaintenanceErrorCode.MAINTENANCE_WITHDRAWAL_EXCEEDS_QUOTA,
                    "WithdrawalQuota", "申请领取金额超过可领取上限 " + maxWithdrawableAmount());
        }
    }
}
