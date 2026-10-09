package com.titanium.maintenance.valueobject.item;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.enums.config.MaintenanceEffectPayloadKind;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/** 保全项稳定编码及旧案件主类型映射。 */
public record MaintenanceItemCode(String value) {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final Map<String, MaintenanceType> LEGACY_TYPE_BY_ITEM_CODE = legacyTypeMappings();

    /**
     * 金额型保全项目录：生效载荷是一笔收付差额，无字段目录入口、无合同状态动作。
     * <p>
     * 四类均已在 {@code Maintenance.requiresPremiumSettlement()} 名单内——即它们的差额在生效前
     * 已由 Product 差额与 Billing 入账登记为<b>既有事实</b>，生效环节只负责把这笔差额递给保单域，
     * 不重新计价、也不接受调用方自报金额。
     * </p>
     */
    private static final Set<MaintenanceType> AMOUNT_TYPES = Set.of(
            MaintenanceType.ADDITIONAL_PAYMENT,
            MaintenanceType.REDUCTION_PAYMENT,
            MaintenanceType.TOP_UP,
            MaintenanceType.PARTIAL_WITHDRAWAL);

    /**
     * 专用跨域出口目录：生效动作不出本域字段回写，而由各自的跨域出口承接。
     * <p>
     * 账户转换 → investment 账户；贷款放款/还款 → policy 贷款账户；减额缴清 → policy 保额与缴费方式；
     * 保单补发 → document 单证请求（渲染寄送由 G13 承接）。五者的出口前置条件各自独立成段（不得与其它项
     * 混案、仅支持立即生效等），本目录只声明「载荷另有出处」，不重复表达那些约束。
     * </p>
     */
    private static final Set<MaintenanceType> DEDICATED_EXIT_TYPES = Set.of(
            MaintenanceType.FUND_SWITCH,
            MaintenanceType.POLICY_LOAN,
            MaintenanceType.LOAN_REPAYMENT,
            MaintenanceType.REDUCED_PAID_UP,
            MaintenanceType.POLICY_REISSUE);

    /**
     * 服务受限型保全项目录：生效载荷是保单上一条服务受限标记的施加或解除（挂失 / 解除挂失）。
     * <p>
     * 两类均无字段目录入口、无收付差额、不改合同状态（挂失保单仍是 {@code EFFECTIVE}），
     * 生效环节只负责把这条受限事实递给保单域，不落字段明细、也不产生批单字段清单。
     * </p>
     * <p>
     * 🔴 施加与解除是<b>对称的两项</b>，不可合并成一项带参数的动作：两者的受理前置条件、准入校验与
     * 批单语义都不同（对未受限者解除、对已受限者重复施加都应被拒），且合并会把「挂失中」这一业务事实
     * 藏进案件参数里，外部无从按其检索。
     * </p>
     */
    private static final Set<MaintenanceType> SERVICE_RESTRICTION_TYPES = Set.of(
            MaintenanceType.LOSS_REPORT,
            MaintenanceType.LOSS_REPORT_RELEASE);

    private static final Map<MaintenanceType, MaintenanceEffectPayloadKind> EFFECT_PAYLOAD_KINDS =
            effectPayloadKinds();

    public MaintenanceItemCode {
        if (value == null || !CODE_PATTERN.matcher(value.trim()).matches()) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemCode", "value", "保全项编码必须使用大写字母、数字和下划线");
        }
        value = value.trim();
    }

    public static MaintenanceItemCode of(String value) {
        return new MaintenanceItemCode(value);
    }

    /** 将首个保全项映射到兼容旧事件和下游契约的主保全类型。 */
    public MaintenanceType legacyMaintenanceType() {
        MaintenanceType type = LEGACY_TYPE_BY_ITEM_CODE.get(value);
        if (type == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemCode", "value", "保全项缺少旧主类型映射: " + value);
        }
        return type;
    }

    /**
     * 该保全项的<b>生效载荷种类</b>——生效环节据以判断「这次合同变更把什么递给保单域」。
     * <p>
     * 目录由 {@link #EFFECT_PAYLOAD_KINDS} 单一登记，未登记者一律按
     * {@link MaintenanceEffectPayloadKind#FIELD}（字段/状态动作）处理，保持既有行为不变。
     * </p>
     * <p>
     * 🔴 为何是目录而非闸口里的 {@code ||} 串：生效闸口原先把「不走字段回写的出口」逐个串成
     * {@code || switchCase || loanCase || ...}，每新增一类出口就再串一段——漏串一次即静默拦死整类业务，
     * 且判断散落在多处（闸口两道 + 请求载荷判据一道）。把「载荷是什么」收敛为<b>按保全项登记的数据</b>后，
     * 新增一类只需在本目录加一项，三处判据共享同一事实。
     * </p>
     */
    public MaintenanceEffectPayloadKind effectPayloadKind() {
        return EFFECT_PAYLOAD_KINDS.getOrDefault(legacyMaintenanceType(), MaintenanceEffectPayloadKind.FIELD);
    }

    /**
     * 是否为<b>金额型</b>保全项：生效载荷是一笔收付差额（金额 + 方向 + 币种），而非字段变更或合同状态动作。
     * <p>
     * 金额型保全项既无字段目录入口也无状态动作，须由生效环节把差额事实作为<b>并列载荷</b>递给保单域；
     * 差额本身来自案件已登记的资金事实（Product 差额 + Billing 入账），不由调用方自报。
     * </p>
     */
    public boolean amountEffect() {
        return effectPayloadKind() == MaintenanceEffectPayloadKind.AMOUNT;
    }

    /**
     * 是否为<b>服务受限型</b>保全项：生效载荷是保单上一条受限标记的施加或解除（挂失 / 解除挂失）。
     */
    public boolean serviceRestrictionEffect() {
        return effectPayloadKind() == MaintenanceEffectPayloadKind.SERVICE_RESTRICTION;
    }

    /** 目录中登记为金额型的全部主类型（单一来源，新增金额项只改 {@link #AMOUNT_TYPES}）。 */
    public static Set<MaintenanceType> amountTypes() {
        return AMOUNT_TYPES;
    }

    /** 目录中登记为专用跨域出口的全部主类型（单一来源，新增出口只改 {@link #DEDICATED_EXIT_TYPES}）。 */
    public static Set<MaintenanceType> dedicatedExitTypes() {
        return DEDICATED_EXIT_TYPES;
    }

    /**
     * 目录中登记为服务受限型的全部主类型（单一来源，新增受限项只改 {@link #SERVICE_RESTRICTION_TYPES}）。
     */
    public static Set<MaintenanceType> serviceRestrictionTypes() {
        return SERVICE_RESTRICTION_TYPES;
    }

    private static Map<String, MaintenanceType> legacyTypeMappings() {
        Map<String, MaintenanceType> mappings = new HashMap<>();
        for (MaintenanceType type : MaintenanceType.values()) {
            mappings.put(type.getCode(), type);
        }
        mappings.put("SURRENDER", MaintenanceType.POLICY_TERMINATION);
        mappings.put("POLICY_SURRENDER", MaintenanceType.POLICY_TERMINATION);
        return Map.copyOf(mappings);
    }

    private static Map<MaintenanceType, MaintenanceEffectPayloadKind> effectPayloadKinds() {
        Map<MaintenanceType, MaintenanceEffectPayloadKind> kinds = new EnumMap<>(MaintenanceType.class);
        AMOUNT_TYPES.forEach(type -> kinds.put(type, MaintenanceEffectPayloadKind.AMOUNT));
        DEDICATED_EXIT_TYPES.forEach(type -> kinds.put(type, MaintenanceEffectPayloadKind.DEDICATED_EXIT));
        SERVICE_RESTRICTION_TYPES.forEach(
                type -> kinds.put(type, MaintenanceEffectPayloadKind.SERVICE_RESTRICTION));
        return Map.copyOf(kinds);
    }
}
