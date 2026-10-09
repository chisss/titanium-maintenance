package com.titanium.maintenance.infrastructure.adapter.payment;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanPayoutUnavailableException;
import com.titanium.maintenance.infrastructure.client.payment.LoanDisbursementPayoutClient;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.loan.CreateLoanDisbursementRequest;
import com.titanium.payment.api.response.loan.CreateLoanDisbursementResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款放款出账适配器（六边形架构 driven adapter，g04-03）。
 * <p>
 * {@link LoanDisbursementPayoutPort} 的基础设施实现，调用支付域 {@link LoanDisbursementPayoutClient}
 * （Feign），将远程响应防腐翻译为领域侧类型化的 {@link LoanPayoutReceipt}。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyLoanAdapter} 同范式，禁止以 0/空指令号兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_PAYMENT_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>出账失败</b>：支付域<b>正常应答</b>但业务码非成功或应答体无效 —— 抛
 * {@link PolicyLoanPayoutUnavailableException}（{@code MAINTENANCE_LOAN_PAYOUT_UNAVAILABLE}），
 * 由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答贷款号/保单号与请求不符 —— 契约无效，同「出账失败」处置。租户与操作人由
 * {@code RequestContextFeignInterceptor} 请求头透传（响应契约不含租户字段，无从回显校验）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDisbursementPayoutAdapter implements LoanDisbursementPayoutPort {

    private final LoanDisbursementPayoutClient client;

    @Override
    public LoanPayoutReceipt payOut(LoanPayoutOrder order) {
        ApiResponse<CreateLoanDisbursementResponse> response;
        try {
            CreateLoanDisbursementRequest request = new CreateLoanDisbursementRequest();
            request.setLoanId(order.loanId());
            request.setPolicyId(order.policyId());
            request.setAmount(order.amount());
            request.setCurrency(order.currency());
            request.setCustomerId(order.customerId());
            response = client.createLoanDisbursement(request);
        } catch (RuntimeException exception) {
            log.warn("保单贷款出账失败(服务不可达), loanId={}, policyId={}, 原因={}",
                    order.loanId(), order.policyId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "支付域贷款出账调用失败(服务不可达), loanId=" + order.loanId(),
                    MaintenanceErrorCode.MAINTENANCE_PAYMENT_REMOTE_ERROR, exception);
        }
        CreateLoanDisbursementResponse data = requireSuccessfulResponse(response, order);
        validateEcho(order, data);
        return new LoanPayoutReceipt(data.getLoanDisbursementId(), data.getLoanId());
    }

    /** 支付域正常应答但业务码失败/缺数据 → 「出账失败」，与「不可达」严格区分。 */
    private CreateLoanDisbursementResponse requireSuccessfulResponse(
            ApiResponse<CreateLoanDisbursementResponse> response, LoanPayoutOrder order) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyLoanPayoutUnavailableException(
                    "保单贷款出账失败(下游业务码失败), loanId=" + order.loanId() + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：贷款号/保单号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(LoanPayoutOrder order, CreateLoanDisbursementResponse data) {
        if (!Objects.equals(order.loanId(), data.getLoanId())
                || !Objects.equals(order.policyId(), data.getPolicyId())) {
            throw new PolicyLoanPayoutUnavailableException(
                    "保单贷款出账失败(应答回显不一致), loanId=" + order.loanId());
        }
    }
}
