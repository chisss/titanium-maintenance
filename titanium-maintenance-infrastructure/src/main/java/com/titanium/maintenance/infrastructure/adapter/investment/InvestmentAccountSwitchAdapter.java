package com.titanium.maintenance.infrastructure.adapter.investment;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.query.result.InvestmentAccountQueryResult;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.investment.InvestmentAccountSwitchClient;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投资账户转换适配器（六边形架构 driven adapter）
 * <p>
 * {@link InvestmentAccountSwitchPort} 的基础设施实现，调用投资域 {@link InvestmentAccountSwitchClient}（Feign）。
 * 保全案件只持有保单标识，故本适配器承担「保单 → 投资账户」的解析：先按保单查账户列表，再对唯一命中的账户执行
 * 转换。两步都必须成功，中间不得插入对账户状态的本地假设。
 * </p>
 * <p>
 * 🔴 <b>「不存在」与「失败」严格区分</b>（与 {@code CustomerServiceAdapter} 同规，D13）：
 * 账户查询正常应答的空列表才是「保单名下无投资账户」，此时<b>返回 null 原样上交</b>，由调用方决定处置；
 * 对端返回非成功码、响应缺数据、连接失败、超时、契约反序列化失败一律抛 {@link MaintenanceRemoteCallException}
 * （失败关闭），禁止把服务故障伪装成「账户不存在」。
 * </p>
 * <p>
 * 🔴 <b>多账户歧义不静默择一</b>：同一保单可派生多个形态账户（投连/万能/分红），转换目标是业务事实而非技术
 * 细节，无从推断时以 {@code MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS} 显式失败，交人工明确账户后再执行，
 * 避免「转换落到了另一个账户」这类事后无从对账的错账。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentAccountSwitchAdapter implements InvestmentAccountSwitchPort {

    /** 缺省币种（与投资域值对象组装约定一致） */
    private static final String                DEFAULT_CURRENCY = "CNY";

    private final        InvestmentAccountSwitchClient investmentAccountSwitchClient;

    @Override
    public InvestmentSwitchFact switchByPolicy(InvestmentSwitchRequest request) {
        try {
            List<InvestmentAccountQueryResult> accounts = requireAccounts(request);
            if (accounts.isEmpty()) {
                log.info("保单名下无可用投资账户，账户转换无处可施, policyId={}", request.policyId());
                return null;
            }
            if (accounts.size() > 1) {
                throw new MaintenanceRemoteCallException(
                        "保单名下存在多个投资账户，账户转换需明确账户标识, policyId=" + request.policyId(),
                        MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS);
            }
            InvestmentAccountQueryResult switched = requireSwitched(accounts.getFirst().getAccountId(), request);
            return new InvestmentSwitchFact(switched.getAccountId(), switched.getUnitPrice(),
                    switched.getTotalUnits(), switched.getAccountValue(), switched.getCurrency());
        } catch (MaintenanceRemoteCallException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("投资域远程调用失败(服务不可达), action=账户转换, policyId={}, 原因={}", request.policyId(),
                    exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "投资域账户转换失败(服务不可达), policyId=" + request.policyId(),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR, exception);
        }
    }

    /** 按保单解析账户列表；对端未正常应答一律失败关闭，空列表才是「无账户」 */
    private List<InvestmentAccountQueryResult> requireAccounts(InvestmentSwitchRequest request) {
        ApiResponse<List<InvestmentAccountQueryResult>> response = investmentAccountSwitchClient
                .getAccountsByPolicy(request.policyId(), request.tenantId());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new MaintenanceRemoteCallException(
                    "投资域账户查询未返回有效结果, policyId=" + request.policyId() + ", code="
                            + (response == null ? "null" : response.getCode()),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR);
        }
        return response.getData();
    }

    /** 执行转换并校验回执完整性；转换后账户事实必须齐备，否则视为契约无效 */
    private InvestmentAccountQueryResult requireSwitched(String accountId, InvestmentSwitchRequest request) {
        ApiResponse<InvestmentAccountQueryResult> response = investmentAccountSwitchClient
                .switchUnits(accountId, toSwitchRequest(request), request.tenantId());
        InvestmentAccountQueryResult switched = response == null ? null : response.getData();
        if (response == null || !response.isSuccess() || switched == null
                || switched.getAccountId() == null || switched.getUnitPrice() == null
                || switched.getTotalUnits() == null || switched.getAccountValue() == null) {
            throw new MaintenanceRemoteCallException(
                    "投资域账户转换未返回有效回执, policyId=" + request.policyId() + ", accountId=" + accountId
                            + ", code=" + (response == null ? "null" : response.getCode()),
                    MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR);
        }
        return switched;
    }

    /** 本域请求 → 对端契约请求体（币种缺省 CNY，与投资域值对象组装约定一致） */
    private InvestmentAccountApi.SwitchUnitsRequest toSwitchRequest(InvestmentSwitchRequest request) {
        InvestmentAccountApi.SwitchUnitsRequest body = new InvestmentAccountApi.SwitchUnitsRequest();
        body.setSwitchOutUnits(request.switchOutUnits());
        body.setTargetUnitPrice(request.targetUnitPrice());
        body.setTargetCurrency(request.targetCurrency() != null ? request.targetCurrency() : DEFAULT_CURRENCY);
        body.setTargetFund(request.targetFund());
        body.setOperatorId(request.operatorId());
        return body;
    }
}
