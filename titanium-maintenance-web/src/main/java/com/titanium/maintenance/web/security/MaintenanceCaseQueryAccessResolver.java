package com.titanium.maintenance.web.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从受信认证主体解析案件敏感字段查看能力；匿名或未授权请求默认脱敏。 */
@Component
public class MaintenanceCaseQueryAccessResolver {

    private static final String SENSITIVE_VIEW_PERMISSION = "maintenance:sensitive:view";

    public boolean sensitiveDetailsVisible() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> SENSITIVE_VIEW_PERMISSION.equals(authority.getAuthority()));
    }
}
