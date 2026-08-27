package com.titanium.maintenance.web.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceNotFoundException;
import com.titanium.maintenance.web.response.MaintenanceErrorVO;

class MaintenanceExceptionHandlerTest {

    private final MaintenanceExceptionHandler handler = new MaintenanceExceptionHandler();

    @Test
    void shouldPreserveNotFoundStatusAndErrorCode() {
        ResponseEntity<MaintenanceErrorVO> response =
                handler.handleBusinessException(
                        new MaintenanceNotFoundException(), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MAINTENANCE_NOT_FOUND");
    }

    @Test
    void shouldPreserveConflictStatusAndErrorCode() {
        BusinessException exception = new BusinessException(
                "保全计价产品与保单产品不一致", "MAINTENANCE_PREMIUM_PRODUCT_MISMATCH", HttpStatus.CONFLICT);

        ResponseEntity<MaintenanceErrorVO> response = handler.handleBusinessException(
                exception, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MAINTENANCE_PREMIUM_PRODUCT_MISMATCH");
    }
}
