package com.titanium.maintenance.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.port.policy.PolicyMaintenanceApplicationPort;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * 证据版本列宽守卫（G04 真机缺陷固化，2026-09-28）。
 *
 * <p>
 * 🔴 <b>被固化的缺陷</b>：贷款类证据（放款 g04-03 / 还款 g04-04）把 {@code evidenceVersion()}
 * 实现为<b>生效请求号</b>，形态 {@code "effect-case-" + sha256} = <b>76 字符</b>；
 * 而 {@code t_maintenance_workflow_task_view.last_evidence_version} 为 {@code VARCHAR(64)}。
 * 投影写该列时抛 {@code Data too long}，致「生效回执」事件<b>整条投影失败</b>并进死信队列——
 * 实测 6 个贷款案件 EFFECT 任务全部卡在 {@code WAITING_EXTERNAL}、无一到 {@code COMPLETED}，
 * 案件永远无法收口。历史非贷款证据取 {@code actualPolicyVersion} 等短值故不受影响
 * （全库仍有 16 个 EFFECT 任务正常 COMPLETED），因此<b>既有单测与架构守卫全绿</b>、只在真机暴露。
 * </p>
 *
 * <p>
 * 🔴 <b>为何这样测</b>：真正生效的宽度在 <b>DDL 文件</b>里（{@code ddl-auto: none}，
 * {@code @Column(length=...)} 不产生 DDL）——只断言实体注解就是<b>量错了对象</b>。
 * 本测试沿用本包既有范式：手写<b>生产改造前的基线表</b>（{@code VARCHAR(64)}，即 m4_02 原貌）
 * → 真 Liquibase 跑待测 DDL → 从 {@code INFORMATION_SCHEMA} 读<b>改造后的真实列宽</b>。
 * 宽度与请求号长度两侧各取真值互比，<b>不写死 76</b>（改形态即自动跟随）。
 * </p>
 *
 * <p>
 * 🔴 <b>判据自带反向对照</b>：先在 64 位基线上写入一个<b>真实</b>生效请求号，断言<b>必须失败</b>
 * ——这一步复现了真机缺陷本身，证明本条判据不是恒真的空断言；随后的加宽与写入成功才具证明力。
 * 若把「基线写入失败」这步删掉，测试就退化为「读一个数字比大小」，无法证明它测的
 * 正是那个会导致投影失败的长度。
 * </p>
 */
class MaintenanceEvidenceVersionColumnWidthTest {

    /** 待测 DDL：把证据版本列由 64 加宽（G04 真机缺陷修复） */
    private static final String CHANGELOG =
            "liquibase/ddl/maintenance_evidence_version_widen_g04_fix_202609281630_weisun_ddl.sql";

    /** 生产改造前的列宽（m4_02 原貌），基线表据此复刻，使「缺陷可复现」成立 */
    private static final int ORIGINAL_WIDTH = 64;

    private static final String TABLE = "t_maintenance_workflow_task_view";
    private static final String COLUMN = "last_evidence_version";

    @Test
    @DisplayName("加宽后须装得下真实生效请求号（改造前基线须复现 Data too long）")
    void widenedColumnMustHoldRealEffectRequestId() throws Exception {
        String url = "jdbc:h2:mem:maintenance_evidence_version_"
                + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        // 真实形态的生效请求号：本判据的被测值取自领域方法实际产出，非字面量
        String requestId = PolicyMaintenanceApplicationPort.stableCaseRequestId(
                "1", "0e0202fe-7c2f-5451-bc0f-c93c349692da");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            createBaseline(connection);

            // ① 反向对照：改造前的 64 位列写入真实请求号必须**失败**——真机缺陷形态在此复现
            //    （MySQL 严格模式抛 Data too long，H2 MODE=MySQL 抛 Value too long，同为超长拒绝）。
            //    这一步证明下面的加宽判据测的正是「会导致投影失败的那个长度」，而非恒真的空断言。
            //    🔴 基线表必须**先置一行**：空表上的 UPDATE 命中 0 行、根本不校验长度，
            //    缺陷会「静默复现不了」而看似判据无效（本条测试首跑即栽在此处）。
            assertThrows(SQLException.class, () -> writeValue(connection, requestId),
                    "基线 " + ORIGINAL_WIDTH + " 位列竟能存下 " + requestId.length()
                            + " 字符的生效请求号——缺陷前提不成立，本测试失去意义");

            // ② 施加待测 DDL
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());

            // ③ 加宽后同一写入必须成功且逐字可读回——比「比大小」更直接的行为证据
            int width = columnLength(connection);
            assertTrue(width >= requestId.length(),
                    "改造后列宽 " + width + " 仍装不下生效请求号 " + requestId.length() + " 字符");
            writeValue(connection, requestId);
            assertEquals(requestId, readValue(connection), "加宽后应逐字存下，不得被截断");
        }
    }

    @Test
    @DisplayName("生效请求号形态：前缀 + 64 位 sha256 = 76 字符，本就长于 64 位摘要列")
    void effectRequestIdShapeIsLongerThanDigestColumn() {
        String caseRequestId = PolicyMaintenanceApplicationPort.stableCaseRequestId(
                "1", "0e0202fe-7c2f-5451-bc0f-c93c349692da");
        assertTrue(caseRequestId.startsWith("effect-case-"),
                "生效请求号须为 effect-case- 前缀形态，实际: " + caseRequestId);
        assertEquals("effect-case-".length() + 64, caseRequestId.length(),
                "前缀 + 64 位 sha256 = 76 字符（这是它装不进 " + ORIGINAL_WIDTH + " 位列的根因）");
        assertTrue(caseRequestId.length() > ORIGINAL_WIDTH,
                "证据版本长度必须真的超出原列宽，否则本缺陷不成立、守卫失去意义");
    }

    /** 复刻生产改造前的基线表（仅含被测列，宽度取 m4_02 原貌 64），并置一行以便回读 */
    private void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + TABLE + " ("
                    + "task_id VARCHAR(191) PRIMARY KEY, " + COLUMN + " VARCHAR(" + ORIGINAL_WIDTH + "))");
            statement.execute("INSERT INTO " + TABLE + " (task_id) VALUES ('effect-task')");
        }
    }

    private void writeValue(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE + " SET " + COLUMN + " = ?")) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private String readValue(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT " + COLUMN + " FROM " + TABLE)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    /** 从数据库元数据读改造后的真实列宽（权威口径，非源码文本推断） */
    private int columnLength(Connection connection) throws SQLException {
        String sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            statement.setString(2, COLUMN);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(),
                        "元数据中查不到 " + TABLE + "." + COLUMN + "——表名/列名已变，判据失据");
                return resultSet.getInt(1);
            }
        }
    }
}
