package com.titanium.maintenance.valueobject.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 保单补发单证请求回执（保单补发保全案件生效出口的产物，g04-09）。
 * <p>
 * 与 {@link MaintenanceLoanDisbursementEvidence}、{@link MaintenanceLoanRepaymentEvidence} 并列的第五种
 * 生效回执：补发不改保单状态与字段、不产生批单号、不推进保单版本，只向对端产出一份<b>单证请求事实</b>，
 * 故独立成出口，不得塞进保单回执模型（后者以「实际版本必须递增」「字段型回执必须含实际字段」为不变量）。
 * </p>
 * <p>
 * 🔴 <b>与批单出口（{@code EndorsementDocument}）的形态差异</b>：批单是案件生效的<b>旁路产物</b>（出站失败
 * 只令单证延后，不令生效失败），故由事件处理路径异步搬运；补发请求事实是本案<b>生效的实质内容</b>——案件的
 * 全部意义就是这份请求，出站未果则案件不得置为生效，故由生效编排同步出站、拿到投递确认后才落本回执。
 * </p>
 * <p>
 * 回执字段分两类：<b>请求侧</b>（案件级生效请求号）与<b>交付侧</b>（交给对端的补发请求号、补发指向的保单）。
 * 对端<b>尚未定义</b>补发件的业务号（渲染寄送归 G13），故不以对端业务号作回执要素——待 G13 定义后如需勾稽，
 * 按「新增列而非改语义」的方式扩展。
 * </p>
 */
public record MaintenancePolicyReissueEvidence(
        String        requestId,
        String        policyId,
        String        reissueRequestId,
        LocalDateTime requestedAt) {

    public MaintenancePolicyReissueEvidence {
        requestId = requireText("requestId", requestId);
        policyId = requireText("policyId", policyId);
        reissueRequestId = requireText("reissueRequestId", reissueRequestId);
        if (requestedAt == null) {
            throw invalid("requestedAt", "回执时间不能为空");
        }
    }

    /**
     * 回执版本：补发不推进保单版本，以生效请求号作为版本标识，保证与案件级请求证据同源可勾稽
     * （与 {@link MaintenanceLoanDisbursementEvidence#evidenceVersion()} 同规）。
     */
    public String evidenceVersion() {
        return requestId;
    }

    /**
     * 载荷幂等指纹：供工作流操作载荷摘要与重试去重使用。
     * <p>
     * 🔴 {@code requestedAt} <b>不参与</b>摘要——它是本域出站的时刻，投递确认丢失后原载荷重试会取到新时刻，
     * 纳入摘要将使同一笔补发在重试时得到不同指纹、幂等失效（与 {@link MaintenanceLoanDisbursementEvidence#contentHash()}
     * 同规）。交付侧字段（对端请求号、保单）与请求侧字段全部参与。
     * </p>
     */
    public String contentHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, requestId);
        append(canonical, policyId);
        append(canonical, reissueRequestId);
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
        return new MaintenanceValidationException("MaintenancePolicyReissueEvidence", field, message);
    }
}
