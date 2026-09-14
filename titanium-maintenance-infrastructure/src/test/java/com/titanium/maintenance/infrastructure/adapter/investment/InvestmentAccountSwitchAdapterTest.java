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
import com.titanium.maintenance.infrastructure.client.investment.InvestmentAccountSwitchClient;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort.InvestmentSwitchFact;
import com.titanium.maintenance.port.investment.InvestmentAccountSwitchPort.InvestmentSwitchRequest;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

class InvestmentAccountSwitchAdapterTest {

    @Test
    @DisplayName("保单名下无投资账户：返回 null 原样上交，不升级为远程故障")
    void shouldReturnNullWhenPolicyHasNoAccount() {
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(
                client(List.of(), null, null));

        assertNull(adapter.switchByPolicy(request()));
    }

    @Test
    @DisplayName("唯一账户：转换事实逐字段取对端应答，并透传租户与缺省币种")
    void shouldReturnSwitchedFactAndPropagateTenantAndDefaultCurrency() {
        InvestmentAccountQueryResult account = account("account-1", "account-1", "1.25000000", "800.00000000",
                "1000.00000000", "CNY");
        RecordingClient recording = new RecordingClient(List.of(account("account-1", null, null, null, null, null)),
                account);
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(recording);

        InvestmentSwitchFact fact = adapter.switchByPolicy(request());

        assertEquals("account-1", fact.accountId());
        assertEquals(new BigDecimal("1.25000000"), fact.unitPrice());
        assertEquals(new BigDecimal("800.00000000"), fact.totalUnits());
        assertEquals(new BigDecimal("1000.00000000"), fact.accountValue());
        assertEquals("CNY", fact.currency());
        assertEquals("tenant-1", recording.queryTenantId);
        assertEquals("policy-1", recording.queryPolicyId);
        assertEquals("tenant-1", recording.switchTenantId);
        assertEquals("account-1", recording.switchAccountId);
        assertEquals("CNY", recording.switchBody.getTargetCurrency(), "币种缺省应为 CNY");
        assertEquals("FUND-B", recording.switchBody.getTargetFund());
    }

    @Test
    @DisplayName("显式币种优先于缺省币种")
    void shouldKeepExplicitCurrency() {
        RecordingClient recording = new RecordingClient(
                List.of(account("account-1", null, null, null, null, null)),
                account("account-1", "account-1", "1.00000000", "1.00000000", "1.00000000", "USD"));
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(recording);

        adapter.switchByPolicy(objectRequest("USD"));

        assertEquals("USD", recording.switchBody.getTargetCurrency());
    }

    @Test
    @DisplayName("同保单多账户：显式失败而非静默择一")
    void shouldRejectAmbiguousAccounts() {
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(client(
                List.of(account("account-1", null, null, null, null, null),
                        account("account-2", null, null, null, null, null)),
                null, null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.switchByPolicy(request()));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_ACCOUNT_AMBIGUOUS.getCode(), error.getErrorCode());
    }

    @Test
    @DisplayName("账户查询服务不可达：抛远程调用异常，不得伪装成『账户不存在』")
    void shouldThrowRemoteErrorWhenQueryUnreachable() {
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(new InvestmentAccountSwitchClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(
                    String policyId, String tenantId) {
                throw new RuntimeException(new ConnectException("Connection refused: titanium-investment"));
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> switchUnits(
                    String accountId, InvestmentAccountApi.SwitchUnitsRequest request, String tenantId) {
                throw new IllegalStateException("查询已失败，不得再发起转换");
            }
        });

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.switchByPolicy(request()));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(), error.getErrorCode());
    }

    @Test
    @DisplayName("账户查询返回失败码或空数据：失败关闭，不得当作『无账户』")
    void shouldThrowRemoteErrorWhenQueryResponseInvalid() {
        InvestmentAccountSwitchAdapter failed = new InvestmentAccountSwitchAdapter(
                client(null, null, null));
        InvestmentAccountSwitchAdapter emptyData = new InvestmentAccountSwitchAdapter(new InvestmentAccountSwitchClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(
                    String policyId, String tenantId) {
                return ApiResponse.success(null);
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> switchUnits(
                    String accountId, InvestmentAccountApi.SwitchUnitsRequest request, String tenantId) {
                throw new IllegalStateException("查询未返回有效列表，不得再发起转换");
            }
        });

        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class, () -> failed.switchByPolicy(request()))
                        .getErrorCode());
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(),
                assertThrows(MaintenanceRemoteCallException.class, () -> emptyData.switchByPolicy(request()))
                        .getErrorCode());
    }

    @Test
    @DisplayName("转换应答失败码：抛远程调用异常")
    void shouldThrowRemoteErrorWhenSwitchRejected() {
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(client(
                List.of(account("account-1", null, null, null, null, null)), null, null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.switchByPolicy(request()));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(), error.getErrorCode());
    }

    @Test
    @DisplayName("转换回执字段缺失：视为契约无效，失败关闭")
    void shouldThrowRemoteErrorWhenSwitchReceiptIncomplete() {
        InvestmentAccountSwitchAdapter adapter = new InvestmentAccountSwitchAdapter(client(
                List.of(account("account-1", null, null, null, null, null)),
                account("account-1", "account-1", null, "1.00000000", "1.00000000", "CNY"), null));

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.switchByPolicy(request()));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR.getCode(), error.getErrorCode());
    }

    private InvestmentSwitchRequest request() {
        return objectRequest(null);
    }

    private InvestmentSwitchRequest objectRequest(String targetCurrency) {
        return new InvestmentSwitchRequest("tenant-1", "policy-1", new BigDecimal("100.00000000"),
                new BigDecimal("1.25000000"), targetCurrency, "FUND-B", "operator-1");
    }

    private InvestmentAccountQueryResult account(String accountId, String receiptAccountId, String unitPrice,
            String totalUnits, String accountValue, String currency) {
        InvestmentAccountQueryResult result = new InvestmentAccountQueryResult();
        result.setAccountId(receiptAccountId == null ? accountId : receiptAccountId);
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

    /** 账户列表应答与转换应答可分别指定的客户端桩 */
    private InvestmentAccountSwitchClient client(List<InvestmentAccountQueryResult> accounts,
            InvestmentAccountQueryResult switched, ApiResponse<InvestmentAccountQueryResult> switchResponse) {
        return new InvestmentAccountSwitchClient() {
            @Override
            public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(
                    String policyId, String tenantId) {
                return accounts == null ? null : ApiResponse.success(accounts);
            }

            @Override
            public ApiResponse<InvestmentAccountQueryResult> switchUnits(
                    String accountId, InvestmentAccountApi.SwitchUnitsRequest request, String tenantId) {
                return switchResponse != null ? switchResponse
                        : (switched == null ? ApiResponse.error(
                                MaintenanceErrorCode.MAINTENANCE_INVESTMENT_REMOTE_ERROR) : ApiResponse.success(switched));
            }
        };
    }

    /** 记录调用的客户端桩，用于断言租户头与请求体透传 */
    private static final class RecordingClient implements InvestmentAccountSwitchClient {

        private final List<InvestmentAccountQueryResult>  accounts;
        private final InvestmentAccountQueryResult        switched;
        private       String                              queryPolicyId;
        private       String                              queryTenantId;
        private       String                              switchAccountId;
        private       String                              switchTenantId;
        private       InvestmentAccountApi.SwitchUnitsRequest switchBody;

        private RecordingClient(List<InvestmentAccountQueryResult> accounts,
                InvestmentAccountQueryResult switched) {
            this.accounts = accounts;
            this.switched = switched;
        }

        @Override
        public ApiResponse<List<InvestmentAccountQueryResult>> getAccountsByPolicy(String policyId, String tenantId) {
            this.queryPolicyId = policyId;
            this.queryTenantId = tenantId;
            return ApiResponse.success(accounts);
        }

        @Override
        public ApiResponse<InvestmentAccountQueryResult> switchUnits(String accountId,
                InvestmentAccountApi.SwitchUnitsRequest request, String tenantId) {
            this.switchAccountId = accountId;
            this.switchTenantId = tenantId;
            this.switchBody = request;
            assertNotNull(request, "转换请求体不得为空");
            return ApiResponse.success(switched);
        }
    }
}
