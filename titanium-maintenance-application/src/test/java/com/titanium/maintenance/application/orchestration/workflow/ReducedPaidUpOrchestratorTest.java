package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.application.model.effect.ReducedPaidUpFact;
import com.titanium.maintenance.application.model.effect.ReducedPaidUpOrder;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.policy.PolicyFinancialPort;
import com.titanium.maintenance.port.policy.PolicyFinancialPort.PolicyFinancialData;
import com.titanium.maintenance.port.product.ProductReducedPaidUpPolicyPort;
import com.titanium.maintenance.port.product.ProductReducedPaidUpPolicyPort.ReducedPaidUpPolicy;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 减额缴清核定编排器测试（g04-05）。
 * <p>
 * 覆盖「取数 → 委派值对象核定 → 产出减额缴清事实」的两步编排：正例（减额成立时透出事实并保留取数要素）
 * 与反例（减额不成立时携 71 段错误码 71007012 失败关闭）。编排器不启动容器，端口以 mock 注入。
 * </p>
 * <p>
 * 🔴 反例用例守的是<b>抛出点本身</b>：{@code MaintenanceEffectApplicationServiceTest} 只覆盖了该异常的
 * 「记录失败事实并向上传播」，那里的编排器是被桩替换的（stub 直接抛）。若没有本用例，判定分支
 * 「{@code isReductionValid()} 为假即拒绝」被改成永不成立时，没有任何测试会变红。
 * </p>
 */
class ReducedPaidUpOrchestratorTest {

    private static final LocalDateTime BUSINESS_TIME = LocalDateTime.parse("2026-09-28T12:00:00");

    private PolicyFinancialPort policyFinancialPort;
    private ProductReducedPaidUpPolicyPort productReducedPaidUpPolicyPort;
    private ReducedPaidUpOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        policyFinancialPort = mock(PolicyFinancialPort.class);
        productReducedPaidUpPolicyPort = mock(ProductReducedPaidUpPolicyPort.class);
        orchestrator = new ReducedPaidUpOrchestrator(policyFinancialPort, productReducedPaidUpPolicyPort);
    }

    @Test
    @DisplayName("正例：减额成立 → 透出事实（新保额=现金价值÷净费率），要素取自两个域的快照")
    void shouldProduceFactWhenReductionIsValid() {
        when(policyFinancialPort.getFinancialSnapshot("policy-1", "tenant-1")).thenReturn(financial(
                new BigDecimal("30000"), new BigDecimal("200000")));
        when(productReducedPaidUpPolicyPort.queryEffectiveReducedPaidUpPolicy("product-1", BUSINESS_TIME))
                .thenReturn(new ReducedPaidUpPolicy(
                        "rate-policy-1", "product-1", new BigDecimal("0.3"), "a".repeat(64)));

        ReducedPaidUpFact fact = orchestrator.calculate(order());

        assertEquals("policy-1", fact.policyId());
        assertEquals("PP-MAIN", fact.mainSegmentId(), "保额字段变更的 objectId 取自主险段标识");
        assertEquals(new BigDecimal("30000"), fact.cashValue());
        assertEquals(new BigDecimal("200000"), fact.originalSumInsured());
        assertEquals(new BigDecimal("0.3"), fact.netSinglePremiumRatePerUnit());
        assertEquals(new BigDecimal("100000.00"), fact.newSumInsured());
        verify(productReducedPaidUpPolicyPort)
                .queryEffectiveReducedPaidUpPolicy("product-1", BUSINESS_TIME);
    }

    @Test
    @DisplayName("反例：新保额超过原保额 → 携 71007012 失败关闭，且不产出任何事实")
    void shouldRejectWhenNewSumInsuredExceedsOriginal() {
        when(policyFinancialPort.getFinancialSnapshot("policy-1", "tenant-1")).thenReturn(financial(
                new BigDecimal("90000"), new BigDecimal("100000")));
        when(productReducedPaidUpPolicyPort.queryEffectiveReducedPaidUpPolicy("product-1", BUSINESS_TIME))
                .thenReturn(new ReducedPaidUpPolicy(
                        "rate-policy-1", "product-1", new BigDecimal("0.5"), "a".repeat(64)));

        MaintenanceValidationException error = assertThrows(MaintenanceValidationException.class,
                () -> orchestrator.calculate(order()));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_EXCEEDS_SUM_INSURED.getCode(),
                error.getErrorCodeValue(),
                "必须携 71 段错误码 71007012（红线 19：异常携带 BaseErrorCode 枚举）");
        assertTrue(error.getMessage().contains("减额缴清"), error.getMessage());
    }

    @Test
    @DisplayName("失败关闭：保单/产品任一侧取数失败即上抛，不回落任何默认值")
    void shouldFailClosedWhenSnapshotUnavailable() {
        when(policyFinancialPort.getFinancialSnapshot("policy-1", "tenant-1"))
                .thenThrow(new IllegalStateException("policy domain unavailable"));

        assertThrows(IllegalStateException.class, () -> orchestrator.calculate(order()));
        verifyNoInteractions(productReducedPaidUpPolicyPort);
    }

    private ReducedPaidUpOrder order() {
        return new ReducedPaidUpOrder("tenant-1", "maintenance-1", "policy-1", "product-1", BUSINESS_TIME);
    }

    private PolicyFinancialData financial(BigDecimal cashValue, BigDecimal sumInsured) {
        return new PolicyFinancialData("policy-1", cashValue, sumInsured, "PP-MAIN",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.parse("2020-01-01"), 6);
    }
}
