package com.titanium.maintenance.infrastructure.adapter.payment;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.LoanRepaymentCollectionUnavailableException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.payment.LoanRepaymentCollectionClient;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.errorcode.PaymentErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.payment.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.payment.PaymentOrderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款还款收款适配器（六边形架构 driven adapter，g04-04）。
 * <p>
 * {@link LoanRepaymentCollectionPort} 的基础设施实现，调用支付域 {@link LoanRepaymentCollectionClient}
 * （Feign），将远程响应防腐翻译为领域侧类型化的 {@link RepaymentCollectionReceipt}。
 * </p>
 * <p>
 * 🔴 <b>先查后建幂等</b>（与 {@code PaymentPremiumCollectionAdapter} 同范式）：以还款业务号作为支付单业务号，
 * 先按该号回查；已存在即原样返回既有收款单，不重复建单。响应丢失后的重试因此收敛到同一笔收款单，
 * 不会产生第二张单（双记收款）。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyLoanAdapter} 同范式，禁止以 0/空指令号兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_PAYMENT_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>收款失败</b>：支付域<b>正常应答</b>但业务码非成功或应答体无效（回查时「单不存在」除外，
 * 那是「尚未创建」的正常分支）—— 抛 {@link LoanRepaymentCollectionUnavailableException}
 * （{@code MAINTENANCE_LOAN_REPAYMENT_COLLECTION_UNAVAILABLE}），由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答单号/保单号/金额/币种与请求不符 —— 契约无效，同「收款失败」处置。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanRepaymentCollectionAdapter implements LoanRepaymentCollectionPort {

    /**
     * 支付类型 code（对端权威：{@code com.titanium.payment.common.enums.PaymentType.LOAN_REPAYMENT}）。
     * <p>
     * 🔴 <b>必须显式传入</b>：支付域 {@code PaymentWebMapper.toPaymentType} 对未传或不可识别的 code
     * <b>静默回落 {@code PREMIUM_COLLECT}</b>，漏传会把贷款还款记成保费收取（错账且无报错）。
     * 跨域契约以字符串 code 承载，故此处声明常量而非依赖对端枚举类型。
     * </p>
     */
    private static final String PAYMENT_TYPE_LOAN_REPAYMENT = "LOAN_REPAYMENT";

    /**
     * 业务类型 code（对端权威：{@code com.titanium.metadata.enums.BusinessDomainType.MAINTENANCE}）。
     * <p>
     * 不传将回落 {@code POLICY}——还款的业务单据是保全案件而非保单本身，须显式声明，否则跨域回写
     * 会按错误的业务域寻址。
     * </p>
     */
    private static final String BUSINESS_TYPE_MAINTENANCE = "MAINTENANCE";

    private final LoanRepaymentCollectionClient client;

    @Override
    public RepaymentCollectionReceipt collect(RepaymentCollectionOrder order) {
        try {
            ApiResponse<PaymentOrderResponse> existing = client.get(order.repaymentId());
            if (existing != null && existing.isSuccess() && existing.getData() != null) {
                // 🔴 回查命中分支传 null（= 不做「创建结果与请求一致」比对），与 toReceipt 的契约声明一致。
                // 回查走支付域读模型，其 PaymentOrderView 不落 policy_id 列 ⇒ 该字段必为 null，带 order 比对
                // 会把「读模型能力缺列」误判成「契约不一致」，使幂等重放路径（响应丢失后的重试）恒 503。
                // 必填字段完整性校验仍在 toReceipt 内执行，不因传 null 而放松。
                return toReceipt(existing.getData(), null);
            }
            if (!paymentOrderMissing(existing)) {
                throw unavailable("还款收款单回查失败: " + responseDetail(existing));
            }
            return toReceipt(requireSuccess(client.create(buildBody(order))), order);
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("创建 Payment 还款收款单失败(服务不可达), repaymentId={}, policyId={}, 原因={}",
                    order.repaymentId(), order.policyId(), exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "支付域还款收款调用失败(服务不可达), repaymentId=" + order.repaymentId(),
                    MaintenanceErrorCode.MAINTENANCE_PAYMENT_REMOTE_ERROR, exception);
        }
    }

    /** 收款指令 → 支付单创建请求（业务号即还款业务号，作幂等键）。 */
    private CreatePaymentOrderRequest buildBody(RepaymentCollectionOrder order) {
        CreatePaymentOrderRequest body = new CreatePaymentOrderRequest();
        body.setOrderId(order.repaymentId());
        body.setPolicyId(order.policyId());
        body.setBusinessId(order.repaymentId());
        body.setPaymentType(PAYMENT_TYPE_LOAN_REPAYMENT);
        body.setBusinessType(BUSINESS_TYPE_MAINTENANCE);
        body.setCustomerId(order.customerId());
        body.setAmount(order.amount());
        body.setCurrency(order.currency());
        body.setDescription(order.description());
        return body;
    }

    /** 支付域正常应答但业务码失败/缺数据 → 「收款失败」，与「不可达」严格区分。 */
    private PaymentOrderResponse requireSuccess(ApiResponse<PaymentOrderResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw unavailable("还款收款单创建失败: " + responseDetail(response));
        }
        return response.getData();
    }

    /** 回查应答为「支付单不存在」——即本笔收款尚未创建，属正常分支（非失败）。 */
    private boolean paymentOrderMissing(ApiResponse<PaymentOrderResponse> response) {
        return response != null
                && PaymentErrorCode.PAYMENT_ORDER_NOT_EXIST.getCode().equals(response.getCode());
    }

    private String responseDetail(ApiResponse<PaymentOrderResponse> response) {
        return response == null ? "空响应" : response.getCode() + ":" + response.getMessage();
    }

    /**
     * 应答回执翻译 + 回显校验。
     *
     * @param request 非空时逐项比对「创建结果与请求一致」；回查既有单时传 {@code null}（无请求要素可比对），
     *                但契约必填字段的完整性校验始终执行
     */
    private RepaymentCollectionReceipt toReceipt(PaymentOrderResponse response, RepaymentCollectionOrder request) {
        if (response.getOrderId() == null || response.getStatus() == null
                || response.getAmount() == null || response.getCurrency() == null) {
            throw unavailable("还款收款单应答字段不完整");
        }
        if (request != null
                && (!Objects.equals(request.repaymentId(), response.getOrderId())
                        || !Objects.equals(request.policyId(), response.getPolicyId())
                        || request.amount().compareTo(response.getAmount()) != 0
                        || !request.currency().equalsIgnoreCase(response.getCurrency()))) {
            throw unavailable("还款收款单创建结果与请求不一致, repaymentId=" + request.repaymentId());
        }
        return new RepaymentCollectionReceipt(
                response.getOrderId(), response.getPolicyId(), response.getStatus(),
                response.getTransactionId(), response.getPaymentDate());
    }

    private LoanRepaymentCollectionUnavailableException unavailable(String message) {
        return new LoanRepaymentCollectionUnavailableException(message);
    }
}
