package com.titanium.maintenance.infrastructure.adapter.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.query.result.InvestmentAccountQueryResult;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.investment.InvestmentAccountRedeemClient;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort.InvestmentRedeemFact;
import com.titanium.maintenance.port.investment.InvestmentAccountRedeemPort.InvestmentRedeemRequest;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

/**
 * 部分领取赎回适配器测试（G14/g14-06）
 * <p>
 * 本类钉死三条判据，任一条失守即产生一类错账：
 * </p>
 * <ol>
 * <li><b>「不存在」与「失败」严格区分</b>（D13）——空列表是业务事实（返 null 上交），对端失败码、响应缺数据、
 * 连接失败都是技术故障（fail-closed 抛出）。二者混淆会让一次服务抖动被读成「客户没有账户」，从而跳过赎回；</li>
 * <li><b>多账户不静默择一</b>——钱从哪个账户里扣是业务事实，无从推断时显式失败；</li>
 * <li><b>递交的是金额而非单位数</b>，且币种缺省、原因由案件号派生——单位数折算入口只在投资域账户侧，
 * 本域先折算会因两步之间的净值变动让客户实领金额静默不等。</li>
 * </ol>
 *
 * @author wei.sun
 * @since 2026/10/12
 */
class InvestmentAccountRedeemAdapterTest {

    @Test
    @DisplayName("保单名下无投资账户：返回 null 原样上交，不升级为远程故障")
    void shouldReturnNullWhenPolicyHasNoAccount() {
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(
                client(List.of(), null, null));

        assertNull(adapter.redeemByPolicy(request(null)));
    }

    @Test
    @DisplayName("唯一账户：赎回事实逐字段取对端应答，请求体递金额而非单位数、币种缺省、原因由案件号派生")
    void shouldReturnRedeemedFactAndSubmitAmountNotUnits() {
        InvestmentAccountQueryResult redeemed = account("account-1", "1.25000000", "200.00000000",
                "250.00000000", "CNY");
        RecordingClient recording = new RecordingClient(
                List.of(account("account-1", null, null, null, null)), redeemed);
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(recording);

        InvestmentRedeemFact fact = adapter.redeemByPolicy(request(null));

        assertNotNull(fact);
        assertEquals("account-1", fact.accountId());
        assertEquals(new BigDecimal("1.25000000"), fact.unitPrice());
        assertEquals(new BigDecimal("200.00000000"), fact.totalUnits());
        assertEquals(new BigDecimal("250.00000000"), fact.accountValue());
        assertEquals("CNY", fact.currency());
        assertEquals("policy-1", recording.queryPolicyId);
        assertEquals("account-1", recording.redeemAccountId);
        assertEquals(0, recording.redeemBody.getRedeemAmount().compareTo(new BigDecimal("1000.00")));
        assertEquals("CNY", recording.redeemBody.getCurrency(), "币种缺省应为 CNY");
        assertEquals("MAINTENANCE_PARTIAL_WITHDRAWAL:maintenance-1", recording.redeemBody.getReason(),
                "原因由案件号派生：重复赎回须可按案在投资域事件流中检索");
        assertEquals("operator-1", recording.redeemBody.getOperatorId());
    }

    @Test
    @DisplayName("显式币种优先于缺省币种")
    void shouldKeepExplicitCurrency() {
        RecordingClient recording = new RecordingClient(
                List.of(account("account-1", null, null, null, null)),
                account("account-1", "1.00000000", "1.00000000", "1.00000000", "USD"));
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(recording);

        adapter.redeemByPolicy(request("USD"));

        assertEquals("USD", recording.redeemBody.getCurrency());
    }

    @Test
    @DisplayName("同保单多账户：显式失败而非静默择一（钱从哪个账户里扣是业务事实）")
    void shouldRejectAmbiguousAccounts() {
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(client(
                List.of(account("account-1", null, null, null, null),
                        account("account-2", null, null, null, null)),
                null, null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.redeemByPolicy(request(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS.getCode(),
                error.getErrorCodeValue());
    }

    @Test
    @DisplayName("账户查询服务不可达：抛远程调用异常，不得伪装成『账户不存在』")
    void shouldThrowRemoteErrorWhenQueryUnreachable() {
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(new InvestmentAccountRedeemClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(String policyId) {
                throw new RuntimeException(new ConnectException("Connection refused: titanium-investment"));
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> redeemByAmount(String accountId,
                    InvestmentAccountApi.RedeemByAmountRequest request) {
                throw new IllegalStateException("查询已失败，不得再发起赎回");
            }
        });

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.redeemByPolicy(request(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                error.getErrorCodeValue());
        assertNotNull(error.getCause(), "根因须保留，否则只剩「失败了」而无从定位");
    }

    @Test
    @DisplayName("账户查询返回失败码或空数据：失败关闭，不得当作『无账户』")
    void shouldThrowRemoteErrorWhenQueryResponseInvalid() {
        InvestmentAccountRedeemAdapter failed = new InvestmentAccountRedeemAdapter(client(null, null, null));
        InvestmentAccountRedeemAdapter emptyData = new InvestmentAccountRedeemAdapter(new InvestmentAccountRedeemClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(String policyId) {
                return ApiResponse.success(null);
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> redeemByAmount(String accountId,
                    InvestmentAccountApi.RedeemByAmountRequest request) {
                throw new IllegalStateException("查询未返回有效列表，不得再发起赎回");
            }
        });

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class, () -> failed.redeemByPolicy(request(null)))
                        .getErrorCodeValue());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class, () -> emptyData.redeemByPolicy(request(null)))
                        .getErrorCodeValue());
    }

    @Test
    @DisplayName("赎回被对端拒绝（如单位不足）：抛远程调用异常，不得静默当作已赎回")
    void shouldThrowRemoteErrorWhenRedeemRejected() {
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(client(
                List.of(account("account-1", null, null, null, null)), null, null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.redeemByPolicy(request(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                error.getErrorCodeValue());
    }

    @Test
    @DisplayName("赎回回执字段缺失：视为契约无效，失败关闭")
    void shouldThrowRemoteErrorWhenRedeemReceiptIncomplete() {
        InvestmentAccountRedeemAdapter adapter = new InvestmentAccountRedeemAdapter(client(
                List.of(account("account-1", null, null, null, null)),
                account("account-1", null, "200.00000000", "250.00000000", "CNY"), null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.redeemByPolicy(request(null)));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                error.getErrorCodeValue());
    }

    private InvestmentRedeemRequest request(String currency) {
        return new InvestmentRedeemRequest("tenant-1", "policy-1", "maintenance-1",
                new BigDecimal("1000.00"), currency, "operator-1");
    }

    private InvestmentAccountQueryResult account(String accountId, String unitPrice, String totalUnits,
            String accountValue, String currency) {
        InvestmentAccountQueryResult result = new InvestmentAccountQueryResult();
        result.setAccountId(accountId);
        if (unitPrice != null) {
            result.setUnitPrice(new BigDecimal(unitPrice));
        }
        if (totalUnits != null) {
            result.setTotalUnits(new BigDecimal(totalUnits));
        }
        if (accountValue != null) {
            result.setAccountValue(new BigDecimal(accountValue));
        }
        result.setCurrency(currency);
        return result;
    }

    /** 账户列表应答与赎回应答可分别指定的客户端桩 */
    private InvestmentAccountRedeemClient client(List<InvestmentAccountQueryResult> accounts,
            InvestmentAccountQueryResult redeemed, ApiResponse<InvestmentAccountQueryResult> redeemResponse) {
        return new InvestmentAccountRedeemClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(String policyId) {
                return accounts == null ? null : ApiResponse.success(accounts);
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> redeemByAmount(String accountId,
                    InvestmentAccountApi.RedeemByAmountRequest request) {
                return redeemResponse != null ? redeemResponse
                        : (redeemed == null ? ApiResponse.error(
                                MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR)
                                : ApiResponse.success(redeemed));
            }
        };
    }

    /** 记录实际发出的查询与赎回请求，供断言递交内容 */
    private static final class RecordingClient implements InvestmentAccountRedeemClient {

        private final List<InvestmentAccountQueryResult> accounts;
        private final InvestmentAccountQueryResult       redeemed;

        private String                                        queryPolicyId;
        private String                                        redeemAccountId;
        private InvestmentAccountApi.RedeemByAmountRequest     redeemBody;

        private RecordingClient(List<InvestmentAccountQueryResult> accounts,
                InvestmentAccountQueryResult redeemed) {
            this.accounts = accounts;
            this.redeemed = redeemed;
        }

        @Override
        public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(String policyId) {
            this.queryPolicyId = policyId;
            return ApiResponse.success(accounts);
        }

        @Override
        public ApiResponse<InvestmentAccountQueryResult> redeemByAmount(String accountId,
                InvestmentAccountApi.RedeemByAmountRequest request) {
            this.redeemAccountId = accountId;
            this.redeemBody = request;
            return ApiResponse.success(redeemed);
        }
    }
}
