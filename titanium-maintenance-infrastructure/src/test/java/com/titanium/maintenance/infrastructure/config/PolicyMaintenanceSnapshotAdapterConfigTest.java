package com.titanium.maintenance.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.titanium.maintenance.infrastructure.adapter.PolicyMaintenanceSnapshotAdapter;
import com.titanium.maintenance.infrastructure.client.PolicyServiceClient;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;

class PolicyMaintenanceSnapshotAdapterConfigTest {

    @Test
    void shouldSelectRealPolicySnapshotAdapterInProductionAssembly() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PolicyServiceClient.class, () -> mock(PolicyServiceClient.class));
            context.register(PolicyMaintenanceSnapshotAdapter.class, PolicyMaintenanceSnapshotAdapterConfig.class);
            context.refresh();

            Map<String, PolicyMaintenanceSnapshotPort> adapters = context
                    .getBeansOfType(PolicyMaintenanceSnapshotPort.class);
            assertEquals(1, adapters.size());
            assertInstanceOf(PolicyMaintenanceSnapshotAdapter.class, adapters.values().iterator().next());
        }
    }
}
