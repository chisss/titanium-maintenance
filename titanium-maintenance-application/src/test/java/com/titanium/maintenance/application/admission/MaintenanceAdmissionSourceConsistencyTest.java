package com.titanium.maintenance.application.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 两条建案路径的准入<b>同源</b>结构守卫（g04-07）。
 *
 * <p>
 * <b>威胁模型</b>：本域有两条建案路径——旧版 {@code MaintenanceApplicationService.createMaintenanceCase}
 * 与独立 {@code MaintenanceCaseCreationApplicationService.create}。整改前二者的状态准入判据<b>各写一套</b>
 * （前者 {@code switch} 覆盖 8/23 类、其余 {@code default} 放行；后者完全依赖产品 Offering 可配数据），
 * 两条路径对同一 (保全项, 保单状态) 可给出不同结论，且旧版路径<b>无正向行为用例</b>——
 * 摘掉闸口不会有任何用例转红。
 * </p>
 *
 * <p>
 * 本测试以<b>源码结构判据</b>守护三件事，任一项被人改回即失败：
 * </p>
 * <ol>
 * <li><b>两条路径都委托同一权威</b>：各自恰有一处 {@code MaintenanceAdmissionMatrix.require(} 调用；</li>
 * <li><b>两条路径都委托同一在途门面</b>：各自恰有一处 {@code inFlightAdmissionPolicy.requireNoConflict(} 调用；</li>
 * <li><b>两条路径都不得就地判准入</b>：代码（已剔注释与字符串）中 {@code PolicyStatus.} 出现<b>零次</b>——
 *     一旦某个路径开始自己比状态常量，就等于再造了第二套权威，本守卫立即转红。</li>
 * </ol>
 *
 * <p>
 * 🔴 全部计数取<b>绝对值</b>并逐个断言（而非「大于零」）：文件被改名/移动导致一个都没扫到时，
 * 本测试必须失败而不是静默通过。
 * </p>
 *
 * <p>
 * 🔴 <b>盲点（本守卫不覆盖）</b>：它只证明两条路径<b>都委托了</b>权威，不证明委托发生在<b>派发命令之前</b>，
 * 也不证明异常被正确上抛。「闸口次序与是否先于派发」由行为用例守护
 * （{@code MaintenanceApplicationServiceCreationTest} 与
 * {@code MaintenanceCaseCreationApplicationServiceTest} 的 {@code verifyNoInteractions(commandGateway)}），
 * 二者互补，缺一不可。
 * </p>
 *
 * @author wei.sun
 * @since 2026/9/29
 */
class MaintenanceAdmissionSourceConsistencyTest {

    /** 旧版建案路径（源码相对模块根，surefire 以模块目录为工作目录） */
    private static final String LEGACY_PATH =
            "src/main/java/com/titanium/maintenance/application/service/MaintenanceApplicationService.java";

    /** 独立建案路径 */
    private static final String INDEPENDENT_PATH = "src/main/java/com/titanium/maintenance/application/"
            + "orchestration/casecreation/MaintenanceCaseCreationApplicationService.java";

    /** 两条路径源码相对模块根的路径 */
    private static final List<String> CREATION_PATHS = List.of(LEGACY_PATH, INDEPENDENT_PATH);

    private static final String MATRIX_CALL = "MaintenanceAdmissionMatrix.require(";
    private static final String IN_FLIGHT_CALL = "inFlightAdmissionPolicy.requireNoConflict(";

    /** 准入取值域的类型前缀——在路径源码里出现即意味着「就地判状态」 */
    private static final String ADMISSION_VOCABULARY = "PolicyStatus.";

    @Test
    @DisplayName("两条建案路径各恰有一处矩阵调用与一处在途门面调用（同源且不重复）")
    void bothCreationPathsMustDelegateToTheSingleAuthority() throws IOException {
        List<String> violations = new ArrayList<>();
        int files = 0;

        for (String relative : CREATION_PATHS) {
            Path file = resolveSource(relative);
            files++;
            String code = stripCommentsAndStrings(readSource(file));

            int matrixCalls = countOf(code, MATRIX_CALL);
            if (matrixCalls != 1) {
                violations.add(relative + " 的 " + MATRIX_CALL + " 调用数为 " + matrixCalls + "，应为 1");
            }

            int inFlightCalls = countOf(code, IN_FLIGHT_CALL);
            if (inFlightCalls != 1) {
                violations.add(relative + " 的 " + IN_FLIGHT_CALL + " 调用数为 " + inFlightCalls + "，应为 1");
            }
        }

        assertEquals(2, files, "扫描到的建案路径数异常，守卫规则可能已失效");
        assertEquals(List.of(), violations, "建案路径未与准入权威同源：" + String.join("；", violations));
    }

    @Test
    @DisplayName("🔴 两条建案路径都不得就地判保单状态（PolicyStatus. 在代码中出现零次）")
    void noCreationPathMayDecideAdmissionLocally() throws IOException {
        List<String> violations = new ArrayList<>();
        int files = 0;

        for (String relative : CREATION_PATHS) {
            Path file = resolveSource(relative);
            files++;
            String code = stripCommentsAndStrings(readSource(file));

            int localStatusRefs = countOf(code, ADMISSION_VOCABULARY);
            if (localStatusRefs > 0) {
                violations.add(relative + " 出现 " + ADMISSION_VOCABULARY + " " + localStatusRefs
                        + " 次——路径不得自己比状态常量，准入结论只能来自 MaintenanceAdmissionMatrix");
            }
        }

        assertEquals(2, files, "扫描到的建案路径数异常，守卫规则可能已失效");
        assertEquals(List.of(), violations, String.join("；", violations));
    }

    @Test
    @DisplayName("守卫自检：剔注释/剔字符串的词法扫描不得把代码一起剔掉（正反对照）")
    void sourceStripperMustKeepCodeAndDropCommentsAndStrings() {
        String sample = """
                // 注释里的 PolicyStatus.EFFECTIVE 应被剔除
                /* 块注释里的 inFlightAdmissionPolicy.requireNoConflict( 同样剔除 */
                String text = "字符串里的 MaintenanceAdmissionMatrix.require( 也应剔除";
                realCall();
                """;
        String code = stripCommentsAndStrings(sample);

        assertEquals(1, countOf(code, "realCall()"), "真实代码必须保留——否则上面的判据是空集合恒绿");
        assertEquals(0, countOf(code, ADMISSION_VOCABULARY), "行注释未被剔除");
        assertEquals(0, countOf(code, IN_FLIGHT_CALL), "块注释未被剔除");
        assertEquals(0, countOf(code, MATRIX_CALL), "字符串字面量未被剔除");
    }

    // ==================== 源码工具 ====================

    private String readSource(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 单遍词法扫描，剔出注释与字符串/字符/文本块字面量。
     *
     * <p>
     * 🔴 必须<b>一遍</b>同时处理四类状态：分行处理则「块注释里的 {@code //}」与「字符串里的 {@code /*}」
     * 会各自把对方误判为注释起点，两个方向的误判都会让守卫判据失真。
     * </p>
     */
    static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (c == '"' && next == '"' && i + 2 < n && source.charAt(i + 2) == '"') {
                i += 3;
                while (i + 2 < n && !(source.charAt(i) == '"' && source.charAt(i + 1) == '"'
                        && source.charAt(i + 2) == '"')) {
                    i++;
                }
                i = Math.min(i + 3, n);
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i = Math.min(i + 1, n);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int countOf(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + token.length();
        }
    }

    /** 自模块目录向上定位源码（surefire 与 IDE 的工作目录可能不同） */
    private Path resolveSource(String relative) {
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未定位到源码 " + relative + "，当前目录: " + Paths.get("").toAbsolutePath());
    }
}
