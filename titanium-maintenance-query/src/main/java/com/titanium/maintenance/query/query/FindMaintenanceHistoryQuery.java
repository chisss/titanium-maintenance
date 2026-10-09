package com.titanium.maintenance.query.query;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;

/**
 * 按保单检索跨案保全历史（CQRS 读侧查询入参，G05/AC-07）。
 *
 * <p>
 * 🔴 <b>检索主键是保单</b>（{@code policyId} 或 {@code policyNumber} 至少给一个）：需求文档 §14 要求
 * 「按案件号、外部单号、保单号、客户、项目、来源、状态、生效区间、费用方向和处理人检索」，其中
 * **字段级**历史原先无任何入口（{@code t_maintenance_field_change_view} 既无保单号也无案件号）
 * ⇒ 本查询补齐的正是「按保单一次取回全部案件及其字段级变更」。
 * </p>
 * <p>
 * 其余条件为**可选收窄**（项目、费用方向、生效区间）：生效区间按案件「约定生效日」
 * （{@code specific_effective_date}）落在 {@code [effectiveFrom, effectiveTo)} 判定，
 * 半开区间避免双端闭合把次日零点计入（与 {@code MaintenanceViewRepository} 统计口径一致）。
 * 区间两端的业务语义（是否为「值有效期窗口」）待裁示，见 G05 §六 R-14。
 * </p>
 *
 * @param tenantId      租户ID（必填，下推到 SQL 做租户隔离）
 * @param policyId      保单ID（与 policyNumber 至少给一个）
 * @param policyNumber  保单号（对外检索口径，与 policyId 至少给一个）
 * @param itemCode      保全项编码（可选收窄，同时收窄案件级与字段级行）
 * @param feeDirection  费用方向（可选收窄：DEBIT 补收 / CREDIT 退费 / NONE 无费用）
 * @param effectiveFrom 约定生效日起（含，可空）
 * @param effectiveTo   约定生效日止（不含，可空）
 */
public record FindMaintenanceHistoryQuery(
        String tenantId,
        String policyId,
        String policyNumber,
        String itemCode,
        MaintenanceBalanceDirection feeDirection,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo) {
}
