package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyFinancialUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyFinancialClient;
import com.titanium.maintenance.port.policy.PolicyFinancialPort.PolicyFinancialData;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.policy.PolicyFinancialResponse;

/**
 * 保单财务快照适配器测试（g04-02）。
 * <p>
 * 守失败关闭三分面（D13 范式）：① 保单域不可达（抛异常）→ {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 → {@code PolicyFinancialUnavailableException}
 * （财务要素不可得）；③ 应答回显不一致 → 契约无效同「取数失败」。三种失败均不得降级为 0 兜底。
 * </p>
 */
class PolicyFinancialAdapterTest {

    private static final String POLICY_ID = "P1";
    private static final String TENANT_ID = "T1";
    /** 主险段ID：减额缴清回写 policy.coverage.sumInsured 时的 objectId（g04-05）。 */
    private static final String MAIN_SEGMENT_ID = "PP-MAIN";

    @Test
    void shouldMapSuccessfulSnapshotIntoDomainData() {
        PolicyFinancialAdapter adapter = new PolicyFinancialAdapter(
                policyId -> ApiResponse.success(financialResponse(POLICY_ID)));

        PolicyFinancialData data = adapter.getFinancialSnapshot(POLICY_ID, TENANT_ID);

        assertEquals(POLICY_ID, data.policyId());
        assertEquals(new BigDecimal("8800.00"), data.cashValue());
        assertEquals(new BigDecimal("50000"), data.sumInsured());
        assertEquals(MAIN_SEGMENT_ID, data.mainSegmentId());
        assertEquals(new BigDecimal("10000"), data.loanOutstandingPrincipal());
        assertEquals(new BigDecimal("600.00"), data.loanOutstandingInterest());
        assertEquals(new BigDecimal("500.00"), data.arrearsAmount());
        assertEquals(new BigDecimal("300.00"), data.dividendBalance());
        assertEquals(new BigDecimal("20000"), data.accountValue());
        assertEquals(LocalDate.of(2025, 1, 1), data.effectiveDate());
        assertEquals(2, data.paidInstallments());
        // 退费口径四项：漏映射任一项，退保在未满期保费基准下只能按「事实不可得」拒绝，编译期无感
        assertEquals("ISSUE-BIZ-1", data.issuanceBizNo());
        assertEquals(new BigDecimal("3650.00"), data.annualPremium());
        assertEquals(LocalDate.of(2025, 12, 31), data.coverageEndDate());
        assertEquals(false, data.premiumPaidUp());
    }

    @Test
    @DisplayName("保单域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成取数失败")
    void shouldThrowRemoteCallExceptionWhenPolicyUnreachable() {
        PolicyFinancialClient unreachableClient = policyId -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-policy"));
        };
        PolicyFinancialAdapter adapter = new PolicyFinancialAdapter(unreachableClient);

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.getFinancialSnapshot(POLICY_ID, TENANT_ID));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("保单域正常应答但业务码失败：财务要素不可得，不升级为不可达")
    void shouldThrowFinancialUnavailableWhenDownstreamBusinessCodeFailed() {
        PolicyFinancialAdapter adapter = new PolicyFinancialAdapter(
                policyId -> ApiResponse.passthrough("20000000", "保单不存在"));

        PolicyFinancialUnavailableException exception = assertThrows(PolicyFinancialUnavailableException.class,
                () -> adapter.getFinancialSnapshot(POLICY_ID, TENANT_ID));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_FINANCIAL_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同取数失败处置")
    void shouldThrowFinancialUnavailableWhenResponsePayloadInvalid() {
        PolicyFinancialAdapter nullResponseAdapter = new PolicyFinancialAdapter(policyId -> null);
        assertThrows(PolicyFinancialUnavailableException.class,
                () -> nullResponseAdapter.getFinancialSnapshot(POLICY_ID, TENANT_ID));

        PolicyFinancialAdapter nullDataAdapter = new PolicyFinancialAdapter(policyId -> ApiResponse.success(null));
        assertThrows(PolicyFinancialUnavailableException.class,
                () -> nullDataAdapter.getFinancialSnapshot(POLICY_ID, TENANT_ID));
    }

    @Test
    @DisplayName("应答保单号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowFinancialUnavailableWhenEchoMismatched() {
        PolicyFinancialAdapter adapter = new PolicyFinancialAdapter(
                policyId -> ApiResponse.success(financialResponse("OTHER-POLICY")));

        PolicyFinancialUnavailableException exception = assertThrows(PolicyFinancialUnavailableException.class,
                () -> adapter.getFinancialSnapshot(POLICY_ID, TENANT_ID));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_FINANCIAL_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    /** 构造字段齐备的保单域财务响应（金额非负、期数非负，满足契约紧凑构造器校验）。 */
    private PolicyFinancialResponse financialResponse(String policyId) {
        return new PolicyFinancialResponse(
                policyId,
                new BigDecimal("8800.00"),
                new BigDecimal("50000"),
                MAIN_SEGMENT_ID,
                new BigDecimal("10000"),
                new BigDecimal("600.00"),
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                new BigDecimal("20000"),
                LocalDate.of(2025, 1, 1),
                2,
                "ISSUE-BIZ-1",
                new BigDecimal("3650.00"),
                LocalDate.of(2025, 12, 31),
                false);
    }
}
