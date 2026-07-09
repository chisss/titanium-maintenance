package com.titanium.maintenance.service;

import com.titanium.maintenance.valueobject.SurrenderDetail;
import com.titanium.maintenance.valueobject.SurrenderRefundResult;

/**
 * 退保退费计算领域服务（纯领域服务）
 * <p>
 * 承载退保退费的跨聚合纯业务规则：犹豫期内退保全额退还已缴保费，犹豫期外退保退还保单现金价值。
 * 遵循「三无」纪律——无 CommandGateway、无外部 Port、无基础设施依赖，入参/出参仅值对象，
 * 可脱离 Spring 容器以 {@code new} 直测。取数据（现金价值来源）与发命令由应用层编排。
 * </p>
 */
public interface SurrenderRefundDomainService {

    /**
     * 计算退保退费。
     *
     * @param detail 退保明细（已缴保费/现金价值/犹豫期/申请日）
     * @return 退费计算结果（退费类型 + 退费金额）
     */
    SurrenderRefundResult calculateRefund(SurrenderDetail detail);
}
