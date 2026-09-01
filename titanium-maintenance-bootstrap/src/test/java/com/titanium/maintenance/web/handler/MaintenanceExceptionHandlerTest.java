package com.titanium.maintenance.web.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.common.exception.MaintenanceSettlementConflictException;
import com.titanium.maintenance.web.response.error.MaintenanceErrorVO;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class MaintenanceExceptionHandlerTest {

    private final MaintenanceExceptionHandler handler = new MaintenanceExceptionHandler();

    @Test
    void shouldPreserveNotFoundStatusAndErrorCode() {
        ResponseEntity<MaintenanceErrorVO> response =
                handler.handleNotFoundException(
                        new MaintenanceNotFoundException(), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(MaintenanceErrorCode.MAINTENANCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldPreserveConflictStatusAndErrorCode() {
        MaintenanceSettlementConflictException exception = new MaintenanceSettlementConflictException(
                "保全计价产品与保单产品不一致", MaintenanceErrorCode.MAINTENANCE_PREMIUM_PRODUCT_MISMATCH);

        ResponseEntity<MaintenanceErrorVO> response = handler.handleSettlementConflictException(
                exception, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(MaintenanceErrorCode.MAINTENANCE_PREMIUM_PRODUCT_MISMATCH.getCode());
    }
}
