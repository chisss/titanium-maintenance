package com.titanium.maintenance.infrastructure.adapter.billing;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.request.loan.CreatePolicyLoanPostingRequest;
import com.titanium.billing.api.response.loan.PolicyLoanPostingResponse;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanPostingUnavailableException;
import com.titanium.maintenance.infrastructure.client.billing.PolicyLoanPostingClient;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款入账适配器（六边形架构 driven adapter，g04-03）。
 * <p>
 * {@link PolicyLoanPostingPort} 的基础设施实现，调用计费域 {@link PolicyLoanPostingClient}（Feign），
 * 将远程响应防腐翻译为领域侧类型化的 {@link PolicyLoanPostingReceipt}。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyLoanAdapter} 同范式，禁止以 0/空流水号兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_BILLING_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>入账失败</b>：计费域<b>正常应答</b>但业务码非成功或应答体无效 —— 抛
 * {@link PolicyLoanPostingUnavailableException}（{@code MAINTENANCE_LOAN_POSTING_UNAVAILABLE}），
 * 由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答贷款号与请求不符 —— 契约无效，同「入账失败」处置。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyLoanPostingAdapter implements PolicyLoanPostingPort {

    private final PolicyLoanPostingClient client;

    @Override
    public PolicyLoanPostingReceipt postLoanDisbursement(PolicyLoanPostingOrder order) {
        ApiResponse<PolicyLoanPostingResponse> response;
        try {
            response = client.post(new CreatePolicyLoanPostingRequest(
                    order.loanId(), order.policyId(), order.amount(), order.currency(),
                    order.createdBy(), order.tenantId()));
        } catch (RuntimeException exception) {
            log.warn("保单贷款入账失败(服务不可达), loanId={}, policyId={}, 原因={}",
                    order.loanId(), order.policyId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "计费域贷款入账调用失败(服务不可达), loanId=" + order.loanId(),
                    MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR, exception);
        }
        PolicyLoanPostingResponse data = requireSuccessfulResponse(response, order);
        validateEcho(order, data);
        return new PolicyLoanPostingReceipt(data.postingId(), data.loanId());
    }

    /** 计费域正常应答但业务码失败/缺数据 → 「入账失败」，与「不可达」严格区分。 */
    private PolicyLoanPostingResponse requireSuccessfulResponse(
            ApiResponse<PolicyLoanPostingResponse> response, PolicyLoanPostingOrder order) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyLoanPostingUnavailableException(
                    "保单贷款入账失败(下游业务码失败), loanId=" + order.loanId() + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：贷款号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(PolicyLoanPostingOrder order, PolicyLoanPostingResponse data) {
        if (!Objects.equals(order.loanId(), data.loanId())) {
            throw new PolicyLoanPostingUnavailableException(
                    "保单贷款入账失败(应答回显不一致), loanId=" + order.loanId());
        }
    }
}
