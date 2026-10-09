package com.titanium.maintenance.application.query;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.pii.PiiMasker;

/**
 * 对保全字段查询值执行目录指定的默认掩码。
 *
 * <p>🔴 <b>本类已退化为薄委托，不含任何格式实现</b>（G06 D-PII-5 ①）：9 种策略的格式已提级到
 * {@link PiiMasker}（metadata，纯函数）。保留本类与 {@code @Component} 是为了<b>不动既有注入点与用例</b>——
 * 故本类的方法签名与行为必须与提级前逐字一致。
 * </p>
 *
 * <p><b>为何要提级（而非两处各写一份）</b>：掩码是<b>取值级</b>跨域契约，同一字段在全仓任意出口必须得到
 * 同一形态。若 maintenance 与 customer（G06 新增出口）各持一份实现，新增出口时必然漂移，
 * 且漂移<b>静默</b>——两侧都能跑、都有测试、都「符合各自文档」。提级后由<b>构造</b>消除重复，
 * 不靠纪律约束。原 R-8「两处实现漂移」风险随之关闭。
 * </p>
 *
 * @see PiiMasker 格式的唯一实现（含各策略的保留位数与失败关闭判据）
 */
@Component
public class MaintenanceCaseFieldMasker {

    /**
     * 按目录声明的等级与策略掩码一个字段值。语义与判据详见 {@link PiiMasker#mask}。
     *
     * @param value                   原始值
     * @param sensitivity             目录声明的敏感级别
     * @param maskingPolicy           目录声明的掩码策略
     * @param sensitiveDetailsVisible 本请求是否已获授权查看明文
     * @return 掩码后的值
     */
    public String mask(
            String value,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            boolean sensitiveDetailsVisible) {
        return PiiMasker.mask(value, sensitivity, maskingPolicy, sensitiveDetailsVisible);
    }
}
