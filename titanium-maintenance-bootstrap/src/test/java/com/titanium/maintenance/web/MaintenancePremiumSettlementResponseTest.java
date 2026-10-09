package com.titanium.maintenance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.titanium.maintenance.api.response.MaintenancePremiumSettlementResponse;
import com.titanium.maintenance.application.model.premium.MaintenancePremiumSettlementResult;
import com.titanium.maintenance.web.mapper.MaintenanceWebMapper;
import com.titanium.maintenance.web.mapper.MaintenanceWebMapperImpl;

class MaintenancePremiumSettlementResponseTest {

    private final MaintenanceWebMapper webMapper = new MaintenanceWebMapperImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPreservePostingFieldsAndAppendFinancialSettlementFields() {
        MaintenancePremiumSettlementResult result = new MaintenancePremiumSettlementResult(
                "maintenance-1", "SETTLEMENT_PENDING", "calc-original", "calc-replacement", "adjustment-1",
                "hash-1", "posting-1", "POSTED", "CREDIT", new BigDecimal("53.00"), "CNY",
                "refund-instruction-1", "refund-order-1", "PROCESSING", 2, new BigDecimal("9.99"));

        MaintenancePremiumSettlementResponse response = webMapper.toSettlementResponse(result);
        JsonNode json = objectMapper.valueToTree(response);

        assertEquals("posting-1", json.get("billingPostingId").asText());
        assertEquals("POSTED", json.get("billingPostingStatus").asText());
        assertEquals("refund-instruction-1", json.get("refundInstructionId").asText());
        assertEquals("refund-order-1", json.get("refundOrderId").asText());
        assertEquals("PROCESSING", json.get("refundStatus").asText());
        assertEquals(2, json.get("commissionAdjustmentCount").asInt());
        // 追回金额须经 MapStruct 同名自动映射原样出站，不得被丢弃或改写。
        assertEquals(0, new BigDecimal("9.99").compareTo(json.get("commissionClawbackAmount").decimalValue()));
    }
}
