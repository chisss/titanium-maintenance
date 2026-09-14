package com.titanium.maintenance.valueobject.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保全凭证出具事实。
 * <p>
 * 「出具凭证」是保全生效完成后的收口动作：按冻结配置的输出规则（{@code MaintenanceOutputRule.voucherTemplateCode}）
 * 形成并送达客户可留存的批单凭证。凭证模板编码由**冻结配置**决定，调用方不得自报，与核保证据「不接受调用方
 * 自报结论」同一红线；凭证编号来自出具动作本身，须与 Policy 权威回执的批单号可勾稽（由应用层组装时保证）。
 * </p>
 *
 * @param templateCode 凭证模板编码（取自保全项冻结配置的输出规则）
 * @param voucherNo    凭证编号（对外可检索的凭证标识）
 * @param issuedAt     出具时间
 * @param issuedBy     出具人
 */
public record MaintenanceDocumentEvidence(
        String templateCode,
        String voucherNo,
        LocalDateTime issuedAt,
        String issuedBy) {

    public MaintenanceDocumentEvidence {
        templateCode = requireText("templateCode", templateCode);
        voucherNo = requireText("voucherNo", voucherNo);
        issuedBy = requireText("issuedBy", issuedBy);
        if (issuedAt == null) {
            throw invalid("issuedAt", "凭证出具时间不能为空");
        }
    }

    /**
     * 生成不含服务端时间的稳定凭证载荷摘要，支持响应丢失后的原载荷重试。
     * <p>
     * {@code issuedAt} 由服务端在组装时取当前时间，若参与摘要则同一业务事实无法重放出相同指纹，
     * 幂等判定会退化为「同操作号不同载荷」而误拒重试。
     * </p>
     */
    public String contentHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, templateCode);
            update(digest, voucherNo);
            update(digest, issuedBy);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK不支持SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException invalid(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceDocumentEvidence", fieldName, message);
    }
}
