package com.titanium.maintenance.infrastructure.adapter.investment;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.api.response.InvestmentAccountResponse;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.investment.InvestmentAccountRedeemClient;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投资账户部分领取赎回适配器（六边形架构 driven adapter）
 * <p>
 * {@link InvestmentAccountRedeemPort} 的基础设施实现，调用投资域 {@link InvestmentAccountRedeemClient}（Feign）。
 * 保全案件只持有保单标识，故本适配器承担「保单 → 投资账户」的解析：先按保单查账户列表，再对唯一命中的账户执行
 * 按金额赎回。两步都必须成功，中间不得插入对账户状态的本地假设。
 * </p>
 * <p>
 * 🔴 <b>「不存在」与「失败」严格区分</b>（与 {@code InvestmentAccountSwitchAdapter}、{@code CustomerServiceAdapter}
 * 同规，D13）：账户查询正常应答的空列表才是「保单名下无投资账户」，此时<b>返回 null 原样上交</b>，由调用方决定
 * 处置；对端返回非成功码、响应缺数据、连接失败、超时、契约反序列化失败一律抛
 * {@link MaintenanceRemoteCallException}（失败关闭），禁止把服务故障伪装成「账户不存在」。
 * </p>
 * <p>
 * 🔴 <b>多账户歧义不静默择一</b>：同一保单可派生多个形态账户（投连/万能/分红），赎回目标是业务事实而非技术
 * 细节，无从推断时以 {@code MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS} 显式失败，交人工明确账户后再执行，
 * 避免「钱从另一个账户里扣了」这类事后无从对账的错账。
 * </p>
 * <p>
 * 🔴 <b>本适配器不校验金额是否超过账户价值</b>：可领额度与退费因子归 G04/G05（本域不重复表达其规则），
 * 单位不足由投资域账户侧以业务错误码拒绝，本适配器只负责把该拒绝如实上报为远程调用失败（fail-closed）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentAccountRedeemAdapter implements InvestmentAccountRedeemPort {

    /** 缺省币种（与投资域值对象组装约定一致） */
    private static final String             DEFAULT_CURRENCY = "CNY";

    private final       InvestmentAccountRedeemClient investmentAccountRedeemClient;

    @Override
    public InvestmentRedeemFact redeemByPolicy(InvestmentRedeemRequest request) {
        try {
            List<InvestmentAccountResponse> accounts = requireAccounts(request);
            if (accounts.isEmpty()) {
                log.info("保单名下无可用投资账户，部分领取的赎回无处可施, policyId={}, maintenanceId={}",
                        request.policyId(), request.maintenanceId());
                return null;
            }
            if (accounts.size() > 1) {
                throw new MaintenanceRemoteCallException(
                        "保单名下存在多个投资账户，部分领取需明确账户标识, policyId=" + request.policyId(),
                        MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS);
            }
            InvestmentAccountResponse redeemed = requireRedeemed(accounts.getFirst().accountId(), request);
            return new InvestmentRedeemFact(redeemed.accountId(), redeemed.unitPrice(),
                    redeemed.totalUnits(), redeemed.accountValue(), redeemed.currency());
        } catch (MaintenanceRemoteCallException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("投资域远程调用失败(服务不可达), action=部分领取赎回, policyId={}, 原因={}", request.policyId(),
                    exception.getMessage(), exception);
            throw new MaintenanceRemoteCallException(
                    "投资域部分领取赎回失败(服务不可达), policyId=" + request.policyId(),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR, exception);
        }
    }

    /** 按保单解析账户列表；对端未正常应答一律失败关闭，空列表才是「无账户」 */
    private List<InvestmentAccountResponse> requireAccounts(InvestmentRedeemRequest request) {
        ApiResponse<List<InvestmentAccountResponse>> response = investmentAccountRedeemClient
                .getAccountsByPolicy(request.policyId());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new MaintenanceRemoteCallException(
                    "投资域账户查询未返回有效结果, policyId=" + request.policyId() + ", code="
                            + (response == null ? "null" : response.getCode()),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR);
        }
        return response.getData();
    }

    /** 执行赎回并校验回执完整性；赎回后账户事实必须齐备，否则视为契约无效 */
    private InvestmentAccountResponse requireRedeemed(String accountId, InvestmentRedeemRequest request) {
        ApiResponse<InvestmentAccountResponse> response = investmentAccountRedeemClient
                .redeemByAmount(accountId, toRedeemRequest(request));
        InvestmentAccountResponse redeemed = response == null ? null : response.getData();
        if (response == null || !response.isSuccess() || redeemed == null
                || redeemed.accountId() == null || redeemed.unitPrice() == null
                || redeemed.totalUnits() == null || redeemed.accountValue() == null) {
            throw new MaintenanceRemoteCallException(
                    "投资域部分领取赎回未返回有效回执, policyId=" + request.policyId() + ", accountId=" + accountId
                            + ", code=" + (response == null ? "null" : response.getCode()),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR);
        }
        return redeemed;
    }

    /** 本域请求 → 对端契约请求体（币种缺省 CNY，与投资域值对象组装约定一致；原因由案件号派生） */
    private InvestmentAccountApi.RedeemByAmountRequest toRedeemRequest(InvestmentRedeemRequest request) {
        InvestmentAccountApi.RedeemByAmountRequest body = new InvestmentAccountApi.RedeemByAmountRequest();
        body.setRedeemAmount(request.redeemAmount());
        body.setCurrency(request.currency() != null ? request.currency() : DEFAULT_CURRENCY);
        body.setReason(request.reason());
        body.setOperatorId(request.operatorId());
        return body;
    }
}
