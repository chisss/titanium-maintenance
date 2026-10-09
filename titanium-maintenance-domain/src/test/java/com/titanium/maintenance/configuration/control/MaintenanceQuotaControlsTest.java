package com.titanium.maintenance.configuration.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 保全项额度控制引用键测试（g04-01）
 * <p>
 * 覆盖：空控制工厂、引用键空白归一化、isConfigured 判定。
 * </p>
 */
class MaintenanceQuotaControlsTest {

    @Test
    @DisplayName("none() 工厂：三个引用键均为 null，未配置")
    void shouldReturnEmptyControls() {
        MaintenanceQuotaControls controls = MaintenanceQuotaControls.none();
        assertNull(controls.loanPolicyReferenceCode());
        assertNull(controls.withdrawalPolicyReferenceCode());
        assertNull(controls.sumInsuredBoundReferenceCode());
        assertFalse(controls.isConfigured());
    }

    @Test
    @DisplayName("任一引用键非空即已配置")
    void shouldBeConfiguredWhenAnyReferencePresent() {
        assertTrue(new MaintenanceQuotaControls("LOAN-POLICY-01", null, null).isConfigured());
        assertTrue(new MaintenanceQuotaControls(null, "WD-POLICY-01", null).isConfigured());
        assertTrue(new MaintenanceQuotaControls(null, null, "SI-BOUND-01").isConfigured());
    }

    @Test
    @DisplayName("引用键空白归一化：全空白视为未配置")
    void shouldNormalizeBlankReferences() {
        MaintenanceQuotaControls controls = new MaintenanceQuotaControls("  ", "", " SI-BOUND-01 ");
        assertNull(controls.loanPolicyReferenceCode());
        assertNull(controls.withdrawalPolicyReferenceCode());
        assertEquals("SI-BOUND-01", controls.sumInsuredBoundReferenceCode());
        assertTrue(controls.isConfigured());
    }
}
