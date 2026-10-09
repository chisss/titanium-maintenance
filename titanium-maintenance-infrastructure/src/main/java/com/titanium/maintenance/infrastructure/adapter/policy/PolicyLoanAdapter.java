package com.titanium.maintenance.infrastructure.adapter.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanRepaymentUnavailableException;
import com.titanium.maintenance.common.exception.PolicyLoanUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyLoanClient;
import com.titanium.maintenance.port.policy.PolicyLoanPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款放款与还款适配器（六边形架构 driven adapter，g04-03 放款 / g04-04 还款）。
 * <p>
 * {@link PolicyLoanPort} 的基础设施实现，调用保单域 {@link PolicyLoanClient}（Feign），
 * 将远程响应防腐翻译为领域侧类型化的 {@link PolicyLoanDisbursementReceipt}（放款）与
 * {@link PolicyLoanRepaymentReceipt}（还款）。还款回执的利息/本金拆分与还款后余额<b>原样搬运</b>，
 * 不做任何复算或精度调整（权威在对端聚合，复算即产生第二份公式）。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyFinancialAdapter} 同范式，禁止以 0/空回执兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_POLICY_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>放款失败</b>：保单域<b>正常应答</b>但业务码非成功（保单不存在/未生效/要素非法/重复放款，
 * 下游失败关闭语义）或应答体无效 —— 抛 {@link PolicyLoanUnavailableException}
 * （{@code MAINTENANCE_LOAN_DISBURSEMENT_UNAVAILABLE}），由调用方按业务规则失败关闭；</li>
 * <li><b>还款失败</b>：同上，但抛 {@link PolicyLoanRepaymentUnavailableException}
 * （{@code MAINTENANCE_LOAN_REPAYMENT_UNAVAILABLE}）——贷款不存在/还款额超欠款总额/要素非法
 * 均属对端业务拒绝，与不可达严格区分；</li>
 * <li><b>回显不一致</b>：应答业务号/保单号与请求不符 —— 契约无效，同各自的「失败」处置。租户隔离由
 * Feign 请求头透传 + 保单域聚合校验保证（响应契约不含租户字段，无从回显校验）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyLoanAdapter implements PolicyLoanPort {

    private final PolicyLoanClient client;

    @Override
    public PolicyLoanDisbursementReceipt disburse(PolicyLoanDisbursementOrder order) {
        ApiResponse<DisbursePolicyLoanResponse> response;
        try {
            response = client.disburse(new DisbursePolicyLoanRequest(
                    order.loanId(), order.policyId(), order.loanAmount(), order.interestRate(), order.loanDate()));
        } catch (RuntimeException exception) {
            log.warn("保单贷款放款失败(服务不可达), loanId={}, policyId={}, 原因={}",
                    order.loanId(), order.policyId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "保单域贷款放款调用失败(服务不可达), loanId=" + order.loanId(),
                    MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR, exception);
        }
        DisbursePolicyLoanResponse data = requireSuccessfulResponse(response, order);
        validateEcho(order, data);
        return new PolicyLoanDisbursementReceipt(
                data.loanId(), data.policyId(), data.loanAmount(), data.interestRate(), data.loanDate());
    }

    @Override
    public PolicyLoanRepaymentReceipt repay(PolicyLoanRepaymentOrder order) {
        ApiResponse<RepayPolicyLoanResponse> response;
        try {
            response = client.repay(new RepayPolicyLoanRequest(
                    order.repaymentId(), order.policyId(), order.loanId(),
                    order.repaymentAmount(), order.repaymentDate()));
        } catch (RuntimeException exception) {
            log.warn("保单贷款还款失败(服务不可达), repaymentId={}, loanId={}, 原因={}",
                    order.repaymentId(), order.loanId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "保单域贷款还款调用失败(服务不可达), repaymentId=" + order.repaymentId(),
                    MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR, exception);
        }
        RepayPolicyLoanResponse data = requireSuccessfulResponse(response, order);
        validateEcho(order, data);
        return new PolicyLoanRepaymentReceipt(
                data.repaymentId(), data.policyId(), data.loanId(), data.repaymentAmount(),
                data.interestPortion(), data.principalPortion(), data.accruedInterest(),
                data.outstandingPrincipalAfter(), data.outstandingInterestAfter(),
                data.settled(), data.repaymentDate());
    }

    /** 保单域正常应答但业务码失败/缺数据 → 「放款失败」，与「不可达」严格区分。 */
    private DisbursePolicyLoanResponse requireSuccessfulResponse(
            ApiResponse<DisbursePolicyLoanResponse> response, PolicyLoanDisbursementOrder order) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyLoanUnavailableException(
                    "保单贷款放款失败(下游业务码失败), loanId=" + order.loanId() + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：贷款号/保单号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(PolicyLoanDisbursementOrder order, DisbursePolicyLoanResponse data) {
        if (!Objects.equals(order.loanId(), data.loanId()) || !Objects.equals(order.policyId(), data.policyId())) {
            throw new PolicyLoanUnavailableException(
                    "保单贷款放款失败(应答回显不一致), loanId=" + order.loanId());
        }
    }

    /** 保单域正常应答但业务码失败/缺数据 → 「还款失败」，与「不可达」严格区分。 */
    private RepayPolicyLoanResponse requireSuccessfulResponse(
            ApiResponse<RepayPolicyLoanResponse> response, PolicyLoanRepaymentOrder order) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new PolicyLoanRepaymentUnavailableException(
                    "保单贷款还款失败(下游业务码失败), repaymentId=" + order.repaymentId() + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：还款号/保单号/贷款号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(PolicyLoanRepaymentOrder order, RepayPolicyLoanResponse data) {
        if (!Objects.equals(order.repaymentId(), data.repaymentId())
                || !Objects.equals(order.policyId(), data.policyId())
                || !Objects.equals(order.loanId(), data.loanId())) {
            throw new PolicyLoanRepaymentUnavailableException(
                    "保单贷款还款失败(应答回显不一致), repaymentId=" + order.repaymentId());
        }
    }
}
