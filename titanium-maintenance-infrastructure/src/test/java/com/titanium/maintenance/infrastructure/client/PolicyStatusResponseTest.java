package com.titanium.maintenance.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.infrastructure.client.PolicyServiceClient.PolicyStatusResponse;

/**
 * 保单状态响应契约判定测试
 * <p>
 * 固定「保全域 ↔ 保单域」跨域状态码契约：保单域原生状态码为 EFFECTIVE/LAPSED/TERMINATED/EXPIRED
 * 等，本测试防止回退到历史缺陷（误判 ACTIVE、复效误纳入 TERMINATED/EXPIRED）。
 * </p>
 */
class PolicyStatusResponseTest {

    @Test
    @DisplayName("生效判定对齐 EFFECTIVE，不再认 ACTIVE")
    void isActiveShouldMatchEffective() {
        assertTrue(new PolicyStatusResponse("P1", "EFFECTIVE").isActive());
        assertFalse(new PolicyStatusResponse("P1", "ACTIVE").isActive());
        assertFalse(new PolicyStatusResponse("P1", "LAPSED").isActive());
    }

    @Test
    @DisplayName("可复效判定仅认 LAPSED，终态 TERMINATED/EXPIRED 不可复效")
    void isReinstatableShouldOnlyMatchLapsed() {
        assertTrue(new PolicyStatusResponse("P1", "LAPSED").isReinstatable());
        assertFalse(new PolicyStatusResponse("P1", "TERMINATED").isReinstatable());
        assertFalse(new PolicyStatusResponse("P1", "EXPIRED").isReinstatable());
        assertFalse(new PolicyStatusResponse("P1", "EFFECTIVE").isReinstatable());
    }

    @Test
    @DisplayName("空状态码安全返回 false，不抛异常")
    void shouldHandleNullStatusSafely() {
        assertFalse(new PolicyStatusResponse("P1", null).isActive());
        assertFalse(new PolicyStatusResponse("P1", null).isReinstatable());
    }
}
