package com.titanium.maintenance.valueobject.endorsement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.item.MaintenanceItemInstance;

/**
 * 保全批单要素值对象（跨域自足载荷）。
 * <p>
 * 承载「保全案件已生效」这一事实的完整对外凭证内容：由写侧聚合在案件级生效回执落定时固化为
 * {@code MaintenanceEndorsementIssuedEvent} 的载荷，经出口 Port 交付 document 域渲染正文与建档，全程
 * <b>不再回查任何读模型</b>——批单要素属写侧已发生的事实，读侧投影的最终一致滞后不得成为凭证据取路径。
 * </p>
 * <p>
 * 🔴 <b>只载事实，不载展示文案</b>：字段一律以<b>编码</b>呈现，中文名的跨域渲染由 document 域按自身语言
 * 策略处理（{@code DocumentType} 属 document-common，非跨域共享枚举，参 m14-1705 端口契约判据）。
 * </p>
 * <p>
 * 🔴 <b>批单号随要素透传，消费方不得另行生成</b>（g05-08）：{@link #endorsementNo} 的唯一事实源是
 * <b>Policy 台账</b>——由 policy 域在保单批改落账时生成（保单应用出口为
 * {@code PolicyMaintenanceHashing.stableEndorsementNo}，随生效回执回到本域
 * {@code MaintenancePolicyApplicationEvidence.endorsementNo}），本域只搬运、不重算；document 域据此派生
 * 落盘文件名，亦不得再生成一次。三方（Policy 台账 / 本事件 / document 入参）必须<b>逐字相同</b>。
 * </p>
 *
 * @param maintenanceId       保全案件ID（业务号与出站幂等键）
 * @param policyId            被批改的保单ID
 * @param endorsementNo       批单号（Policy 台账生成的权威凭证号；见类注释，可为空——见下）
 * @param maintenanceTypeCode 保全类型编码
 * @param items               本次生效的保全项及其字段变更（结构化要素）
 * @param effectiveTime       批改生效时间（生效规则解算所得，可为空）
 * @param completedAt         案件生效完成时间（权威回执记录时刻）
 * @param tenantId            租户ID
 *                            <p>
 *                            {@code endorsementNo} <b>可为空</b>：案件有<b>五条互斥的生效出口</b>，仅「保单交易出口」
 *                            （{@code MaintenancePolicyApplicationEvidence}）产生批单号；投资账户转换 / 保单贷款放款 /
 *                            保单贷款还款 / 保单补发四条出口的回执均<b>不产生批单号</b>（各自回执类 javadoc 已载明），
 *                            其批单文书由本字段为空表达，消费方按 {@code maintenanceId} 兜底命名。<b>不得</b>为凑齐该字段
 *                            而在这四条路径上臆造编号。
 *                            </p>
 */
public record EndorsementDocument(String maintenanceId, String policyId, String endorsementNo,
        String maintenanceTypeCode, List<EndorsementItem> items, LocalDateTime effectiveTime,
        LocalDateTime completedAt, String tenantId) {

    public EndorsementDocument {
        if (maintenanceId == null || maintenanceId.isBlank()) {
            throw new MaintenanceValidationException("EndorsementDocument", "maintenanceId", "保全案件ID不能为空");
        }
        if (policyId == null || policyId.isBlank()) {
            throw new MaintenanceValidationException("EndorsementDocument", "policyId", "保单ID不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new MaintenanceValidationException("EndorsementDocument", "tenantId", "租户ID不能为空");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 由案件事实推导批单要素：案件保全项定义 + Policy 权威回执生效值 → 批单。
     * <p>
     * 🔴 <b>「变更后」优先取回执权威值</b>：{@link MaintenanceFieldChange#appliedValue()} 由
     * {@code markApplied} 写入，而该方法在本域<b>无生产调用点</b>（仅值对象单测覆盖），事件流中恒为
     * {@code null}；Policy 回执的 {@code appliedFields} 才是实际生效值的唯一权威来源。回执未覆盖某字段时
     * 回落 {@code proposedValue}（案件拟值），如实呈现、不臆造数据。
     * </p>
     *
     * @param itemInstances 案件冻结的保全项实例（携带字段级变更）
     * @param appliedValues 权威生效值索引，键由 {@link #fieldKey(String, String, String)} 构造
     * @param endorsementNo Policy 台账生成的批单号（案件未走保单交易出口时为空，见类注释）
     * @param effectiveTime 批改生效时间（可为空，正文以占位符呈现）
     * @param completedAt   案件生效完成时间
     */
    public static EndorsementDocument of(String maintenanceId, String policyId, String maintenanceTypeCode,
            List<MaintenanceItemInstance> itemInstances, Map<String, String> appliedValues, String endorsementNo,
            LocalDateTime effectiveTime, LocalDateTime completedAt, String tenantId) {
        Map<String, String> values = appliedValues == null ? Map.of() : appliedValues;
        List<EndorsementItem> items = (itemInstances == null ? List.<MaintenanceItemInstance>of() : itemInstances)
                .stream()
                .map(item -> toEndorsementItem(item, values))
                .toList();
        return new EndorsementDocument(maintenanceId, policyId, endorsementNo, maintenanceTypeCode, items,
                effectiveTime, completedAt, tenantId);
    }

    /**
     * 权威生效值索引的键：保全项 + 业务对象 + 字段。
     * <p>
     * 三者联合才唯一确定一个字段——同一保全项可作用于多个业务对象，不同保全项亦可作用于同一对象。
     * </p>
     */
    public static String fieldKey(String itemCode, String objectId, String fieldCode) {
        return itemCode + ":" + objectId + ":" + fieldCode;
    }

    /** 把一个保全项转为批单要素项：「变更前」取本案基准值，「变更后」取回执权威值（缺失时回落拟值）。 */
    private static EndorsementItem toEndorsementItem(MaintenanceItemInstance item, Map<String, String> appliedValues) {
        List<EndorsementFieldChange> fieldChanges = item.fieldChanges().stream()
                .map(change -> new EndorsementFieldChange(change.objectId(), change.fieldCode(),
                        canonicalValue(change.baseValue()),
                        appliedValues.getOrDefault(
                                fieldKey(change.itemCode(), change.objectId(), change.fieldCode()),
                                canonicalValue(change.proposedValue()))))
                .toList();
        return new EndorsementItem(item.itemCode(), item.name(), fieldChanges);
    }

    private static String canonicalValue(MaintenanceFieldValue value) {
        return value == null ? null : value.canonicalValue();
    }
}
