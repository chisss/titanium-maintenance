package com.titanium.maintenance.application.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** 项目撤销失败自动恢复运行参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "titanium.maintenance.withdrawal-recovery")
public class MaintenanceWithdrawalRecoveryProperties {

    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration retryDelay = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int maxAttempts = 5;
}
