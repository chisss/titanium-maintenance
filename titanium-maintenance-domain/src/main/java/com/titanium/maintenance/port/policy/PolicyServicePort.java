package com.titanium.maintenance.port.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;

/**
 * 保单服务端口（出口/driven port）
 * <p>
 * 领域/应用侧表达对保单域的能力需求：校验保单存在、获取保单状态快照。 具体的跨微服务调用（Feign）由基础设施层
 * {@code infrastructure.adapter} 的适配器实现， 领域侧不依赖任何远程响应类型（防腐）。
 * </p>
 */
public interface PolicyServicePort {

    /**
     * 校验保单是否存在
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 存在返回 true，否则 false
     */
    boolean policyExists(String policyId, String tenantId);

    /**
     * 获取保单绑定的产品 ID。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 产品ID；保单不存在或远程事实无效时返回 null
     */
    String getPolicyProductId(String policyId, String tenantId);

    /** 获取退保计算所需的权威保单财务日期快照。 */
    PolicyFinancialSnapshot getPolicyFinancialSnapshot(String policyId, String tenantId);

    /**
     * 获取保单状态快照
     *
     * @param policyId 保单ID
     * @return 保单状态快照
     */
    PolicyStatusSnapshot getPolicyStatus(String policyId);

    /**
     * 保单状态快照（领域侧类型化视图，屏蔽远程响应细节）。
     * <p>
     * 🔴 <b>保留完整状态码，不再折叠成布尔</b>：状态准入需区分 7 个保单状态（复效仅 {@code LAPSED}、
     * 恢复仅 {@code SUSPENDED}、终止为 {@code EFFECTIVE/SUSPENDED/LAPSED} 三态），原先折叠出的
     * {@code active}/{@code reinstatable} 两个布尔<b>无法表达</b>这些取值——这正是旧版准入 switch
     * 只能显式覆盖 8/23 类、其余落 {@code default} 放行的结构性原因。判定统一委托
     * {@code MaintenanceAdmissionMatrix}。
     * </p>
     *
     * @param status 保单状态（读模型词表 {@link PolicyStatus}）；保单域正常应答但状态不可识别时为
     *     {@code null}，由准入矩阵按失败关闭拒绝
     */
    record PolicyStatusSnapshot(PolicyStatus status) {
    }

    record PolicyFinancialSnapshot(
            String productId,
            String issuanceBizNo,
            LocalDate effectiveDate,
            BigDecimal premium,
            String currency) {
    }
}
