package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCapabilityEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldCatalogEvidence;
import com.titanium.maintenance.port.policy.PolicyFieldCatalogPort.PolicyFieldDescriptorEvidence;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCapabilityResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldDescriptorResponse;

/**
 * Policy 字段目录的**跨域契约守卫**（防腐层两侧逐位对齐）。
 *
 * <p>上游 {@link PolicyFieldDescriptorResponse}（policy-api 的 Feign 契约）与本域
 * {@link PolicyFieldDescriptorEvidence}（maintenance 侧防腐证据）互相以**位置**构造（适配器逐位传参），
 * 但 record 加/删/改名/换序在**消费侧漏改时不会编译失败**——只是该字段永远读不到（静默漂移）。
 * 本测试把它变成构建期失败。
 *
 * <p>为何落在 infrastructure 而非 domain：本域 domain 层不得依赖 policy-api（防腐职责在 infrastructure），
 * 守卫必须能同时看到两侧类型。
 */
class PolicyFieldCatalogContractGuardTest {

    /**
     * 两侧分量数。🔴 计数断言独立存在——若反射取不到分量（规则静默失效），下面所有逐位断言都会
     * **空转通过**（空集合恒真）。契约变更时必须显式改这里，改动本身就是「已知晓跨域影响」的证据。
     */
    private static final int EXPECTED_COMPONENT_COUNT = 11;

    /**
     * 目录级（版本头 + 内容）两侧分量数。与描述符级同理：适配器逐位构造，
     * 上游给目录级加位（如 g07-04 的 {@code fallback}）而本域漏加时**编译不会失败**，
     * 只是该字段永远读不到。契约变更时必须显式改这里。
     */
    private static final int EXPECTED_CATALOG_COMPONENT_COUNT = 8;

    /**
     * 有意镜像的分量白名单：两侧类型**故意不同**（上游是 Feign Response，本域是防腐 Evidence），
     * 其余分量要求类型逐字相同。白名单条目自身也受守护——类型对不上（含两侧改成同一类型）即红，
     * 不允许过期条目存活。
     */
    private static final Map<String, Mirror> MIRRORED_COMPONENTS = Map.of(
            "capability",
            new Mirror(PolicyFieldCapabilityResponse.class, PolicyFieldCapabilityEvidence.class));

    /** 某个分量的有意镜像声明：上游类型 ↔ 本域类型。 */
    private record Mirror(Class<?> upstream, Class<?> local) {
    }

    @Test
    void descriptorsMustAlignComponentByComponent() {
        List<RecordComponent> upstream = componentsOf(PolicyFieldDescriptorResponse.class);
        List<RecordComponent> local = componentsOf(PolicyFieldDescriptorEvidence.class);

        // ① 计数断言（防反射规则静默失效后逐位断言空转）。
        assertEquals(EXPECTED_COMPONENT_COUNT, upstream.size(),
                "上游 PolicyFieldDescriptorResponse 分量数已变——跨域契约变更，本域必须同批对齐");
        assertEquals(EXPECTED_COMPONENT_COUNT, local.size(),
                "本域 PolicyFieldDescriptorEvidence 分量数与上游不再对齐");

        // ② 名称 + 顺序：适配器以位置构造，换序不是编译错误，是静默读错字段。
        assertEquals(namesOf(upstream), namesOf(local), "两侧分量名或顺序不一致（跨域契约漂移）");

        // ③ 逐位类型：非镜像分量必须逐字相同；镜像分量按其声明的类型对核验。
        for (int index = 0; index < upstream.size(); index++) {
            RecordComponent upstreamComponent = upstream.get(index);
            RecordComponent localComponent = local.get(index);
            Mirror mirror = MIRRORED_COMPONENTS.get(upstreamComponent.getName());
            if (mirror == null) {
                assertEquals(upstreamComponent.getType(), localComponent.getType(),
                        "分量 " + upstreamComponent.getName() + " 两侧类型不一致");
            } else {
                assertEquals(mirror.upstream(), upstreamComponent.getType(),
                        "镜像分量 " + upstreamComponent.getName() + " 上游类型与白名单声明不符（白名单是否过期？）");
                assertEquals(mirror.local(), localComponent.getType(),
                        "镜像分量 " + upstreamComponent.getName() + " 本域类型与白名单声明不符（白名单是否过期？）");
            }
        }

        // ④ 白名单不得有悬空条目（声明了镜像，而分量在某一侧并不存在）。
        assertTrue(namesOf(upstream).containsAll(MIRRORED_COMPONENTS.keySet()),
                "镜像白名单存在上游不存在的分量：" + MIRRORED_COMPONENTS.keySet());
        assertTrue(namesOf(local).containsAll(MIRRORED_COMPONENTS.keySet()),
                "镜像白名单存在本域不存在的分量：" + MIRRORED_COMPONENTS.keySet());
    }

    /**
     * 🔴 下限断言：{@code analysis} 是 G07 新增的跨域契约分量，**必须**在两侧存在且同为
     * {@link FieldAnalysisCapability}（metadata 共享类型，本域不得建镜像 record）。
     *
     * <p>这条独立于「两侧互比」——两侧一起漏掉或一起换类型时互比恒绿，唯此断言能红。
     */
    @Test
    void analysisComponentMustExistOnBothSidesWithSharedType() {
        RecordComponent upstream = requireComponent(PolicyFieldDescriptorResponse.class, "analysis");
        RecordComponent local = requireComponent(PolicyFieldDescriptorEvidence.class, "analysis");

        assertEquals(FieldAnalysisCapability.class, upstream.getType(),
                "上游 analysis 必须是 metadata 共享类型 FieldAnalysisCapability");
        assertEquals(FieldAnalysisCapability.class, local.getType(),
                "本域 analysis 必须直接复用 metadata 共享类型，不得建镜像 record");
    }

    /** 镜像分量内部同样必须逐位对齐——上游给 Response 加位而 Evidence 漏加时，本域静默读不到。 */
    @Test
    void mirroredComponentsMustAlign() {
        MIRRORED_COMPONENTS.values().forEach(mirror -> {
            List<RecordComponent> upstream = componentsOf(mirror.upstream());
            List<RecordComponent> local = componentsOf(mirror.local());

            assertFalse(upstream.isEmpty(), "镜像上游类型无分量: " + mirror.upstream());
            assertEquals(namesOf(upstream), namesOf(local),
                    "镜像分量内部名称或顺序不一致: " + mirror.upstream() + " ↔ " + mirror.local());
            for (int index = 0; index < upstream.size(); index++) {
                assertEquals(upstream.get(index).getType(), local.get(index).getType(),
                        "镜像分量内部类型不一致: " + upstream.get(index).getName());
            }
        });
    }

    /**
     * 🔴 下限断言：{@code fallback} 是 G07/g07-04 新增的**目录级**跨域契约分量，
     * <b>必须</b>在两侧存在且同为 {@code boolean}。
     *
     * <p>它承载「业务日期早于最早目录版本 ⇒ 返回最早版本」的降级信号（G08 架构方案 §5.1 第 4 条
     * 要求两处同语义）。这条独立于「两侧互比」——两侧一起漏掉时互比恒绿，唯此断言能红。</p>
     */
    @Test
    void fallbackComponentMustExistOnBothSidesAsBoolean() {
        RecordComponent upstream = requireComponent(PolicyFieldCatalogResponse.class, "fallback");
        RecordComponent local = requireComponent(PolicyFieldCatalogEvidence.class, "fallback");

        assertEquals(boolean.class, upstream.getType(), "上游 fallback 必须是 boolean（降级标记不得用包装类型）");
        assertEquals(boolean.class, local.getType(), "本域 fallback 必须与上游同为 boolean");
    }

    /** 目录级（版本头 + 内容）两侧同样必须逐位对齐（名称/顺序/类型）。 */
    @Test
    void catalogsMustAlignComponentByComponent() {
        List<RecordComponent> upstream = componentsOf(PolicyFieldCatalogResponse.class);
        List<RecordComponent> local = componentsOf(PolicyFieldCatalogEvidence.class);

        assertEquals(EXPECTED_CATALOG_COMPONENT_COUNT, upstream.size(),
                "上游 PolicyFieldCatalogResponse 分量数已变——跨域契约变更，本域必须同批对齐");
        assertEquals(EXPECTED_CATALOG_COMPONENT_COUNT, local.size(),
                "本域 PolicyFieldCatalogEvidence 分量数与上游不再对齐");
        assertEquals(namesOf(upstream), namesOf(local), "目录级两侧分量名或顺序不一致（跨域契约漂移）");
        for (int index = 0; index < upstream.size(); index++) {
            assertEquals(upstream.get(index).getType(), local.get(index).getType(),
                    "目录级分量 " + upstream.get(index).getName() + " 两侧类型不一致");
        }
    }

    private static List<RecordComponent> componentsOf(Class<?> recordType) {
        assertTrue(recordType.isRecord(), recordType + " 不是 record 类型");
        return List.of(recordType.getRecordComponents());
    }

    private static List<String> namesOf(List<RecordComponent> components) {
        List<String> names = new ArrayList<>(components.size());
        components.forEach(component -> names.add(component.getName()));
        return names;
    }

    private static RecordComponent requireComponent(Class<?> recordType, String name) {
        return componentsOf(recordType).stream()
                .filter(component -> component.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        recordType.getSimpleName() + " 缺少契约分量: " + name));
    }
}
