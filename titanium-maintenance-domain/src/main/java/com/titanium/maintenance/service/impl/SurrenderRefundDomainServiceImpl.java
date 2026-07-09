package com.titanium.maintenance.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.common.enums.RefundType;
import com.titanium.maintenance.service.SurrenderRefundDomainService;
import com.titanium.maintenance.valueobject.SurrenderDetail;
import com.titanium.maintenance.valueobject.SurrenderRefundResult;

/**
 * 退保退费计算领域服务实现（纯领域逻辑，无 Port/无 Repository/无 CommandGateway）
 * <p>
 * 规则：犹豫期内退保 → 全额退还已缴保费（{@code FULL_REFUND}）；犹豫期外退保 → 退还保单现金价值
 * （{@code DEDUCT_CASH_VALUE}）。标注 {@code @Service} 便于容器装配，但不注入任何依赖，可 {@code new} 直测。
 * </p>
 */
@Service
public class SurrenderRefundDomainServiceImpl implements SurrenderRefundDomainService {

    @Override
    public SurrenderRefundResult calculateRefund(SurrenderDetail detail) {
        boolean withinCoolingOff = detail.isWithinCoolingOff();
        if (withinCoolingOff) {
            return new SurrenderRefundResult(RefundType.FULL_REFUND, detail.premiumPaid(), true);
        }
        BigDecimal refund = detail.cashValue() != null ? detail.cashValue() : BigDecimal.ZERO;
        return new SurrenderRefundResult(RefundType.DEDUCT_CASH_VALUE, refund, false);
    }
}
