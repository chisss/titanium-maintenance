package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.ProductLoanPolicyUnavailableException;
import com.titanium.maintenance.infrastructure.client.product.ProductLoanPolicyClient;
import com.titanium.maintenance.port.product.ProductLoanPolicyPort.LoanPolicy;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.loan.LoanPolicyQueryResponse;

/**
 * 保单贷款策略查询适配器测试（g04-03）。
 * <p>
 * 守失败关闭三分面（D13 范式）：① 产品域不可达（抛异常）→ {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 → {@code ProductLoanPolicyUnavailableException}；
 * ③ 应答回显不一致 → 契约无效同「取数失败」。三种失败均不得降级为默认比例/利率兜底。
 * </p>
 */
class ProductLoanPolicyAdapterTest {

    private static final String PRODUCT_ID = "PROD-1";

    @Test
    void shouldMapSuccessfulLoanPolicyIntoDomainValue() {
        ProductLoanPolicyAdapter adapter = new ProductLoanPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(loanPolicyResponse(PRODUCT_ID)));

        LoanPolicy loanPolicy = adapter.queryEffectiveLoanPolicy(PRODUCT_ID, null);

        assertEquals("POL-1", loanPolicy.policyId());
        assertEquals(PRODUCT_ID, loanPolicy.productId());
        assertEquals(new BigDecimal("0.8000"), loanPolicy.loanableRatio());
        assertEquals(new BigDecimal("0.0500"), loanPolicy.interestRate());
        assertEquals("hash-abc", loanPolicy.policyContentHash());
    }

    @Test
    @DisplayName("产品域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成策略不可得")
    void shouldThrowRemoteCallExceptionWhenProductUnreachable() {
        ProductLoanPolicyClient unreachableClient = (productId, businessTime) -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-product"));
        };
        ProductLoanPolicyAdapter adapter = new ProductLoanPolicyAdapter(unreachableClient);

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.queryEffectiveLoanPolicy(PRODUCT_ID, null));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PRODUCT_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("产品域正常应答但业务码失败：该时点无已发布贷款策略，不升级为不可达")
    void shouldThrowLoanPolicyUnavailableWhenDownstreamBusinessCodeFailed() {
        ProductLoanPolicyAdapter adapter = new ProductLoanPolicyAdapter(
                (productId, businessTime) -> ApiResponse.passthrough("60000000", "无已发布贷款策略"));

        ProductLoanPolicyUnavailableException exception = assertThrows(
                ProductLoanPolicyUnavailableException.class,
                () -> adapter.queryEffectiveLoanPolicy(PRODUCT_ID, null));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_POLICY_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同取数失败处置")
    void shouldThrowLoanPolicyUnavailableWhenResponsePayloadInvalid() {
        ProductLoanPolicyAdapter nullResponseAdapter = new ProductLoanPolicyAdapter(
                (productId, businessTime) -> null);
        assertThrows(ProductLoanPolicyUnavailableException.class,
                () -> nullResponseAdapter.queryEffectiveLoanPolicy(PRODUCT_ID, null));

        ProductLoanPolicyAdapter nullDataAdapter = new ProductLoanPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(null));
        assertThrows(ProductLoanPolicyUnavailableException.class,
                () -> nullDataAdapter.queryEffectiveLoanPolicy(PRODUCT_ID, null));
    }

    @Test
    @DisplayName("应答产品号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowLoanPolicyUnavailableWhenEchoMismatched() {
        ProductLoanPolicyAdapter adapter = new ProductLoanPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(loanPolicyResponse("OTHER-PROD")));

        ProductLoanPolicyUnavailableException exception = assertThrows(
                ProductLoanPolicyUnavailableException.class,
                () -> adapter.queryEffectiveLoanPolicy(PRODUCT_ID, null));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_POLICY_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    /** 构造字段齐备的产品域贷款策略响应。 */
    private LoanPolicyQueryResponse loanPolicyResponse(String productId) {
        return new LoanPolicyQueryResponse(
                "POL-1", productId, "POLICY-CODE", "v1",
                new BigDecimal("0.8000"), new BigDecimal("0.0500"),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2099, 12, 31, 23, 59),
                "hash-abc");
    }
}
