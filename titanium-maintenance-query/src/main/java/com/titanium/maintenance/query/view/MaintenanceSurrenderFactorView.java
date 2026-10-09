package com.titanium.maintenance.query.view;

import java.math.BigDecimal;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 退保退费因子明细读模型（{@code t_maintenance_surrender_factor_view}，一案件多行）。
 *
 * <p>
 * 🔴 <b>为何独立子表而不在 {@link MaintenanceView} 平铺</b>：因子种类随产品类型扩展
 * （分红/万能增红利账户价值、投连增账户价值、未来口径再增项），平铺等于每加一个因子就改主视图宽表；
 * 子表只增行不改列，且「净额 = ΣCREDIT − ΣDEBIT」在子表上可直接加总复算（AC-06/AC-01 恒等式）。
 * </p>
 * <p>
 * 🔴 {@code factorType} / {@code direction} 是 <b>Product 域字符串码的防腐镜像</b>（本域不定义同义枚举，
 * 未知码原样透传由展示与对账方按同一 code 解释，项目 CLAUDE.md §3.4.11 ③）；
 * {@code explainKey} 是文案键而非中文文案，渲染按 {@code Accept-Language} 在边界层完成（§3.4.12 红线 20）。
 * </p>
 * <p>
 * 🔴 <b>最终一致</b>：本表由事件投影维护（见 {@code MaintenanceSurrenderFactorProjectionEventHandler}），
 * 与写模型存在投影延迟，<b>仅供对客展示与对账</b>；<b>禁止</b>回流写侧做任何金额/资格决策
 * （强一致决策走写侧仓储，项目 CLAUDE.md §3.4.9 ⑥）。退费金额的权威仍在 Product 域。
 * </p>
 */
@Entity
@Table(name = "t_maintenance_surrender_factor_view")
@Getter
@Setter
public class MaintenanceSurrenderFactorView extends BaseView {

    /** 因子行稳定派生 ID：tenantId + 案件 + 序号 + 因子类型 的 SHA-256（重投影同值覆盖） */
    @Id
    @Column(name = "factor_view_id", nullable = false, length = 64)
    private String factorViewId;

    @Column(name = "maintenance_id", nullable = false, length = 64)
    private String maintenanceId;

    /** 因子在明细中的顺序（对端下发顺序即展示顺序，重投影后保持） */
    @Column(name = "factor_order", nullable = false)
    private Integer factorOrder;

    /** 因子类型码（对端 Product 的 {@code SurrenderFactorType} 名称，本域不解析其值域） */
    @Column(name = "factor_type", nullable = false, length = 64)
    private String factorType;

    /** 金额方向码：CREDIT / DEBIT / NONE（对端 {@code ChargeDirection} 名称） */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    /** 金额绝对值（正负号由 direction 表达，本列恒非负） */
    @Column(name = "amount", precision = 20, scale = 8)
    private BigDecimal amount;

    /** 文案键（非中文文案，按 locale 渲染） */
    @Column(name = "explain_key", length = 191)
    private String explainKey;
}
