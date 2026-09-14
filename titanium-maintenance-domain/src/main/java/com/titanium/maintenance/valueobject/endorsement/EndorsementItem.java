package com.titanium.maintenance.valueobject.endorsement;

import java.util.List;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 批单中的一个保全项及其字段变更值对象。
 *
 * @param itemCode     保全项编码
 * @param itemName     保全项名称（取自案件创建时冻结的配置快照，非硬编码文案）
 * @param fieldChanges 该保全项下已生效的字段变更
 */
public record EndorsementItem(String itemCode, String itemName, List<EndorsementFieldChange> fieldChanges) {

    public EndorsementItem {
        if (itemCode == null || itemCode.isBlank()) {
            throw new MaintenanceValidationException("EndorsementItem", "itemCode", "保全项编码不能为空");
        }
        fieldChanges = fieldChanges == null ? List.of() : List.copyOf(fieldChanges);
    }
}
