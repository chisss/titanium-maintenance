package com.titanium.maintenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 保全服务启动类（组合根）
 * <p>
 * 写侧为 Axon 事件溯源聚合根，写模型实体位于 {@code infrastructure.entity}；读侧读模型 {@code query.view}
 * 由投影维护。JPA 同时扫描写侧 {@code infrastructure.entity} 与读侧 {@code query.view}， 仓储扫描
 * {@code infrastructure.repository} 与 {@code query.repository}。 {@code @EnableScheduling} 驱动读侧 DLQ 重试。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.titanium.maintenance")
@EntityScan(basePackages = { "com.titanium.maintenance.infrastructure.entity", "com.titanium.maintenance.query.view" })
@EnableJpaRepositories(basePackages = { "com.titanium.maintenance.infrastructure.repository",
        "com.titanium.maintenance.query.repository" })
@EnableFeignClients(basePackages = "com.titanium.maintenance.infrastructure.client")
public class Bootstrap {
    public static void main(String[] args) {
        SpringApplication.run(Bootstrap.class, args);
    }
}
