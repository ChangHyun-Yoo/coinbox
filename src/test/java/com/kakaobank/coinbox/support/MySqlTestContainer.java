package com.kakaobank.coinbox.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Repository와 통합 테스트가 개발자의 로컬 MySQL 상태에 의존하지 않도록 동일한 MySQL을 제공한다.
 */
@Testcontainers
public abstract class MySqlTestContainer {

    @Container
    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.3.0")
            .withDatabaseName("coinbox")
            .withUsername("coinbox")
            .withPassword("coinbox");
}
