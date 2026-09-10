package com.titanium.maintenance.infrastructure.adapter.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.customer.api.response.CustomerResponse;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.customer.CustomerServiceClient;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

class CustomerServiceAdapterTest {

    @Test
    @DisplayName("客户域正常应答时按 /exists 结果返回存在性")
    void shouldReturnExistenceFromDedicatedEndpoint() {
        CustomerServiceAdapter adapter = new CustomerServiceAdapter(client(true));

        assertTrue(adapter.customerExists("customer-1", "tenant-1"));
    }

    @Test
    @DisplayName("客户域正常应答但不存在：返回 false，不升级为不可达")
    void shouldReturnFalseWhenCustomerAbsentButServiceReachable() {
        CustomerServiceAdapter adapter = new CustomerServiceAdapter(client(false));

        assertFalse(adapter.customerExists("customer-1", "tenant-1"));
    }

    @Test
    @DisplayName("客户服务不可达时抛远程调用异常，不得伪装成『客户不存在』（D13 同规回归）")
    void shouldThrowRemoteCallExceptionWhenCustomerServiceUnreachable() {
        CustomerServiceAdapter adapter = new CustomerServiceAdapter(new CustomerServiceClient() {
            @Override
            public CustomerResponse getCustomer(String customerId, String tenantId) {
                throw unreachable();
            }

            @Override
            public boolean isCustomerExists(String customerId, String tenantId) {
                throw unreachable();
            }

            private RuntimeException unreachable() {
                return new RuntimeException(new ConnectException("Connection refused: titanium-customer"));
            }
        });

        MaintenanceRemoteCallException error = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.customerExists("customer-1", "tenant-1"));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_CUSTOMER_REMOTE_ERROR.getCode(), error.getErrorCode());
    }

    private CustomerServiceClient client(boolean exists) {
        return new CustomerServiceClient() {
            @Override
            public CustomerResponse getCustomer(String customerId, String tenantId) {
                return null;
            }

            @Override
            public boolean isCustomerExists(String customerId, String tenantId) {
                return exists;
            }
        };
    }
}
