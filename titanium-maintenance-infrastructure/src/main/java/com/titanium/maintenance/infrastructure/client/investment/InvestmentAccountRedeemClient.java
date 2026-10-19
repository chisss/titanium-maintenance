package com.titanium.maintenance.infrastructure.client.investment;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.api.response.InvestmentAccountResponse;
import com.titanium.metadata.response.ApiResponse;

/**
 * 投资域 Feign 客户端（部分领取赎回，防腐适配，路径与响应类型严格对齐 investment 域真实契约）
 * <p>
 * 路径 {@code /api/v1/investment-accounts} 与 investment 域 {@code InvestmentAccountApi} 保持一致。响应类型复用
 * {@code titanium-investment-api} 模块公开的契约出参 {@code InvestmentAccountResponse}（PKG-02 起不再暴露读模型
 * {@code InvestmentAccountQueryResult}），避免本域自维护镜像 record 导致字段漂移；赎回请求体复用
 * {@link InvestmentAccountApi.RedeemByAmountRequest}，与对端契约同源。
 * </p>
 * <p>
 * 两个端点的分工：{@code getAccountsByPolicy} 完成「保单 → 投资账户」的解析（保全案件只持有保单标识），
 * {@code redeemByAmount} 执行按金额赎回。二者必须成对使用，中间不得插入其它账户状态假设。
 * </p>
 * <p>
 * 🔴 与 {@code InvestmentAccountSwitchClient} 是两个独立客户端（同 name 不同 {@code contextId}）：二者虽共用
 * 同一资源路径，但分属两条语义不同的出口（转换=价值守恒；赎回=资金动作），合成一个客户端会让「哪条出口用了
 * 哪个端点」不再由类型系统约束——错误地跨用端点无法在编译期发现。
 * </p>
 */
@FeignClient(name = "titanium-investment-service", contextId = "maintenanceInvestmentAccountRedeemClient",
        path = "/api/v1/investment-accounts")
public interface InvestmentAccountRedeemClient {

    /**
     * 按关联保单ID查询投资账户列表
     *
     * @param policyId 保单ID
     * @return 账户列表（无账户时为空列表，非 null）
     */
    @GetMapping("/policy/{policyId}")
    ApiResponse<List<InvestmentAccountResponse>> getAccountsByPolicy(@PathVariable String policyId);

    /**
     * 按金额赎回（部分领取）——单位数由账户按当日净值折算，本域只递金额
     *
     * @param accountId 投资账户ID
     * @param request   赎回请求（实领金额、币种、原因、操作人）
     * @return 赎回后账户读模型结果
     */
    @PostMapping("/{accountId}/redemptions-by-amount")
    ApiResponse<InvestmentAccountResponse> redeemByAmount(@PathVariable String accountId,
            @RequestBody InvestmentAccountApi.RedeemByAmountRequest request);
}
