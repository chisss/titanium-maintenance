package com.titanium.maintenance.application.model.effect;

import java.time.LocalDateTime;

/**
 * 减额缴清核定指令（application 层编排入参，g04-05）。
 * <p>
 * 由生效编排在分流到减额缴清出口时组装。与贷款/账户转换出口的关键差异：<b>本指令不含任何用户输入</b>
 * （减额缴清没有「金额」可填——新保额完全由「现金价值 ÷ 趸缴净费率」核定），故无请求载荷摘要可比对，
 * 幂等重试的一致性由后续 {@code ApplicationRequest.requestPayloadHash}（含两条派生字段变更）承担。
 * </p>
 * <p>
 * 保单标识与产品标识均取自案件读模型（不额外取数）：产品是费率策略的定位键，
 * 保单是现金价值/原保额的取数键，二者都是案件的既有事实。
 * </p>
 *
 * @param tenantId 租户ID
 * @param maintenanceId 保全案件号（仅用于日志与错误文案定位）
 * @param policyId 保单ID（取现金价值、原保额与主险段标识）
 * @param productId 产品ID（查产品域已发布的减额缴清净费率策略）
 * @param businessTime 业务时点（定位适用费率版本；同时作为生效时点，不得与合同变更时点分叉）
 */
public record ReducedPaidUpOrder(
        String tenantId,
        String maintenanceId,
        String policyId,
        String productId,
        LocalDateTime businessTime) {
}
