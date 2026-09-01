package com.titanium.maintenance.web.dto.withdrawal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class WithdrawMaintenanceItemDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadAllowedFields() throws Exception {
        WithdrawMaintenanceItemDTO request = objectMapper.readValue(
                """
                        {"operationId":"operation-1","reason":"客户取消项目","paymentMethod":"BANK_CARD"}
                        """,
                WithdrawMaintenanceItemDTO.class);

        assertEquals("operation-1", request.operationId());
        assertEquals("BANK_CARD", request.paymentMethod());
    }

    @Test
    void shouldRejectClientSuppliedFinancialFact() {
        String json = """
                {"operationId":"operation-1","reason":"客户取消项目","amount":20.00}
                """;

        assertThrows(JsonMappingException.class,
                () -> objectMapper.readValue(json, WithdrawMaintenanceItemDTO.class));
    }
}
