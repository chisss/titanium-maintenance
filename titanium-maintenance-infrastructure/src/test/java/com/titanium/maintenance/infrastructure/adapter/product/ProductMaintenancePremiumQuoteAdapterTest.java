package com.titanium.maintenance.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.product.ProductMaintenancePremiumQuotePort.QuoteFact;
import com.titanium.maintenance.port.product.ProductMaintenancePremiumQuotePort.QuoteRequest;
import com.titanium.maintenance.port.product.ProductMaintenancePremiumQuotePort.SnapshotReference;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductMaintenancePremiumQuoteApi;
import com.titanium.product.api.response.premium.MaintenancePremiumQuoteResponse;

import feign.FeignException;
import feign.Request;
import feign.Response;

class ProductMaintenancePremiumQuoteAdapterTest {

    private ProductMaintenancePremiumQuoteApi api;
    private ProductMaintenancePremiumQuoteAdapter adapter;

    @BeforeEach
    void setUp() {
        api = mock(ProductMaintenancePremiumQuoteApi.class);
        adapter = new ProductMaintenancePremiumQuoteAdapter(api);
    }

    @Test
    void shouldMapAndValidateAllAuthoritativeQuoteFields() {
        QuoteRequest request = request();
        when(api.quote(eq("product-1"), any(), eq("tenant-1")))
                .thenReturn(ApiResponse.success(response(request, "tenant-1")));

        QuoteFact fact = adapter.quote(request);

        assertEquals("quote-1", fact.quoteId());
        assertEquals(MaintenanceBalanceDirection.DEBIT, fact.direction());
        assertEquals(request.payloadHash(), fact.payloadHash());
        assertEquals("plan-v2", fact.pricingPlanVersion());
    }

    @Test
    void shouldRejectMismatchedEchoAndRemoteFailure() {
        QuoteRequest request = request();
        when(api.quote(eq("product-1"), any(), eq("tenant-1")))
                .thenReturn(ApiResponse.success(response(request, "tenant-2")));
        assertThrows(BusinessException.class, () -> adapter.quote(request));

        when(api.quote(eq("product-1"), any(), eq("tenant-1")))
                .thenThrow(new RuntimeException("timeout"));
        assertThrows(BusinessException.class, () -> adapter.quote(request));
    }

    @Test
    void shouldPreserveStructuredProductValidationFailure() {
        QuoteRequest request = request();
        when(api.quote(eq("product-1"), any(), eq("tenant-1")))
                .thenThrow(productValidationFailure());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> adapter.quote(request));

        // 下游字符串错误码不允许作为本域业务错误码外泄，收敛为本域远程调用错误码并保留下游信息于消息
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PRODUCT_QUOTE_REMOTE_ERROR.getCode(), exception.getErrorCode());
        assertEquals("Product 保全报价下游拒绝: 定价方案校验失败: 原计算用途不受支持 "
                + "[code=60000117, httpStatus=422]", exception.getMessage());
    }

    @Test
    void shouldPreserveInBandProductValidationFailure() {
        QuoteRequest request = request();
        when(api.quote(eq("product-1"), any(), eq("tenant-1")))
                .thenReturn(new ApiResponse<>(
                        "60000117", "定价方案校验失败: 原计算用途不受支持", null));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> adapter.quote(request));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_PRODUCT_QUOTE_REMOTE_ERROR.getCode(), exception.getErrorCode());
        assertEquals("Product 保全报价下游拒绝: 定价方案校验失败: 原计算用途不受支持 "
                + "[code=60000117, httpStatus=422]", exception.getMessage());
    }

    private QuoteRequest request() {
        OffsetDateTime capturedAt = OffsetDateTime.of(
                2026, 8, 25, 8, 0, 0, 0, ZoneOffset.ofHours(8));
        return new QuoteRequest(
                "tenant-1", "product-1", "case-1", "policy-1", 7L,
                "COVERAGE_AMOUNT_CHANGE", "product-v3", "plan-v2", "ENDORSEMENT",
                new SnapshotReference("before.json", "a".repeat(64), 7L, capturedAt),
                new SnapshotReference("proposed.json", "b".repeat(64), 7L, capturedAt.plusMinutes(5)),
                "original-calc", LocalDateTime.parse("2026-08-25T09:00:00"), "CNY",
                new BigDecimal("500000"), 35, "M", 10, 20, 12,
                Map.of("insured.occupation", "1"), List.of(), "agent", 3,
                "保额增加", "c".repeat(64));
    }

    private MaintenancePremiumQuoteResponse response(QuoteRequest request, String tenantId) {
        LocalDateTime quotedAt = LocalDateTime.parse("2026-08-25T10:00:00");
        return new MaintenancePremiumQuoteResponse(
                tenantId, request.maintenanceId(), request.policyId(), request.policyBaselineVersion(),
                request.productId(), request.productVersion(), request.planVersion(), request.itemCode(),
                request.beforeSnapshot().contentHash(), request.proposedSnapshot().contentHash(),
                "quote-1", "d".repeat(64), request.originalCalculationId(), "e".repeat(64),
                "replacement-calc", "f".repeat(64), "plan-v2", "1".repeat(64),
                request.idempotencyKey(), request.payloadHash(), "2".repeat(64),
                "DEBIT 20 CNY; lines=1", "DEBIT", new BigDecimal("20"), "CNY",
                quotedAt, quotedAt.plusHours(24));
    }

    private FeignException productValidationFailure() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "/api/v1/products/product-1/maintenance-premium-quotes",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .status(422)
                .reason("Unprocessable Entity")
                .request(request)
                .headers(Map.of())
                .body("{\"code\":\"60000117\",\"message\":\"定价方案校验失败: 原计算用途不受支持\"}",
                        StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("ProductMaintenancePremiumQuoteApi#quote", response);
    }
}
