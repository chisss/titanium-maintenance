package com.titanium.maintenance.application.orchestration.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.titanium.maintenance.application.model.effect.LoanRepaymentFact;
import com.titanium.maintenance.application.model.effect.LoanRepaymentOrder;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort.PolicyLoanRepaymentPostingOrder;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort.PolicyLoanRepaymentPostingReceipt;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort.RepaymentCollectionOrder;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort.RepaymentCollectionReceipt;
import com.titanium.maintenance.port.policy.PolicyLoanPort;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentOrder;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentReceipt;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.port.policy.PolicyServicePort.PolicyFinancialSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款还款编排器（application/orchestration，g04-04 生效出口）。
 * <p>
 * 还款类保全案件生效的实质内容 = 跨保单/计费/支付三域的「还款三连写」：取数（财务快照取保单币种）
 * → 保单域记账（权威分配）→ 计费域冲销入账 → 支付域收款。本编排器只做取数与编排。
 * </p>
 * <p>
 * 🔴 <b>分配与余额一律取自保单域回执，本编排器不复算</b>：利息/本金拆分（先息后本、单利 365 分段计息）
 * 与还款后未偿余额的权威都在保单域贷款账户聚合——它持有该贷款的全量还款记录。计费域冲销与支付域收款的
 * 金额要素<b>全部取自回执</b>（而非请求载荷），保证三域口径逐位一致；复算即产生第二份公式，必然漂移。
 * </p>
 * <p>
 * 🔴 <b>repaymentId 幂等铁律</b>：还款业务号由本编排器按「租户 + 案件号」稳定派生
 * （{@code "RPAY" + sha256 前 28 位}，32 字符，满足保单域契约 {@code @Size(max=32)}）——三连写部分失败后
 * 重试，repaymentId 必须保持不变：保单域贷款账户聚合同键重试幂等放行并回显首次记账的分配（金额与还款
 * 日期以首次为准），计费域同键幂等返回既有入账流水，支付域按业务号回查既有收款单不重复建单；若重试换
 * 新号，保单域会追加第二笔还款，同一笔钱被记两遍、贷款余额被多冲减，属业务错账。金额与目标明细一致性
 * 由调用方请求哈希锁定，保单域另以「同键异额拒绝」兜底（串号防御）。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面由各 Adapter 承担</b>（不可达 502 / 业务码失败 / 回显不符），本编排器不兜底、
 * 不重试、不降级——任一环节失败即整体抛出，案件保持未生效，由调用方决定是否重试。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRepaymentOrchestrator {

    /**
     * 收款摘要文案（纯展示文案，非业务判断值，故为常量而非枚举；入库/跨域载荷中作业务描述使用时
     * 一律以本常量承载）。
     */
    private static final String COLLECTION_DESCRIPTION = "保单贷款还款收款";

    private final PolicyServicePort policyServicePort;
    private final PolicyLoanPort policyLoanPort;
    private final PolicyLoanRepaymentPostingPort policyLoanRepaymentPostingPort;
    private final LoanRepaymentCollectionPort loanRepaymentCollectionPort;

    /**
     * 执行还款三连写（同步编排，任一环节失败即抛异常失败关闭，绝不返回部分事实）。
     *
     * @param order 还款指令（目标明细与金额为案件冻结的申请要素；还款日期为本次执行时点，幂等重试时以
     *              保单域首次记账为准）
     * @return 还款事实（分配/余额/还款日期一律取自保单域回执，保证重试与首次一致）
     */
    public LoanRepaymentFact repay(LoanRepaymentOrder order) {
        PolicyFinancialSnapshot snapshot = policyServicePort.getPolicyFinancialSnapshot(
                order.policyId(), order.tenantId());

        String repaymentId = stableRepaymentId(order.tenantId(), order.maintenanceId());
        PolicyLoanRepaymentReceipt repayReceipt = policyLoanPort.repay(new PolicyLoanRepaymentOrder(
                repaymentId, order.policyId(), order.loanId(),
                order.repaymentAmount(), order.repaymentDate()));

        // 冲销与收款的金额/拆分/币种一律以保单域回执为准（幂等重试时回显首次记账的分配）
        PolicyLoanRepaymentPostingReceipt postingReceipt = policyLoanRepaymentPostingPort.postLoanRepayment(
                new PolicyLoanRepaymentPostingOrder(
                        repaymentId, repayReceipt.loanId(), repayReceipt.policyId(),
                        repayReceipt.repaymentAmount(), repayReceipt.interestPortion(),
                        repayReceipt.principalPortion(), snapshot.currency(),
                        order.operatorId(), order.tenantId()));
        RepaymentCollectionReceipt collectionReceipt = loanRepaymentCollectionPort.collect(
                new RepaymentCollectionOrder(
                        repaymentId, repayReceipt.policyId(), order.customerId(),
                        repayReceipt.repaymentAmount(), snapshot.currency(), COLLECTION_DESCRIPTION));

        return LoanRepaymentFact.of(repayReceipt, postingReceipt.postingId(), collectionReceipt.orderId());
    }

    /** 稳定还款业务号：租户 + 案件号派生（重试不变、案件级唯一、满足对端 32 字符上限）。 */
    static String stableRepaymentId(String tenantId, String maintenanceId) {
        return "RPAY" + sha256(tenantId + "\n" + maintenanceId).substring(0, 28);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }
}
