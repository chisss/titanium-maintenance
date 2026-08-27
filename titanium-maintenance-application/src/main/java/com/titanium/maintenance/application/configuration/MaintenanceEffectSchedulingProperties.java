package com.titanium.maintenance.application.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** 未来生效调度运行参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "titanium.maintenance.scheduling")
public class MaintenanceEffectSchedulingProperties {

    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration retryDelay = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int maxAttempts = 5;
}
