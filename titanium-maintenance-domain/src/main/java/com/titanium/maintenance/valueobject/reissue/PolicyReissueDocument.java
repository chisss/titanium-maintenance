package com.titanium.maintenance.valueobject.reissue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单补发单证请求事实（保单补发保全案件生效出口的产物，g04-09）。
 * <p>
 * 与 {@code EndorsementDocument}（批单，案件<b>派生</b>的文书）不同，补发指向的是<b>保单自身凭证</b>的替换
 * 关系：挂失后原单证作废、补发件取而代之。故本事实只声明「哪张保单、因哪一案、要一份补发件」，不描述补发件
 * 的版式与内容。
 * </p>
 * <p>
 * 🔴 <b>边界：本域只产出请求事实，不渲染、不寄送</b>。补发件的渲染、签章与寄送归 {@code G13}（见 G04 缺口
 * 文档边界表）。本记录作为跨域事实交付对端，本域不依赖其产物是否已生成——但<b>请求事实本身必须已离开本域</b>
 * 才允许案件置为生效（出站失败即案件生效失败，见出口编排）。
 * </p>
 * <p>
 * 🔴 <b>无「受限」前置</b>：本事实刻意不声明「补发须先挂失」这类约束。挂失的业务边界尚未定界（R-5 开放
 * 问题），未定界前只落「登记 + 受限标记」骨架、不落具体冻结规则；把未定界的约束写进事实，事后无从区分
 * 「当时有意为之」与「当时遗漏」。
 * </p>
 */
public record PolicyReissueDocument(
        String        maintenanceId,
        String        policyId,
        String        maintenanceTypeCode,
        String        reissueRequestId,
        LocalDateTime requestedAt,
        String        tenantId) {

    public PolicyReissueDocument {
        maintenanceId = requireText("maintenanceId", maintenanceId);
        policyId = requireText("policyId", policyId);
        maintenanceTypeCode = requireText("maintenanceTypeCode", maintenanceTypeCode);
        reissueRequestId = requireText("reissueRequestId", reissueRequestId);
        tenantId = requireText("tenantId", tenantId);
        if (requestedAt == null) {
            throw invalid("requestedAt", "请求时间不能为空");
        }
    }

    /**
     * 稳定派生补发请求号（{@code "RISS" + sha256 前 28 位}，32 字符）。
     * <p>
     * 对同一 (租户, 案件) 恒定，故出站重试、事件重放与调用方重发都收敛到同一个请求号，对端据以去重——
     * 与 {@code LoanRepaymentOrchestrator} 的还款业务号同规（稳定派生而非流水号，流水号在重试时会变，
     * 幂等即失效）。
     * </p>
     */
    public static String deriveReissueRequestId(String tenantId, String maintenanceId) {
        String seed = (tenantId == null ? "" : tenantId) + "\n" + (maintenanceId == null ? "" : maintenanceId);
        return "RISS" + sha256(seed).substring(0, 28);
    }

    /**
     * 请求载荷摘要：供冻结请求的幂等比对使用。
     * <p>
     * 🔴 {@code requestedAt} <b>不参与</b>摘要——它是本次出站的时刻，重试会取到新时刻，纳入摘要将使同一案件
     * 的冻结请求与重算载荷永不相等、幂等闸口恒拒（与各出口证据的 {@code contentHash()} 同规）。其余字段
     * 全部由案件事实唯一确定，参与摘要。
     * </p>
     */
    public String requestHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, maintenanceId);
        append(canonical, policyId);
        append(canonical, maintenanceTypeCode);
        append(canonical, reissueRequestId);
        append(canonical, tenantId);
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value == null ? -1 : value.length()).append(':');
        if (value != null) {
            target.append(value);
        }
        target.append('\n');
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String field, String message) {
        return new MaintenanceValidationException("PolicyReissueDocument", field, message);
    }
}
