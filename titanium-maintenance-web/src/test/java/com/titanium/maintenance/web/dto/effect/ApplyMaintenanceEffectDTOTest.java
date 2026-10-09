package com.titanium.maintenance.web.dto.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 生效请求 DTO 测试（g04-03 贷款接线）。
 * <p>
 * 核心断言：① 贷款申请金额字段可经 JSON 反序列化（web→application 翻译链的入口契约不丢）；
 * ② 金额字段的 {@code @NotNull}/{@code @DecimalMin} 校验注解在位——注解是 {@code @Valid} 链上
 * 唯一拦「空/非正金额」的牙，被误删会静默放行 null 直穿应用层（应用层紧凑构造器仍会拒，但错误
 * 语义从 400 参数校验退化为业务异常）。
 * </p>
 */
class ApplyMaintenanceEffectDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadLoanDisbursementAmount() throws Exception {
        ApplyMaintenanceEffectDTO request = objectMapper.readValue(
                """
                        {"operationId":"operation-1","loanDisbursement":{"loanAmount":5000}}
                        """,
                ApplyMaintenanceEffectDTO.class);

        assertEquals("operation-1", request.operationId());
        assertNull(request.investmentSwitch(), "贷款案件不携带账户转换参数");
        assertEquals(new BigDecimal("5000"), request.loanDisbursement().loanAmount());
    }

    @Test
    void shouldKeepLoanAmountValidationAnnotations() throws Exception {
        // JSR-303 注解 @Target 不含 RECORD_COMPONENT：写在 record 组件上的注解传播到字段，
        // 反射须从字段读取（组件反射 getAnnotation 恒为 null）。
        var field = ApplyMaintenanceEffectDTO.LoanDisbursementDTO.class
                .getDeclaredField("loanAmount");

        Set<String> annotations = Arrays.stream(field.getAnnotations())
                .map(Annotation::annotationType)
                .map(Class::getName)
                .collect(Collectors.toSet());
        assertNotNull(field.getAnnotation(NotNull.class),
                "申请贷款金额必须带 @NotNull（防止 null 直穿应用层）");
        assertNotNull(field.getAnnotation(DecimalMin.class),
                "申请贷款金额必须带 @DecimalMin（防止 0/负数直穿应用层）");
        assertAnnotationsSolelyFromJakartaValidation(annotations);
    }

    @Test
    void shouldReadLoanRepaymentParameters() throws Exception {
        ApplyMaintenanceEffectDTO request = objectMapper.readValue(
                """
                        {"operationId":"operation-2","loanRepayment":{"loanId":"LOAN001","repaymentAmount":911.10}}
                        """,
                ApplyMaintenanceEffectDTO.class);

        assertNull(request.investmentSwitch(), "还款案件不携带账户转换参数");
        assertNull(request.loanDisbursement(), "还款案件不携带放款参数");
        assertEquals("LOAN001", request.loanRepayment().loanId());
        assertEquals(new BigDecimal("911.10"), request.loanRepayment().repaymentAmount());
    }

    @Test
    void shouldKeepLoanRepaymentValidationAnnotations() throws Exception {
        var loanIdField = ApplyMaintenanceEffectDTO.LoanRepaymentDTO.class.getDeclaredField("loanId");
        var amountField = ApplyMaintenanceEffectDTO.LoanRepaymentDTO.class.getDeclaredField("repaymentAmount");

        assertNotNull(loanIdField.getAnnotation(NotBlank.class),
                "目标贷款明细业务号必须带 @NotBlank（防止空串直穿应用层）");
        assertNotNull(loanIdField.getAnnotation(Size.class),
                "目标贷款明细业务号必须带 @Size（保单域贷款号契约上限32，超长即被下游拒绝）");
        assertNotNull(amountField.getAnnotation(NotNull.class),
                "还款金额必须带 @NotNull（防止 null 直穿应用层）");
        assertNotNull(amountField.getAnnotation(DecimalMin.class),
                "还款金额必须带 @DecimalMin（防止 0/负数直穿应用层）");
    }

    /** 校验注解必须来自 jakarta.validation（JSR-303 链），不得是自定义注解或其它命名空间。 */
    private void assertAnnotationsSolelyFromJakartaValidation(Set<String> annotations) {
        for (String annotation : annotations) {
            if (!annotation.startsWith("jakarta.validation")) {
                throw new AssertionError("loanAmount 出现非 JSR-303 注解: " + annotation);
            }
        }
    }
}
