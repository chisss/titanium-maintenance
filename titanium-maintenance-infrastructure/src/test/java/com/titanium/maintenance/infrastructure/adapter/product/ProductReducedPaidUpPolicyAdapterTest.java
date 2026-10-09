package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.ProductReducedPaidUpPolicyUnavailableException;
import com.titanium.maintenance.infrastructure.client.product.ProductReducedPaidUpPolicyClient;
import com.titanium.maintenance.port.product.ProductReducedPaidUpPolicyPort.ReducedPaidUpPolicy;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.paidup.ReducedPaidUpPolicyQueryResponse;

/**
 * 减额缴清净费率策略查询适配器测试（g04-05）。
 * <p>
 * 守失败关闭三分面（D13 范式，与 {@code ProductLoanPolicyAdapterTest} 同构）：① 产品域不可达（抛异常）→
 * {@code MaintenanceRemoteCallException}（HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 →
 * {@code ProductReducedPaidUpPolicyUnavailableException}；③ 应答回显不一致 → 契约无效同「取数失败」。
 * 三种失败<b>均不得降级为近似费率兜底</b>——减额缴清的新保额是精算结果，任何猜测都会改变合同要素。
 * </p>
 */
class ProductReducedPaidUpPolicyAdapterTest {

    private static final String PRODUCT_ID = "PROD-1";
    private static final LocalDateTime BUSINESS_TIME = LocalDateTime.of(2026, 9, 28, 12, 0);

    @Test
    void shouldMapSuccessfulPaidUpPolicyIntoDomainValue() {
        ProductReducedPaidUpPolicyAdapter adapter = new ProductReducedPaidUpPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(paidUpPolicyResponse(PRODUCT_ID)));

        ReducedPaidUpPolicy ratePolicy = adapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME);

        assertEquals("PP-1", ratePolicy.policyId());
        assertEquals(PRODUCT_ID, ratePolicy.productId());
        assertEquals(new BigDecimal("0.3000"), ratePolicy.netSinglePremiumRatePerUnit());
        assertEquals("hash-abc", ratePolicy.policyContentHash());
    }

    @Test
    @DisplayName("产品域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成策略不可得")
    void shouldThrowRemoteCallExceptionWhenProductUnreachable() {
        ProductReducedPaidUpPolicyClient unreachableClient = (productId, businessTime) -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-product"));
        };
        ProductReducedPaidUpPolicyAdapter adapter = new ProductReducedPaidUpPolicyAdapter(unreachableClient);

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PRODUCT_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("产品域正常应答但业务码失败：该时点无已发布减额缴清策略，不升级为不可达")
    void shouldThrowPaidUpPolicyUnavailableWhenDownstreamBusinessCodeFailed() {
        ProductReducedPaidUpPolicyAdapter adapter = new ProductReducedPaidUpPolicyAdapter(
                (productId, businessTime) -> ApiResponse.passthrough("60000000", "无已发布减额缴清策略"));

        ProductReducedPaidUpPolicyUnavailableException exception = assertThrows(
                ProductReducedPaidUpPolicyUnavailableException.class,
                () -> adapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_POLICY_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同取数失败处置")
    void shouldThrowPaidUpPolicyUnavailableWhenResponsePayloadInvalid() {
        ProductReducedPaidUpPolicyAdapter nullResponseAdapter = new ProductReducedPaidUpPolicyAdapter(
                (productId, businessTime) -> null);
        assertThrows(ProductReducedPaidUpPolicyUnavailableException.class,
                () -> nullResponseAdapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME));

        ProductReducedPaidUpPolicyAdapter nullDataAdapter = new ProductReducedPaidUpPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(null));
        assertThrows(ProductReducedPaidUpPolicyUnavailableException.class,
                () -> nullDataAdapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME));
    }

    @Test
    @DisplayName("应答产品号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowPaidUpPolicyUnavailableWhenEchoMismatched() {
        ProductReducedPaidUpPolicyAdapter adapter = new ProductReducedPaidUpPolicyAdapter(
                (productId, businessTime) -> ApiResponse.success(paidUpPolicyResponse("OTHER-PROD")));

        ProductReducedPaidUpPolicyUnavailableException exception = assertThrows(
                ProductReducedPaidUpPolicyUnavailableException.class,
                () -> adapter.queryEffectiveReducedPaidUpPolicy(PRODUCT_ID, BUSINESS_TIME));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_REDUCED_PAID_UP_POLICY_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    /** 构造字段齐备的产品域减额缴清净费率策略响应。 */
    private ReducedPaidUpPolicyQueryResponse paidUpPolicyResponse(String productId) {
        return new ReducedPaidUpPolicyQueryResponse(
                "PP-1", productId, "RPU-CODE", "v1",
                new BigDecimal("0.3000"),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2099, 12, 31, 23, 59),
                "hash-abc");
    }
}
