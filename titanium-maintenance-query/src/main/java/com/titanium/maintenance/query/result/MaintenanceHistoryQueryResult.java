package com.titanium.maintenance.query.result;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictStatus;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.enums.workflow.MaintenanceEffectStatus;
import com.titanium.metadata.enums.maintenance.MaintenanceType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

/**
 * 跨案保全历史查询结果（CQRS 读侧稳定返回契约，G05/AC-07）。
 *
 * <p>
 * 🔴 <b>不得直接返回读模型实体</b>（项目 CLAUDE.md §3.4.9 ③）：本类型是查询出口的稳定契约，
 * 读模型列（如 {@code view/entity} 命名、乐观锁版本、租户列）一律不外泄；一张 View 可投影多个
 * QueryResult，反之亦然。
 * </p>
 * <p>
 * 🔴 <b>三要素</b>（{@link #effectiveStart} / {@link #effectiveEnd} / {@link #feeDirection} /
 * {@link #endorsementNo}）是需求文档 §14 明列的检索与展示要素，两端以 ISO-8601 字符串同型对外
 * （{@code LocalDateTime} 直出会在秒为 0 时省略秒位，形态不稳定）。
 * </p>
 * <p>
 * 🔴 <b>最终一致</b>：本结果来自事件投影维护的读模型（{@code t_maintenance_view} +
 * {@code t_maintenance_field_change_view}），存在投影延迟，<b>仅供对客展示与对账</b>；
 * 写侧不得据此做金额/资格决策（§3.4.9 ⑥）。
 * </p>
 *
 * @param maintenanceId 案件ID
 * @param maintenanceNo 案件号
 * @param policyId      保单ID
 * @param policyNumber  保单号
 * @param maintenanceType 保全类型
 * @param status        案件流程状态
 * @param effectStatus  Policy 生效状态
 * @param effectiveStart 生效区间起（约定生效日，ISO-8601）
 * @param effectiveEnd   生效区间止（业务生效时点，ISO-8601 含偏移）
 * @param feeDirection  费用方向
 * @param endorsementNo 外部单证号（批单号；未生效为 {@code null}）
 * @param createdAt     案件创建时间
 * @param fieldChanges  该案件的字段级变更明细（按保全项 → 字段 → 对象排序）
 */
public record MaintenanceHistoryQueryResult(
        String maintenanceId,
        String maintenanceNo,
        String policyId,
        String policyNumber,
        MaintenanceType maintenanceType,
        MaintenanceStatus status,
        MaintenanceEffectStatus effectStatus,
        String effectiveStart,
        String effectiveEnd,
        MaintenanceBalanceDirection feeDirection,
        String endorsementNo,
        LocalDateTime createdAt,
        List<FieldChangeHistory> fieldChanges) {

    /** 字段级变更历史行（跨案检索的最小自描述单元）。 */
    public record FieldChangeHistory(
            String itemCode,
            String objectId,
            String fieldCode,
            String labelKey,
            PolicyFieldDataType dataType,
            String baseValue,
            String currentValue,
            String proposedValue,
            String appliedValue,
            MaintenanceFieldConflictStatus conflictStatus,
            String resolutionCode,
            String effectiveStart,
            String effectiveEnd,
            MaintenanceBalanceDirection feeDirection,
            String endorsementNo,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            String changeTypeCode) {
    }
}
