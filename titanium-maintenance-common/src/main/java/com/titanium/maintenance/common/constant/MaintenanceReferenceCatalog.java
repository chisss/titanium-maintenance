package com.titanium.maintenance.common.constant;

import java.util.Set;

/**
 * 保全配置引用目录：本域自管的权限码与模板码白名单。
 * <p>
 * 🔴 <b>为什么权限/模板仍留在本域而非改问外部权威</b>（G09 批次，2026-10-09）：
 * 保全配置引用的<b>规则码</b>权威在 rule-engine（存在性 + 已激活 + {@code RuleSetType.MAINTENANCE}），
 * 已由 {@code RuleEngineConfigurationReferenceAdapter} 调其只读契约判定；
 * 而<b>权限码与模板码</b>全仓<b>不存在</b>可查询的跨域目录或模板契约
 * （已逐域检索，无权限目录 API、无模板查询 API）。
 * 若为此造一个「永远返回请求原样」的空壳权威，等于把「不校验」包装成「已校验」——
 * 那才是真正的静默放宽。故二者保留本域目录，取值与本目录的既有白名单逐字一致，行为不变。
 * </p>
 * <p>
 * 🔴 <b>取值即契约</b>：这两组编码会被写入已发布的保全配置，前端默认值亦取自其中
 * （{@code maintenance:item:operate} / {@code maintenance:item:view}）。
 * 增删条目会改变「哪些权限/模板可被引用」，属业务语义变更而非重构，须随需求评审。
 * </p>
 */
public final class MaintenanceReferenceCatalog {

    private MaintenanceReferenceCatalog() {
        // 纯目录常量，禁止实例化
    }

    /** 可被保全项访问规则引用的权限码（保全域自管）。 */
    public static final Set<String> PERMISSION_CODES = Set.of(
            "maintenance:item:operate",
            "maintenance:item:view",
            "maintenance:sensitive:view");

    /** 可被保全项输出规则引用的凭证/通知/归档模板码（保全域自管）。 */
    public static final Set<String> TEMPLATE_CODES = Set.of(
            "MAINTENANCE_VOUCHER",
            "MAINTENANCE_NOTICE",
            "MAINTENANCE_ARCHIVE");
}
