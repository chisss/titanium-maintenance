package com.titanium.maintenance.infrastructure.adapter.product;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.ProductLoanPolicyUnavailableException;
import com.titanium.maintenance.infrastructure.client.product.ProductLoanPolicyClient;
import com.titanium.maintenance.port.product.ProductLoanPolicyPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.loan.LoanPolicyQueryResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款策略查询适配器（六边形架构 driven adapter，g04-03）。
 * <p>
 * {@link ProductLoanPolicyPort} 的基础设施实现，调用产品域 {@link ProductLoanPolicyClient}（Feign），
 * 将远程响应防腐翻译为领域侧类型化的 {@link LoanPolicy}。
 * </p>
 * <p>
 * 🔴 <b>失败关闭三分面</b>（与 {@code PolicyFinancialAdapter} 同范式，禁止以默认比例/利率兜底）：
 * </p>
 * <ul>
 * <li><b>不可达</b>：调用<b>抛异常</b>（连接失败、超时、5xx、契约反序列化失败等）—— 一律抛
 * {@link MaintenanceRemoteCallException}（错误码 {@code MAINTENANCE_PRODUCT_REMOTE_ERROR}，Web 层
 * 映射 HTTP 502）；</li>
 * <li><b>取数失败</b>：产品域<b>正常应答</b>但业务码非成功（该时点无已发布贷款策略，下游失败关闭
 * 语义）或应答体无效 —— 抛 {@link ProductLoanPolicyUnavailableException}
 * （{@code MAINTENANCE_LOAN_POLICY_UNAVAILABLE}），由调用方按业务规则失败关闭；</li>
 * <li><b>回显不一致</b>：应答产品号与请求不符 —— 契约无效，同「取数失败」处置。租户隔离由
 * Feign 请求头透传 + 产品域查询校验保证（响应契约不含租户字段，无从回显校验）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLoanPolicyAdapter implements ProductLoanPolicyPort {

    private final ProductLoanPolicyClient client;

    @Override
    public LoanPolicy queryEffectiveLoanPolicy(String productId, LocalDateTime businessTime) {
        ApiResponse<LoanPolicyQueryResponse> response;
        try {
            response = client.queryEffectiveLoanPolicy(productId, businessTime);
        } catch (RuntimeException exception) {
            log.warn("保单贷款策略查询失败(服务不可达), productId={}, 原因={}", productId, exception.getMessage());
            throw new MaintenanceRemoteCallException(
                    "产品域贷款策略查询失败(服务不可达), productId=" + productId,
                    MaintenanceErrorCode.MAINTENANCE_PRODUCT_REMOTE_ERROR, exception);
        }
        LoanPolicyQueryResponse data = requireSuccessfulResponse(response, productId);
        validateEcho(productId, data);
        return new LoanPolicy(
                data.policyId(), data.productId(), data.loanableRatio(), data.interestRate(),
                data.policyContentHash());
    }

    /** 产品域正常应答但业务码失败/缺数据 → 「取数失败」，与「不可达」严格区分。 */
    private LoanPolicyQueryResponse requireSuccessfulResponse(
            ApiResponse<LoanPolicyQueryResponse> response, String productId) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "无响应";
            throw new ProductLoanPolicyUnavailableException(
                    "保单贷款策略查询失败(下游业务码失败), productId=" + productId + ", message=" + message);
        }
        return response.getData();
    }

    /** 应答回显校验：产品号与请求不符即契约无效，不做任何本地推断。 */
    private void validateEcho(String productId, LoanPolicyQueryResponse data) {
        if (!productId.equals(data.productId())) {
            throw new ProductLoanPolicyUnavailableException(
                    "保单贷款策略查询失败(应答回显不一致), productId=" + productId);
        }
    }
}
