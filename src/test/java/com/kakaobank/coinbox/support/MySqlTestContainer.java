package com.kakaobank.coinbox.support;

import com.kakaobank.coinbox.common.config.JpaAuditingConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Repository와 통합 테스트가 개발자의 로컬 MySQL 상태에 의존하지 않도록 동일한 MySQL을 제공한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(JpaAuditingConfiguration.class)
public class MySqlTestContainer {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.3.0")
                .withDatabaseName("coinbox")
                .withUsername("coinbox")
                .withPassword("coinbox")
                .withUrlParam("useAffectedRows", "true");
    }
}
