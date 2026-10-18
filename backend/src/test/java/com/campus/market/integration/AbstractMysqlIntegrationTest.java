package com.campus.market.integration;

import com.campus.market.common.util.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Testcontainers MySQL 8 集成测试基类：
 * - disabledWithoutDocker：无 Docker 环境时集成测试优雅跳过（Mockito 单测不受影响）；
 * - 容器静态单例（同 JVM 所有集成测试类共享实例，一次启动跨类复用）；
 * - 灌库：执行项目根 sql/schema.sql（唯一 schema 来源，避免测试副本漂移）；
 * - Redis：@MockitoBean 替换（集成范围仅验证 DB 一致性路径，缓存行为由 Mockito 单测覆盖）；
 * - 子类标 @Transactional 实现每用例自动回滚隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractMysqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    /** schema 只灌一次（跨测试类共享容器） */
    private static final AtomicBoolean SCHEMA_LOADED = new AtomicBoolean(false);

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    protected RedisService redisService;

    @BeforeEach
    void ensureSchemaLoaded() throws Exception {
        if (SCHEMA_LOADED.compareAndSet(false, true)) {
            Path schema = locateSchema();
            try (Connection conn = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(conn, new FileSystemResource(schema.toFile()));
            }
        }
    }

    /** 定位项目根 sql/schema.sql：surefire 工作目录可能是 backend 模块根或仓库根，两级探测 */
    private static Path locateSchema() {
        for (Path candidate : new Path[]{
                Paths.get("..", "sql", "schema.sql"),      // backend 模块根 → 项目根
                Paths.get("sql", "schema.sql")}) {          // 已在项目根（IDE 运行场景）
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("未找到 sql/schema.sql，请确认工作目录为 backend 模块根");
    }
}
