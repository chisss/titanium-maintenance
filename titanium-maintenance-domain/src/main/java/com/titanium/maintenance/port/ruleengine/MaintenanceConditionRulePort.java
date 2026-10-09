package com.titanium.maintenance.port.ruleengine;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 字段条件规则求值端口（对端域：rule-engine）。
 * <p>
 * 保全项的字段规则（{@code MaintenanceFieldRule}）可携带 {@code conditionRuleCode}——
 * 该码此前<b>只被发布校验收集、从未被执行</b>（G09 缺口）。本端口让既有编码获得执行语义：
 * 字段提案受理前把该码送规则引擎求值，条件不成立 ⇒ 该字段<b>被拒收</b>（拒绝发生在派发命令之前）。
 * </p>
 * <p>
 * 🔴 <b>失败语义（失败关闭，禁降级）</b>：规则集不存在/未激活、规则引擎不可达、应答非成功码或载荷无效，
 * 一律<b>抛出</b>（{@code MaintenanceRemoteCallException}，错误码 71 段），
 * <b>不得</b>降级为「视为通过」或「视为拒绝」——「规则说有」与「问不到规则」若同形，
 * 配置里的条件规则会静默失效而全链无异常可看。
 * </p>
 * <p>
 * 🔴 <b>求值变量集（与规则编写者的契约）</b>：本端口固定供给三个变量，键名见下列常量。
 * 规则表达式引用任何<b>未供给</b>的变量都会在求值期显式失败（SpEL 对未知变量抛错，不静默取 null），
 * 因此变量集是契约而非实现细节：
 * </p>
 * <ul>
 *   <li>{@link #FACT_ITEM_CODE} —— 保全项编码（如 {@code POLICY_INFO_CHANGE}）；</li>
 *   <li>{@link #FACT_FIELD_CODE} —— 字段码（如 {@code policy.holder.mobile}）——
 *       🔴 只能作为<b>值</b>传递：SpEL 变量名须为标识符语法，含点号的字段码不可作键；</li>
 *   <li>{@link #FACT_VALUE} —— 本次提案的规范化值（清空类提案为 {@code null}）。</li>
 * </ul>
 */
public interface MaintenanceConditionRulePort {

    /** 变量名：保全项编码 */
    String FACT_ITEM_CODE = "itemCode";

    /** 变量名：字段码 */
    String FACT_FIELD_CODE = "fieldCode";

    /** 变量名：本次提案的规范化值（可为 null） */
    String FACT_VALUE = "value";

    /**
     * 求值字段条件规则。
     *
     * @param request 求值请求
     * @return {@code true} = 条件成立、字段放行；{@code false} = 条件不成立、字段拒收
     * @throws com.titanium.maintenance.common.exception.MaintenanceRemoteCallException 规则引擎不可达 / 应答无效
     */
    boolean isAccepted(ConditionRuleRequest request);

    /** 字段条件规则求值请求。 */
    record ConditionRuleRequest(String tenantId, String maintenanceId, String ruleSetCode,
            String itemCode, String fieldCode, String value) {

        public ConditionRuleRequest {
            tenantId = requireText("tenantId", tenantId);
            maintenanceId = requireText("maintenanceId", maintenanceId);
            ruleSetCode = requireText("ruleSetCode", ruleSetCode);
            itemCode = requireText("itemCode", itemCode);
            fieldCode = requireText("fieldCode", fieldCode);
            // value 允许为空：清空类提案本就没有值，规则引用它时自会判不为真（失败关闭）
        }

        private static String requireText(String fieldName, String value) {
            if (value == null || value.isBlank()) {
                throw new MaintenanceValidationException("ConditionRuleRequest", fieldName, "字段不能为空");
            }
            return value.trim();
        }
    }
}
