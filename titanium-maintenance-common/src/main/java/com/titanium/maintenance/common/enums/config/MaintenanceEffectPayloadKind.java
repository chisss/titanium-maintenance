package com.titanium.maintenance.common.enums.config;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 保全项的<b>生效载荷种类</b>——生效环节据以判断「这次合同变更到底把什么递给保单域」。
 * <p>
 * 之所以要这个分类：生效闸口原先只能识别「字段变更」与「合同状态动作」两种载荷，纯金额类保全项
 * （无字段、无状态动作、只有一笔收付差额）因此在结构化入口<b>结构性不可达</b>。历史修法是给闸口串
 * 一长串 {@code || xxxCase} 豁免——每新增一类出口就再串一段，闸口遂退化为「谁加谁知道」的补丁墙，
 * 且漏串一次即静默拦死整类业务。本枚举把「载荷是什么」变成<b>可声明的数据</b>，由
 * {@code MaintenanceEffectPayloadCatalog} 按保全项编码统一登记。
 * </p>
 * <p>
 * 🔴 目录按<b>保全项主类型</b>登记，而非按原始编码字符串：保全项编码虽由产品 Offering 自由配置，但
 * {@code MaintenanceItemCode.legacyMaintenanceType()} 要求每个编码都能归一到一个主类型（无映射即拒绝建案），
 * 生效侧的四个专用出口判定也一律先归一后比较。目录沿用<b>同一条归一化路径</b>，避免出现「同一编码在
 * 出口判定里是 A 类、在载荷目录里是 B 类」的双口径。
 * </p>
 */
@Getter
public enum MaintenanceEffectPayloadKind implements BaseEnum {

    /** 字段/状态型：把结构化字段变更或合同状态动作递给保单域（多数保全项属此类）。 */
    FIELD(1, "FIELD", "字段与状态动作"),

    /** 金额型：把一笔收付差额（金额 + 方向 + 币种）作为生效载荷递给保单域。 */
    AMOUNT(2, "AMOUNT", "金额事实"),

    /**
     * 专用出口型：生效动作不走保单域字段回写，而由各自的跨域出口承接
     * （账户转换 → investment、贷款/还款 → policy 贷款账户、减额缴清 → policy 保额与缴费方式）。
     * <p>
     * 此类保全项的出口前置条件在生效上下文校验中<b>各自独立成段</b>（如不得与其它项混案、仅支持立即生效），
     * 故在闸口处只需承认「载荷另有出处」，不再重复校验。
     * </p>
     */
    DEDICATED_EXIT(3, "DEDICATED_EXIT", "专用跨域出口"),

    /**
     * 服务受限型：把一条服务受限标记的<b>施加或解除</b>作为生效载荷递给保单域（挂失 / 解除挂失）。
     * <p>
     * 与另三种载荷并列的第四种：挂失既不写字段目录、也无收付差额、更不改合同状态（挂失保单仍是
     * {@code EFFECTIVE}），变更的实质就是这条受限标记本身。缺此种类则挂失案件在保单域侧退化为
     * 「空交易」而被拒绝。
     * </p>
     * <p>
     * 🔴 <b>与 {@link #FIELD} 的关键分野</b>：受限标记落的是保单上与 {@code PolicyStatus} <b>正交</b>的
     * 另一条维度，<b>不产生字段明细</b>——故此类案件的批单没有字段清单，也不得与字段/金额/状态载荷混案
     * （混装即语义不明，两侧闸口均拒绝）。
     * </p>
     */
    SERVICE_RESTRICTION(4, "SERVICE_RESTRICTION", "服务受限事实");

    private final Integer enumCode;
    private final String code;
    private final String name;

    MaintenanceEffectPayloadKind(Integer enumCode, String code, String name) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
    }
}
