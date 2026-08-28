package com.titanium.maintenance.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.titanium.maintenance.web.interceptor.TenantInterceptor;
import com.titanium.maintenance.web.security.MaintenanceAuthenticationInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final TenantInterceptor tenantInterceptor;
    private final MaintenanceAuthenticationInterceptor authenticationInterceptor;

    public WebMvcConfig(
            TenantInterceptor tenantInterceptor,
            MaintenanceAuthenticationInterceptor authenticationInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/web/**", "/api/**");
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns(
                        "/api/v1/maintenance/cases/**",
                        "/web/v1/maintenance/cases/**",
                        "/api/v1/maintenance/configurations/**");
    }
}
