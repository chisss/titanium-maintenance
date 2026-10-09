package com.titanium.maintenance.infrastructure.adapter.billing;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.request.loan.CreatePolicyLoanRepaymentPostingRequest;
import com.titanium.billing.api.response.loan.PolicyLoanRepaymentPostingResponse;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanRepaymentPostingUnavailableException;
import com.titanium.maintenance.infrastructure.client.billing.PolicyLoanRepaymentPostingClient;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款还款入账适配器（六边形架构 driven adapter，g04-04）。
 * <p>
 * {@link PolicyLoanRepaymentPostingPort} 的基础设施实现，调用计费域 {@link PolicyLoanRepaymentPostingClient}
 * （Feign），将远程响应防腐翻译为领域侧类型化的 {@link PolicyLoanRepaymentPostingReceipt}。
 * </p>
 * <p>
 * 🔴 <b>拆分原样搬运</b>：请求中的利息/本金拆分取自保单域还款回执（唯一权威），本适配器只做字段透传，
 * 不重算、不四舍五入、不调整精度——计费域另以「利息 + 本金 == 还款总额」勾稽兜底。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyLoanPostingAdapter} 同范式，禁止以 0/空流水号兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_BILLING_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>入账失败</b>：计费域<b>正常应答</b>但业务码非成功或应答体无效 —— 抛
 * {@link PolicyLoanRepaymentPostingUnavailableException}
 * （{@code MAINTENANCE_LOAN_REPAYMENT_POSTING_UNAVAILABLE}），由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答还款号/贷款号与请求不符 —— 契约无效，同「入账失败」处置。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyLoanRepaymentPostingAdapter implements PolicyLoanRepaymentPostingPort {

    private final PolicyLoanRepaymentPostingClient client;

    @Override
    public PolicyLoanRepaymentPostingReceipt postLoanRepayment(PolicyLoanRepaymentPostingOrder order) {
        ApiResponse<PolicyLoanRepaymentPostingResponse> response;
        try {
            response = client.post(new CreatePolicyLoanRepaymentPostingRequest(
                    order.repaymentId(), order.loanId(), order.policyId(), order.amount(),
                    order.interestPortion(), order.principalPortion(), order.currency(),
                    order.createdBy(), order.tenantId()));
        } catch (RuntimeException exception) {
            log.warn("保单贷款还款入账失败(服务不可达), repaymentId={}, loanId={}, 原因={}",
                    order.repaymentId(), order.loanId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "计费域还款入账调用失败(服务不可达), repaymentId=" + order.repaymentId(),
                    MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR, exception);
        }
        PolicyLoanRepaymentPostingResponse data = requireSuccessfulResponse(response, order);
        validateEcho(order, data);
        return new PolicyLoanRepaymentPostingReceipt(data.postingId(), data.repaymentId());
    }

    /** 计费域正常应答但业务码失败/缺数据 → 「入账失败」，与「不可达」严格区分。 */
    private PolicyLoanRepaymentPostingResponse requireSuccessfulResponse(
            ApiResponse<PolicyLoanRepaymentPostingResponse> response, PolicyLoanRepaymentPostingOrder order) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyLoanRepaymentPostingUnavailableException(
                    "保单贷款还款入账失败(下游业务码失败), repaymentId=" + order.repaymentId() + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：还款号/贷款号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(PolicyLoanRepaymentPostingOrder order, PolicyLoanRepaymentPostingResponse data) {
        if (!Objects.equals(order.repaymentId(), data.repaymentId())
                || !Objects.equals(order.loanId(), data.loanId())) {
            throw new PolicyLoanRepaymentPostingUnavailableException(
                    "保单贷款还款入账失败(应答回显不一致), repaymentId=" + order.repaymentId());
        }
    }
}
