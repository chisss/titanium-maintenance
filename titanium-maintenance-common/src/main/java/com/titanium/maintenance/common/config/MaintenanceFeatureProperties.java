package com.titanium.maintenance.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 保全域功能开关配置（m22-04 / 审查报告 SPR-03）。
 *
 * <p><b>改造缘由</b>：这 4 个开关原以 {@code @Value} 分散注入在 4 个
 * {@code PropertyMaintenance*FeatureAdapter} 里。散落的 {@code @Value} 无类型安全
 * （键名拼错编译期无感）、无法集中查阅「本域有哪些开关」、重构易漏改。
 * 收拢为类型安全的配置对象后，一处即可看到全部开关及其默认值。</p>
 *
 * <p>🔴 全部为 {@code legacy-*} 遗留路径开关 + 1 个配置写开关：它们控制**过渡期**行为，
 * 默认 {@code true} 保持既有行为不变；下线遗留路径时应连同本类字段一并删除。</p>
 */
@Component
@ConfigurationProperties(prefix = "titanium.maintenance")
@Getter
@Setter
public class MaintenanceFeatureProperties {

    /** 是否允许遗留的保全创建路径（过渡期开关，默认保持既有行为）。 */
    private boolean legacyCreationEnabled = true;

    /** 是否允许遗留的保全执行路径。 */
    private boolean legacyExecutionEnabled = true;

    /** 是否允许遗留的保费计算路径。 */
    private boolean legacyPremiumCalculationEnabled = true;

    /** 是否允许写配置（configuration 子域的写开关）。 */
    private Configuration configuration = new Configuration();

    /** configuration 子域开关组。 */
    @Getter
    @Setter
    public static class Configuration {
        /** 是否允许写配置。 */
        private boolean writeEnabled = true;
    }
}
