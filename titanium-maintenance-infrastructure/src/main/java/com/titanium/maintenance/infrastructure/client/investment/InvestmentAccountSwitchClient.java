package com.titanium.maintenance.infrastructure.client.investment;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.query.result.InvestmentAccountQueryResult;
import com.titanium.metadata.response.ApiResponse;

/**
 * 投资域 Feign 客户端（防腐适配，路径与响应类型严格对齐 investment 域真实契约）
 * <p>
 * 路径 {@code /api/v1/investment-accounts} 与 investment 域 {@code InvestmentAccountApi} 保持一致。响应类型复用
 * {@code titanium-investment-api} 模块公开的 {@code InvestmentAccountQueryResult}，避免本域自维护镜像 record
 * 导致字段漂移；转换请求体复用 {@link InvestmentAccountApi.SwitchUnitsRequest}，与对端契约同源。
 * </p>
 * <p>
 * 两个端点的分工：{@code getAccountsByPolicy} 完成「保单 → 投资账户」的解析（保全案件只持有保单标识），
 * {@code switchUnits} 执行转换。二者必须成对使用，中间不得插入其它账户状态假设。
 * </p>
 */
@FeignClient(name = "titanium-investment", contextId = "maintenanceInvestmentAccountSwitchClient",
        path = "/api/v1/investment-accounts")
public interface InvestmentAccountSwitchClient {

    /**
     * 按关联保单ID查询投资账户列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 账户列表（无账户时为空列表，非 null）
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(@PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 账户转换（基金转换）
     *
     * @param accountId 投资账户ID
     * @param request   转换请求（转出单位、目标净值与标的）
     * @param tenantId  租户ID
     * @return 转换后账户读模型结果
     */
    @PostMapping("/{accountId}/switches")
    ApiResponse<InvestmentAccountQueryResult> switchUnits(@PathVariable("accountId") String accountId,
            @RequestBody InvestmentAccountApi.SwitchUnitsRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId);
}
