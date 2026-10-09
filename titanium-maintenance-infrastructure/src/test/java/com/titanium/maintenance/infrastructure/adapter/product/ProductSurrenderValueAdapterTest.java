package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.product.ProductSurrenderValueClient;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFact;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderFactorFact;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort.SurrenderRequest;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.request.premium.SurrenderValueCalculationRequest;
import com.titanium.product.api.response.premium.SurrenderFactorResponse;
import com.titanium.product.api.response.premium.SurrenderValueCalculationResponse;

/**
 * Product 退保价值端口防腐适配器契约测试（g05-01）
 * <p>
 * 🔴 本类的核心断言是<b>退费因子明细真的被透传</b>：跨域契约新增字段后最常见的静默失败是
 * 「对端发了、本域没映射」，编译与既有用例都不会报错，只有明细在对客展示与对账处凭空消失
 * 才被发现。故此处按字段逐项断言，并对 {@code factors == null}（对端为旧版本 / 反序列化缺字段）
 * 显式固化「降级为空列表」而非 NPE。
 * </p>
 */
class ProductSurrenderValueAdapterTest {

    @Test
    @DisplayName("退费因子明细逐项透传：类型码 / 方向码 / 金额 / 文案键")
    void shouldPassThroughFactorFacts() {
        RecordingClient client = new RecordingClient(ApiResponse.success(response(List.of(
                new SurrenderFactorResponse("CASH_VALUE", "CREDIT", new BigDecimal("72.72"),
                        "surrender.factor.cashValue"),
                new SurrenderFactorResponse("LOAN_PRINCIPAL_INTEREST", "DEBIT", new BigDecimal("10.00"),
                        "surrender.factor.loanPrincipalInterest")))));
        ProductSurrenderValueAdapter adapter = new ProductSurrenderValueAdapter(client);

        SurrenderFact fact = adapter.calculate(request());

        List<SurrenderFactorFact> factors = fact.factors();
        assertEquals(2, factors.size());
        assertEquals("CASH_VALUE", factors.get(0).factorType());
        assertEquals("CREDIT", factors.get(0).direction());
        assertEquals(0, factors.get(0).amount().compareTo(new BigDecimal("72.72")));
        assertEquals("surrender.factor.cashValue", factors.get(0).explainKey());
        assertEquals("LOAN_PRINCIPAL_INTEREST", factors.get(1).factorType());
        assertEquals("DEBIT", factors.get(1).direction());
    }

    @Test
    @DisplayName("对端未返回因子明细（旧版本 / 缺字段）降级为空列表，不得抛空指针")
    void shouldDegradeToEmptyFactorsWhenResponseOmitsThem() {
        ProductSurrenderValueAdapter adapter = adapterWith(ApiResponse.success(response(null)));

        SurrenderFact fact = adapter.calculate(request());

        assertTrue(fact.factors().isEmpty());
    }

    @Test
    @DisplayName("既有字段映射不回归：金额、方向、币种与策略取证字段照旧逐项取对端应答")
    void shouldKeepExistingFieldMappingUnchanged() {
        ProductSurrenderValueAdapter adapter = adapterWith(ApiResponse.success(response(List.of())));

        SurrenderFact fact = adapter.calculate(request());

        assertEquals("surrender-req-1", fact.surrenderRequestId());
        assertEquals("SURRENDER-CASH-VALUE", fact.policyCode());
        assertEquals("V1.0", fact.policyVersion());
        assertEquals(2, fact.policyYear());
        assertEquals(15, fact.coolingOffDays());
        assertEquals("CASH_VALUE", fact.refundType());
        assertEquals(Boolean.FALSE, fact.withinCoolingOff());
        assertEquals(0, fact.refundAmount().compareTo(new BigDecimal("72.72")));
        assertEquals(MaintenanceBalanceDirection.CREDIT, fact.direction());
        assertEquals("CNY", fact.currency());
        assertEquals("calc-original", fact.originalCalculationId());
    }

    @Test
    @DisplayName("未满期保费三项事实必须真的过桥（缺任一即被对端读成「事实不可得」并拒绝退算）")
    void shouldCarryUnearnedPremiumFactsIntoOutboundRequest() {
        RecordingClient client = new RecordingClient(ApiResponse.success(response(List.of())));
        ProductSurrenderValueAdapter adapter = new ProductSurrenderValueAdapter(client);

        adapter.calculate(request());

        SurrenderValueCalculationRequest outbound = client.lastRequest();
        assertEquals(0, outbound.annualPremium().compareTo(new BigDecimal("3650.00")));
        assertEquals(LocalDate.of(2026, 12, 31), outbound.coverageEndDate());
        assertEquals(false, outbound.premiumPaidUp());
    }

    @Test
    @DisplayName("贷款未偿本息两项必须真的过桥（漏传即被对端读成「事实不可得」并拒绝退算）")
    void shouldCarryOutstandingLoanFactsIntoOutboundRequest() {
        RecordingClient client = new RecordingClient(ApiResponse.success(response(List.of())));
        ProductSurrenderValueAdapter adapter = new ProductSurrenderValueAdapter(client);
        SurrenderRequest withLoan = new SurrenderRequest(
                "1", "surrender-req-1", "biz-1", "biz-original", "calc-original",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), 2,
                LocalDateTime.of(2026, 8, 20, 10, 0), "退保",
                new BigDecimal("3650.00"), LocalDate.of(2026, 12, 31), false,
                new BigDecimal("1000.00"), new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        adapter.calculate(withLoan);

        SurrenderValueCalculationRequest outbound = client.lastRequest();
        assertEquals(0, outbound.loanOutstandingPrincipal().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, outbound.loanOutstandingInterest().compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("账户余额三项必须原样过桥、不得在本域先行求和（合并口径属产品域，先行求和即丢失原始分量）")
    void shouldCarryBalanceFactsIntoOutboundRequestWithoutMerging() {
        RecordingClient client = new RecordingClient(ApiResponse.success(response(List.of())));
        ProductSurrenderValueAdapter adapter = new ProductSurrenderValueAdapter(client);
        SurrenderRequest withBalance = new SurrenderRequest(
                "1", "surrender-req-1", "biz-1", "biz-original", "calc-original",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), 2,
                LocalDateTime.of(2026, 8, 20, 10, 0), "退保",
                new BigDecimal("3650.00"), LocalDate.of(2026, 12, 31), false,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("300.00"), new BigDecimal("500.00"), new BigDecimal("200.00"));

        adapter.calculate(withBalance);

        SurrenderValueCalculationRequest outbound = client.lastRequest();
        assertEquals(0, outbound.overduePremium().compareTo(new BigDecimal("300.00")));
        assertEquals(0, outbound.dividendBalance().compareTo(new BigDecimal("500.00")),
                "红利余额须保持原值：若在适配器里与账户价值求和，就对端再也拿不到原始分量");
        assertEquals(0, outbound.accountValue().compareTo(new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("业务码失败即抛远程调用异常，不得把服务故障伪装成空结果")
    void shouldThrowOnBusinessFailure() {
        ProductSurrenderValueAdapter adapter = adapterWith(
                ApiResponse.error(MaintenanceErrorCode.MAINTENANCE_SURRENDER_REMOTE_ERROR, "策略不存在"));

        MaintenanceRemoteCallException ex = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.calculate(request()));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_SURRENDER_REMOTE_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("空响应即抛远程调用异常")
    void shouldThrowOnNullResponse() {
        ProductSurrenderValueAdapter adapter = adapterWith(null);

        assertThrows(MaintenanceRemoteCallException.class, () -> adapter.calculate(request()));
    }

    private static ProductSurrenderValueAdapter adapterWith(
            ApiResponse<SurrenderValueCalculationResponse> response) {
        return new ProductSurrenderValueAdapter(new RecordingClient(response));
    }

    private static SurrenderValueCalculationResponse response(List<SurrenderFactorResponse> factors) {
        return new SurrenderValueCalculationResponse(
                "surrender-req-1", "SURRENDER-CASH-VALUE", "V1.0", "hash-policy", 2, 15,
                "CASH_VALUE", "CASH_VALUE", false, new BigDecimal("0.60000000"), new BigDecimal("72.72"),
                new BigDecimal("48.48"), BigDecimal.ZERO, factors,
                "calc-original", "hash-original", "calc-replacement", "hash-replacement",
                "adjustment-1", "hash-request", "hash-adjustment", "plan-1", "hash-plan",
                "CREDIT", new BigDecimal("72.72"), "CNY");
    }

    private static SurrenderRequest request() {
        return new SurrenderRequest("1", "surrender-req-1", "biz-1", "biz-original", "calc-original",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), 2,
                LocalDateTime.of(2026, 8, 20, 10, 0), "退保",
                new BigDecimal("3650.00"), LocalDate.of(2026, 12, 31), false,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** 记录入参并回放固定应答的桩客户端。 */
    private static final class RecordingClient implements ProductSurrenderValueClient {

        private final ApiResponse<SurrenderValueCalculationResponse> response;
        private SurrenderValueCalculationRequest lastRequest;

        private RecordingClient(ApiResponse<SurrenderValueCalculationResponse> response) {
            this.response = response;
        }

        /** 最近一次出站请求，供断言「字段真的过了桥」而非只在本域对象里存在。 */
        private SurrenderValueCalculationRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public ApiResponse<SurrenderValueCalculationResponse> calculate(SurrenderValueCalculationRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
