package com.kakaobank.coinbox.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 슬라이스가 없는 Controller 테스트와 감사 설정을 분리한다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
