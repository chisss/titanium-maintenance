package com.titanium.maintenance.port.document;

import com.titanium.maintenance.valueobject.reissue.PolicyReissueDocument;

/**
 * 保单补发单证请求端口（出口侧 Port，对端域 document）
 * <p>
 * 保单补发案件生效时，须向 document 域交付一份<b>补发单证请求事实</b>，由对端渲染补发件、签章并寄送
 * （渲染寄送归 G13，见 G04 缺口文档边界表）。本端口表达「保全域需要一份补发件」这一领域能力，具体实现
 * （出站机制、载荷编码）由 infrastructure 的 {@code infrastructure/adapter/document/PolicyReissueDocumentAdapter}
 * 承担——按六边形架构，Port 是内核边界概念，Adapter 属基础设施。
 * </p>
 * <p>
 * 🔴 <b>与 {@link EndorsementDocumentPort} 的形态差异（同为 document 域出站，机制相反）</b>：批单是案件生效的
 * <b>旁路产物</b>——出站失败只令单证延后，不该把 document 域可用性绑进「案件生效」这一关键事务，故由事件
 * 处理路径异步搬运；补发请求事实是本案<b>生效的实质内容</b>——案件的唯一目的就是这份请求，出站未果则案件
 * 不得置为生效，故由生效编排<b>同步调用本端口</b>，拿到投递确认后才落回执（与投资账户转换出口的
 * fail-closed 取向一致）。判据是「该产物是否为案件生效的实质内容」，不是「是否同为 document 域出站」。
 * </p>
 */
public interface PolicyReissueDocumentPort {

    /**
     * 交付补发单证请求事实，由 document 域渲染、签章并寄送补发件。
     *
     * @param document 补发单证请求事实（案件已冻结的权威要素）
     * @return 交付侧补发请求号（出站幂等键，与载荷中的 {@code reissueRequestId} 同源）
     */
    String requestPolicyReissue(PolicyReissueDocument document);
}
